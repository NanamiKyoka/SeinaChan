package com.seina.chan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.seina.chan.data.model.ConnectionConfig
import com.seina.chan.data.remote.ConnectionState
import com.seina.chan.data.remote.HermesApiService
import com.seina.chan.data.remote.HermesWsClient
import com.seina.chan.util.FileLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import com.seina.chan.data.remote.ApiError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class ConnectionRepository(
    private val wsClient: HermesWsClient,
    private val apiService: HermesApiService,
    private val dataStore: DataStore<Preferences>,
    private val client: HttpClient
) {
    val connectionState: StateFlow<ConnectionState> = wsClient.state

    suspend fun connect(config: ConnectionConfig): Result<Unit> {
        val urls = parseConnectionUrls(config.ip, config.port)
        val wsUrl = urls.wsUrl
        val httpBaseUrl = urls.httpBaseUrl
        FileLogger.i("ConnectionRepository", "connect() httpBaseUrl=$httpBaseUrl, wsUrl=$wsUrl")
        return try {
            apiService.setConfig(httpBaseUrl, config.token)
            val connected = wsClient.connect(wsUrl, config.token)
            if (connected) {
                wsClient.enableReconnect(true)
                FileLogger.i("ConnectionRepository", "connect() succeeded")
                Result.success(Unit)
            } else {
                FileLogger.e("ConnectionRepository", "connect() failed: WebSocket returned false")
                Result.failure(Exception("WebSocket 连接失败"))
            }
        } catch (e: Exception) {
            FileLogger.e("ConnectionRepository", "connect() exception", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(ip: String, port: String): Result<String> {
        val urls = parseConnectionUrls(ip, port)
        val url = "${urls.httpBaseUrl}/api/status"
        FileLogger.i("ConnectionRepository", "testConnection() url=$url")
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val response = client.get(url)
                if (response.status.value in 200..299) {
                    FileLogger.i("ConnectionRepository", "testConnection() succeeded")
                    return Result.success("连接成功")
                } else {
                    FileLogger.e("ConnectionRepository", "testConnection() failed: HTTP ${response.status.value}")
                    return Result.failure(Exception("HTTP ${response.status.value}"))
                }
            } catch (e: ApiError.Timeout) {
                FileLogger.w("ConnectionRepository", "testConnection timeout (attempt ${attempt + 1})")
                lastError = e
                if (attempt < 2) delay(500)
            } catch (e: ApiError.NetworkError) {
                FileLogger.w("ConnectionRepository", "testConnection network error (attempt ${attempt + 1})", e)
                lastError = e
                if (attempt < 2) delay(500)
            } catch (e: Exception) {
                FileLogger.e("ConnectionRepository", "testConnection() exception (attempt ${attempt + 1})", e)
                lastError = e
                if (attempt < 2) delay(500)
            }
        }
        return Result.failure(lastError ?: Exception("testConnection failed"))
    }

    suspend fun disconnect() {
        FileLogger.i("ConnectionRepository", "disconnect() called")
        wsClient.enableReconnect(false)
        wsClient.disconnect()
    }

    suspend fun saveConfig(config: ConnectionConfig) {
        FileLogger.i("ConnectionRepository", "saveConfig() ip=${config.ip}, port=${config.port}")
        try {
            dataStore.edit { prefs ->
                prefs[IP_KEY] = config.ip
                prefs[PORT_KEY] = config.port
                prefs[TOKEN_KEY] = config.token
            }
        } catch (e: Exception) {
            FileLogger.e("ConnectionRepository", "保存配置失败", e)
        }
    }

    suspend fun loadConfig(): ConnectionConfig? {
        FileLogger.i("ConnectionRepository", "loadConfig() called")
        return try {
            val prefs = dataStore.data.first()
            val ip = prefs[IP_KEY]
            val port = prefs[PORT_KEY]
            val token = prefs[TOKEN_KEY] ?: ""
            if (!ip.isNullOrBlank() && !port.isNullOrBlank()) {
                FileLogger.i("ConnectionRepository", "loadConfig() found new config")
                ConnectionConfig(ip, port, token)
            } else {
                val baseUrl = prefs[BASE_URL_KEY]
                if (!baseUrl.isNullOrBlank()) {
                    FileLogger.i("ConnectionRepository", "loadConfig() found legacy baseUrl=$baseUrl, parsing...")
                    val parsed = parseLegacyBaseUrl(baseUrl)
                    if (parsed != null) {
                        ConnectionConfig(parsed.first, parsed.second, token)
                    } else {
                        FileLogger.w("ConnectionRepository", "loadConfig() failed to parse legacy baseUrl")
                        null
                    }
                } else {
                    FileLogger.i("ConnectionRepository", "loadConfig() no config found")
                    null
                }
            }
        } catch (e: Exception) {
            FileLogger.e("ConnectionRepository", "加载配置失败", e)
            null
        }
    }

    suspend fun clearConfig() {
        FileLogger.i("ConnectionRepository", "clearConfig() called")
        try {
            dataStore.edit { prefs ->
                prefs.remove(IP_KEY)
                prefs.remove(PORT_KEY)
                prefs.remove(TOKEN_KEY)
                prefs.remove(BASE_URL_KEY)
            }
        } catch (e: Exception) {
            FileLogger.e("ConnectionRepository", "清除配置失败", e)
        }
    }

    private fun parseLegacyBaseUrl(baseUrl: String): Pair<String, String>? {
        val cleaned = baseUrl
            .removePrefix("ws://")
            .removePrefix("wss://")
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/api/ws")
            .removeSuffix("/")

        // 支持 IPv6 格式，如 [2001:db8::1]:8080
        return if (cleaned.startsWith("[")) {
            val bracketEnd = cleaned.indexOf("]")
            if (bracketEnd == -1) return null
            val host = cleaned.substring(1, bracketEnd)
            val afterBracket = cleaned.substring(bracketEnd + 1)
            val parsedPort = if (afterBracket.startsWith(":")) {
                afterBracket.removePrefix(":")
            } else {
                "80"
            }
            if (host.isNotBlank() && parsedPort.isNotBlank()) host to parsedPort else null
        } else {
            val parts = cleaned.split(":")
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                parts[0] to parts[1]
            } else null
        }
    }

    suspend fun saveLastDbSessionId(sessionId: String) {
        try {
            dataStore.edit { prefs ->
                prefs[LAST_DB_SESSION_ID_KEY] = sessionId
            }
        } catch (e: Exception) {
            FileLogger.e("ConnectionRepository", "保存 lastDbSessionId 失败", e)
        }
    }

    suspend fun loadLastDbSessionId(): String? {
        return try {
            val prefs = dataStore.data.first()
            prefs[LAST_DB_SESSION_ID_KEY]
        } catch (e: Exception) {
            FileLogger.e("ConnectionRepository", "加载 lastDbSessionId 失败", e)
            null
        }
    }

    suspend fun clearLastDbSessionId() {
        try {
            dataStore.edit { prefs ->
                prefs.remove(LAST_DB_SESSION_ID_KEY)
            }
        } catch (e: Exception) {
            FileLogger.e("ConnectionRepository", "清除 lastDbSessionId 失败", e)
        }
    }

    private data class ConnectionUrls(val wsUrl: String, val httpBaseUrl: String)

    private fun parseConnectionUrls(ip: String, port: String): ConnectionUrls {
        val trimmedIp = ip.trim()
        val (wsScheme, httpScheme, cleanHost) = when {
            trimmedIp.startsWith("wss://", ignoreCase = true) ->
                Triple("wss", "https", trimmedIp.removePrefix("wss://"))
            trimmedIp.startsWith("ws://", ignoreCase = true) ->
                Triple("ws", "http", trimmedIp.removePrefix("ws://"))
            trimmedIp.startsWith("https://", ignoreCase = true) ->
                Triple("wss", "https", trimmedIp.removePrefix("https://"))
            trimmedIp.startsWith("http://", ignoreCase = true) ->
                Triple("ws", "http", trimmedIp.removePrefix("http://"))
            else -> Triple("ws", "http", trimmedIp)
        }
        var host = cleanHost.removeSuffix("/")
        // 自动为裸 IPv6 地址添加方括号（域名不含冒号，不受影响）
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[$host]"
        }
        return ConnectionUrls(
            wsUrl = "$wsScheme://$host:$port/api/ws",
            httpBaseUrl = "$httpScheme://$host:$port"
        )
    }

    companion object {
        private val IP_KEY = stringPreferencesKey("ip")
        private val PORT_KEY = stringPreferencesKey("port")
        private val TOKEN_KEY = stringPreferencesKey("token")
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val LAST_DB_SESSION_ID_KEY = stringPreferencesKey("last_db_session_id")
    }
}
