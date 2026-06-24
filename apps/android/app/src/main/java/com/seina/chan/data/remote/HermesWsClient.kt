package com.seina.chan.data.remote

import com.seina.chan.util.FileLogger
import com.seina.chan.util.NetworkMonitor
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Connecting : ConnectionState()
    data object Open : ConnectionState()
    data object Closed : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
}

class HermesWsClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        /** JSON-RPC 方法超时配置（毫秒） */
        private val METHOD_TIMEOUTS = mapOf(
            HermesMethods.SESSION_CREATE to 30_000L,
            HermesMethods.SESSION_RESUME to 30_000L,
            HermesMethods.PROMPT_SUBMIT to 300_000L,
            HermesMethods.IMAGE_ATTACH_BYTES to 120_000L,
            HermesMethods.SESSION_LIST to 15_000L,
        )
        private const val DEFAULT_TIMEOUT = 60_000L

        private const val HEARTBEAT_CHECK_INTERVAL_MS = 15_000L
        /** 最大重连延迟：5 分钟 */
        private const val MAX_RECONNECT_DELAY_MS = 300_000L
        /** 最大重连次数：20 次 × 最多 5 分钟 = 约 100 分钟总重连窗口 */
        private const val MAX_RECONNECT_ATTEMPTS = 20
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()


    private val connectLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()

    private val reqId = AtomicInteger(0)

    private var session: WebSocketSession? = null

    private var shouldReconnect = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var lastUrl: String? = null
    /** 重连时可调用来获取最新的 WS URL（例如 OAUTH 模式下重新登录+换 ticket） */
    private var urlProvider: (suspend () -> String?)? = null

    /** 上次收到帧的时间戳，用于心跳超时检测 */
    private var lastFrameTime = 0L
    private var heartbeatWatchJob: Job? = null

    private var currentHeartbeatTimeoutMs = 180_000L
    private val baseHeartbeatTimeoutMs = 180_000L
    private val longRunningHeartbeatTimeoutMs = 600_000L

    fun setLongRunningMode(enabled: Boolean) {
        currentHeartbeatTimeoutMs = if (enabled) longRunningHeartbeatTimeoutMs else baseHeartbeatTimeoutMs
        FileLogger.i("HermesWsClient", "LongRunningMode=$enabled, heartbeatTimeout=${currentHeartbeatTimeoutMs}ms")
    }

    init {
        scope.launch {
            state.collect { s ->
                if ((s is ConnectionState.Closed || s is ConnectionState.Error) && shouldReconnect) {
                    scheduleReconnect()
                }
            }
        }

        scope.launch {
            networkMonitor.networkAvailableDebounced.collect { available ->
                if (available) {
                    // 网络恢复，如果当前断开则立即重连
                    val currentState = _state.value
                    if ((currentState is ConnectionState.Closed || currentState is ConnectionState.Error) && shouldReconnect) {
                        FileLogger.i("HermesWsClient", "网络恢复，立即重连")
                        reconnectAttempts = 0
                        reconnectJob?.cancel()
                        reconnectJob = null
                        scheduleReconnect()
                    }
                } else {
                    FileLogger.w("HermesWsClient", "网络断开，取消重连")
                    reconnectJob?.cancel()
                    reconnectJob = null
                }
            }
        }
    }

    fun enableReconnect(enabled: Boolean) {
        shouldReconnect = enabled
        if (enabled) {
            reconnectAttempts = 0
            if (_state.value is ConnectionState.Closed || _state.value is ConnectionState.Error) {
                scheduleReconnect()
            }
        } else {
            reconnectJob?.cancel()
            reconnectJob = null
        }
    }
    suspend fun connect(fullWsUrl: String, urlProvider: (suspend () -> String?)? = null): Boolean {
        connectLock.withLock {
            if (_state.value == ConnectionState.Open || _state.value == ConnectionState.Connecting) {
                FileLogger.i("HermesWsClient", "Already connected or connecting, state=${_state.value}")
                return true
            }
            lastUrl = fullWsUrl
            this.urlProvider = urlProvider
            return doConnect(fullWsUrl)
        }
    }

    private suspend fun doConnect(url: String): Boolean {
        _state.value = ConnectionState.Connecting
        FileLogger.i("HermesWsClient", "doConnect() starting, url=$url")
        return try {
            val newSession = client.webSocketSession(url)
            session = newSession
            _state.value = ConnectionState.Open
            reconnectAttempts = 0
            lastFrameTime = System.currentTimeMillis()
            FileLogger.i("HermesWsClient", "WebSocket handshake succeeded")

            heartbeatWatchJob?.cancel()
            heartbeatWatchJob = scope.launch {
                while (true) {
                    delay(HEARTBEAT_CHECK_INTERVAL_MS)
                    val elapsed = System.currentTimeMillis() - lastFrameTime
                    if (elapsed > currentHeartbeatTimeoutMs) {
                        FileLogger.e("HermesWsClient", "心跳超时：${elapsed}ms 未收到帧（阈值=${currentHeartbeatTimeoutMs}ms），强制关闭会话")
                        try {
                            session?.close()
                        } catch (_: Exception) {}
                        break
                    }
                }
            }

            scope.launch {
                try {
                    for (frame in newSession.incoming) {
                        lastFrameTime = System.currentTimeMillis()
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            FileLogger.d("HermesWsClient", "Frame received, len=${text.length}: ${text.take(200)}")
                            handleFrame(text)
                        }
                    }
                } catch (e: Exception) {
                    FileLogger.e("HermesWsClient", "incoming loop exception", e)
                } finally {
                    heartbeatWatchJob?.cancel()
                    heartbeatWatchJob = null
                    FileLogger.w("HermesWsClient", "incoming loop ended, state=${_state.value}")
                    if (_state.value == ConnectionState.Open) {
                        _state.value = ConnectionState.Closed
                    }
                    clearPending("Connection closed")
                }
            }
            true
        } catch (e: Exception) {
            FileLogger.e("HermesWsClient", "doConnect() failed", e)
            _state.value = ConnectionState.Error(e.message ?: "Unknown error")
            clearPending(e.message ?: "Connection error")
            false
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                FileLogger.w("HermesWsClient", "重连次数已达上限 $MAX_RECONNECT_ATTEMPTS，停止重连")
                shouldReconnect = false
                return@launch
            }
            val delayMs = (1000 * 2.0.pow(reconnectAttempts.toDouble())).toLong()
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)
            FileLogger.i("HermesWsClient", "计划重连，第${reconnectAttempts + 1}次，延迟${delayMs}ms")
            delay(delayMs)
            reconnectAttempts++
            // 获取最新 URL：有 urlProvider 则调用获取（OAUTH 重连重新登录），否则用 lastUrl
            val targetUrl = urlProvider?.invoke() ?: lastUrl ?: return@launch
            connectLock.withLock {
                if (_state.value == ConnectionState.Open || _state.value == ConnectionState.Connecting) {
                    FileLogger.i("HermesWsClient", "scheduleReconnect() 跳过，已连接")
                    return@withLock
                }
                doConnect(targetUrl)
            }
        }
    }
    fun reconnectImmediately() {
        scope.launch {
            connectLock.withLock {
                if (_state.value == ConnectionState.Open || _state.value == ConnectionState.Connecting) {
                    FileLogger.i("HermesWsClient", "reconnectImmediately() 跳过，当前状态=$_state.value")
                    return@withLock
                }
                FileLogger.i("HermesWsClient", "reconnectImmediately() 触发立即重连")
                reconnectJob?.cancel()
                reconnectJob = null
                reconnectAttempts = 0
                // 关闭旧 WebSocket 防止后续 request 误发到正在关闭的连接上
                try {
                    session?.close()
                } catch (_: Exception) {}
                session = null
                // 获取最新 URL：有 urlProvider 则调用获取（OAUTH 重连重新登录），否则用 lastUrl
                val targetUrl = urlProvider?.invoke() ?: lastUrl
                if (targetUrl == null) {
                    FileLogger.w("HermesWsClient", "reconnectImmediately() 失败：缺少 url")
                    return@withLock
                }
                doConnect(targetUrl)
            }
        }
    }

    suspend fun request(method: String, params: JsonObject? = null): JsonElement {
        val id = reqId.incrementAndGet()
        FileLogger.d("HermesWsClient", "sendRequest method=$method, id=$id")
        val request = JsonRpcRequest(id = id, method = method, params = params)
        val deferred = CompletableDeferred<JsonElement>()
        pendingRequests[id] = deferred

        val text = json.encodeToString(JsonRpcRequest.serializer(), request)
        val s = session ?: throw IllegalStateException("WebSocket not connected")
        s.send(Frame.Text(text))

        val timeout = METHOD_TIMEOUTS[method] ?: DEFAULT_TIMEOUT
        return try {
            withTimeout(timeout) {
                deferred.await()
            }
        } catch (e: Exception) {
            FileLogger.e("HermesWsClient", "request timeout/exception for method=$method, id=$id", e)
            pendingRequests.remove(id)
            throw e
        }
    }

    fun disconnect() {
        FileLogger.i("HermesWsClient", "disconnect() called")
        scope.launch {
            connectLock.withLock {
                shouldReconnect = false
                reconnectJob?.cancel()
                reconnectJob = null
                heartbeatWatchJob?.cancel()
                heartbeatWatchJob = null
                session?.close()
                session = null
                _state.value = ConnectionState.Closed
                clearPending("Disconnected")
            }
        }
    }

    private fun clearPending(reason: String) {
        val entries = pendingRequests.toMap()
        pendingRequests.clear()
        entries.values.forEach { it.completeExceptionally(Exception(reason)) }
    }

    private fun handleFrame(text: String) {
        try {
            val element = json.parseToJsonElement(text)
            val obj = element.jsonObject

            // JSON-RPC 响应（有 id 和 result/error）
            if (obj.containsKey("id") && (obj.containsKey("result") || obj.containsKey("error"))) {
                try {
                    val response = json.decodeFromJsonElement(JsonRpcResponse.serializer(), element)
                    val deferred = pendingRequests.remove(response.id)
                    if (deferred != null) {
                        if (response.error != null) {
                            FileLogger.w("HermesWsClient", "JSON-RPC error id=${response.id}: ${response.error.code} ${response.error.message}")
                            deferred.completeExceptionally(
                                JsonRpcException(response.error.code, response.error.message)
                            )
                        } else {
                            deferred.complete(response.result ?: JsonObject(emptyMap()))
                        }
                    }
                } catch (e: Exception) {
                    FileLogger.e("HermesWsClient", "Failed to parse JSON-RPC response", e)
                }
                return
            }

            // 事件通知（method == "event"）— 统一使用 GatewayEventSerializer 反序列化
            val method = obj["method"]?.jsonPrimitive?.content
            if (method == "event") {
                val params = obj["params"]?.jsonObject ?: return
                val transformed = transformEventParams(params)
                try {
                    val event = json.decodeFromJsonElement(GatewayEventSerializer, transformed)
                    FileLogger.d("HermesWsClient", "Event parsed: ${event::class.simpleName}")
                    val emitted = _events.tryEmit(event)
                    if (!emitted) {
                        FileLogger.w("HermesWsClient", "Event buffer full, dropped event type=${event::class.simpleName}")
                    }
                } catch (e: Exception) {
                    val eventType = params["type"]?.jsonPrimitive?.content ?: "unknown"
                    FileLogger.e("HermesWsClient", "Failed to deserialize event type=$eventType", e)
                }
            }
        } catch (e: Exception) {
            FileLogger.e("HermesWsClient", "Failed to handle frame", e)
        }
    }

    /**
     * 将 JSON-RPC 事件参数转换为 GatewayEventSerializer 可处理的格式。
     * Hermes 网关发送的格式：{"type": "message.delta", "session_id": "...", "payload": {...}}
     * 序列化器期望的格式：{"type": "message.delta", ...payload字段展开到顶层}
     */
    private fun transformEventParams(params: JsonObject): JsonObject {
        val eventType = params["type"]?.jsonPrimitive?.content ?: return params
        val payload = params["payload"]?.jsonObject ?: JsonObject(emptyMap())
        return buildJsonObject {
            put("type", eventType)
            payload.forEach { (key, value) -> put(key, value) }
        }
    }
}
