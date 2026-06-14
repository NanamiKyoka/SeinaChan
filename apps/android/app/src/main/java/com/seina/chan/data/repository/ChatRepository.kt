package com.seina.chan.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.seina.chan.data.local.dao.MessageDao
import com.seina.chan.data.local.dao.SentImageDao
import com.seina.chan.data.local.entity.MessageEntity
import com.seina.chan.data.local.entity.SentImageEntity
import com.seina.chan.data.model.ChatMessage
import com.seina.chan.data.model.ToolCallDetail
import com.seina.chan.data.model.ToolCallStatus
import com.seina.chan.data.remote.GatewayEvent
import com.seina.chan.data.remote.HermesMethods
import com.seina.chan.data.remote.HermesWsClient
import com.seina.chan.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File

class ChatRepository(
    private val context: Context,
    private val wsClient: HermesWsClient,
    private val sentImageDao: SentImageDao,
    private val messageDao: MessageDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val events = wsClient.events

    private var currentSessionId: String? = null

    init {
        wsClient.events.onEach { event ->
            handleEvent(event)
        }.launchIn(scope)


        // Auto-resume 已由 ChatViewModel 统一管理，此处不重复发送 SESSION_RESUME，
        // 避免与 ViewModel 并发发送导致服务端状态不一致
    }

    /**
     * @param dbSessionId 用于 Room 持久化的存储会话 ID（稳定不变）
     * @param wsSessionId 用于 WebSocket PROMPT_SUBMIT 的会话 ID（每次 resume 可能不同）
     */
    suspend fun sendMessage(text: String, wsSessionId: String, dbSessionId: String, parentId: String? = null) {
        currentSessionId = dbSessionId
        // 结束所有未完成的 assistant 消息，避免积累空消息
        _messages.value = _messages.value.map {
            if (it.isStreaming && it.role == "assistant") {
                finalizeToolCallsInMessage(
                    it.copy(isStreaming = false, isReasoning = false)
                )
            } else it
        }
        persistMessages()

        val userMessage = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = "user",
            content = text,
            isStreaming = false,
            parentId = parentId
        )
        _messages.value += userMessage
        persistMessage(userMessage)

        val params = buildJsonObject {
            put("session_id", wsSessionId)
            put("text", text)
            if (parentId != null) {
                put("parent_id", parentId)
            }
        }
        wsClient.request(HermesMethods.PROMPT_SUBMIT, params)
    }

    /**
     * 发送图片消息
     * @param imageUri 图片 URI
     * @param contentResolver 用于读取图片内容
     * @param sessionId 会话 ID
     */
    suspend fun sendImage(imageUri: Uri, contentResolver: ContentResolver, wsSessionId: String, dbSessionId: String) {
        currentSessionId = dbSessionId
        // 结束所有未完成的 assistant 消息，避免积累空消息
        _messages.value = _messages.value.map {
            if (it.isStreaming && it.role == "assistant") {
                finalizeToolCallsInMessage(
                    it.copy(isStreaming = false, isReasoning = false)
                )
            } else it
        }
        persistMessages()

        // 先将图片复制到应用私有目录，保证持久化可用
        val persistentPath = withContext(Dispatchers.IO) {
            copyImageToPrivateDir(imageUri, contentResolver)
        }

        // 读取图片并转为 base64（大图片自动压缩，非 JPEG 小图片转为 JPEG）
        val (base64Data, mimeType) = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                if (bytes.size <= 1_048_576) {
                    // 小图片：检测 MIME 类型，非 JPEG 则转为 JPEG
                    val detectedMime = contentResolver.getType(imageUri) ?: "image/jpeg"
                    if (detectedMime == "image/jpeg" || detectedMime == "image/jpg") {
                        Pair(Base64.encodeToString(bytes, Base64.NO_WRAP), "image/jpeg")
                    } else {
                        // PNG/WebP 等格式转为 JPEG
                        Pair(compressImage(bytes), "image/jpeg")
                    }
                } else {
                    // 大图片压缩
                    Pair(compressImage(bytes), "image/jpeg")
                }
            } ?: throw IllegalArgumentException("无法读取图片")
        }

        // 在本地消息列表添加用户图片消息占位（使用持久路径）
        val userMessage = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = "user",
            content = "",
            isStreaming = false,
            imageUrl = persistentPath
        )
        _messages.value += userMessage
        persistMessage(userMessage)

        // 构造 data URI
        val dataUri = "data:$mimeType;base64,$base64Data"
        val params = buildJsonObject {
            put("session_id", wsSessionId)
            put("data", dataUri)
            put("name", "image.jpg")
        }
        val result = wsClient.request(HermesMethods.IMAGE_ATTACH_BYTES, params)
        if (result is JsonObject) {
            val serverPath = result["path"]?.jsonPrimitive?.content
            if (serverPath != null) {
                // 标准化路径：只保留 .hermes/images/ 及之后部分，确保与 fetchMessages 时提取的路径一致
                val normalizedPath = serverPath.substring(serverPath.lastIndexOf(".hermes/images/"))
                sentImageDao.insert(
                    SentImageEntity(
                        serverPath = normalizedPath,
                        localUri = persistentPath
                    )
                )
            }
        }
    }

    /**
     * 将图片从临时 URI 复制到应用私有目录，返回持久化的绝对路径
     */
    private fun copyImageToPrivateDir(sourceUri: Uri, contentResolver: ContentResolver): String {
        val fileName = "sent_img_${System.currentTimeMillis()}.jpg"
        val destDir = File(context.filesDir, "sent_images").apply { mkdirs() }
        val destFile = File(destDir, fileName)
        contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile.absolutePath
    }

    /**
     * 发送视频消息
     * @param videoUri 视频 URI
     * @param contentResolver 用于读取视频内容
     * @param sessionId 会话 ID
     */
    suspend fun sendVideo(videoUri: Uri, contentResolver: ContentResolver, wsSessionId: String, dbSessionId: String) {
        currentSessionId = dbSessionId
        _messages.value = _messages.value.map {
            if (it.isStreaming && it.role == "assistant") {
                finalizeToolCallsInMessage(
                    it.copy(isStreaming = false, isReasoning = false)
                )
            } else it
        }
        persistMessages()

        val fileSize = withContext(Dispatchers.IO) {
            var size = 0L
            contentResolver.query(videoUri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx != -1) size = cursor.getLong(idx)
                }
            }
            size
        }

        if (fileSize > 10 * 1024 * 1024) {
            FileLogger.w("ChatRepository", "Video too large (${fileSize} bytes), skipping upload")
            val userMessage = ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = "user",
                content = "[视频]",
                isStreaming = false
            )
            _messages.value += userMessage
            persistMessage(userMessage)
            return
        }

        val (base64Data, mimeType) = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(videoUri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val detectedMime = contentResolver.getType(videoUri) ?: "video/mp4"
                Pair(Base64.encodeToString(bytes, Base64.NO_WRAP), detectedMime)
            } ?: throw IllegalArgumentException("无法读取视频")
        }

        val userMessage = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = "user",
            content = "",
            isStreaming = false,
            imageUrl = videoUri.toString()
        )
        _messages.value += userMessage
        persistMessage(userMessage)

        val dataUri = "data:$mimeType;base64,$base64Data"
        val params = buildJsonObject {
            put("session_id", wsSessionId)
            put("data", dataUri)
            put("name", "video.mp4")
        }
        wsClient.request(HermesMethods.IMAGE_ATTACH_BYTES, params)
    }

    /**
     * 提交空 prompt 以触发 assistant 回复（用于纯图片发送场景）
     */
    suspend fun submitPrompt(sessionId: String) {
        val params = buildJsonObject {
            put("session_id", sessionId)
            put("text", "")
        }
        wsClient.request(HermesMethods.PROMPT_SUBMIT, params)
    }

    suspend fun respondApproval(id: String, approved: Boolean, allowPermanent: Boolean = false) {
        val params = buildJsonObject {
            put("request_id", id)
            put("choice", if (approved) "once" else "deny")
            put("all", allowPermanent)
        }
        wsClient.request(HermesMethods.APPROVAL_RESPOND, params)
    }
    suspend fun respondClarify(requestId: String, answer: String) {
        val params = buildJsonObject {
            put("request_id", requestId)
            put("answer", answer)
        }
        wsClient.request(HermesMethods.CLARIFY_RESPOND, params)
    }

    suspend fun respondSecret(requestId: String, value: String) {
        val params = buildJsonObject {
            put("request_id", requestId)
            put("value", value)
        }
        wsClient.request(HermesMethods.SECRET_RESPOND, params)
    }

    suspend fun respondSudo(requestId: String, password: String) {
        val params = buildJsonObject {
            put("request_id", requestId)
            put("password", password)
        }
        wsClient.request(HermesMethods.SUDO_RESPOND, params)
    }

    suspend fun stopGenerating(sessionId: String? = null) {
        FileLogger.i("ChatRepository", "stopGenerating() called")
        try {
            val params = buildJsonObject {
                sessionId?.let { put("session_id", it) }
            }
            wsClient.request(HermesMethods.SESSION_INTERRUPT, params)
            FileLogger.i("ChatRepository", "stopGenerating() succeeded")
        } catch (e: Exception) {
            FileLogger.e("ChatRepository", "stopGenerating() failed", e)
        }
    }

    suspend fun deleteMessageAndAfter(messageId: String) {
        val sid = currentSessionId ?: return
        val message = _messages.value.find { it.id == messageId } ?: return
        val createdAt = message.createdAt
        FileLogger.i("ChatRepository", "deleteMessageAndAfter() messageId=$messageId, createdAt=$createdAt, sessionId=$sid")
        withContext(Dispatchers.IO) {
            messageDao.deleteBySessionIdAndCreatedAtAfter(sid, createdAt)
        }
        _messages.value = _messages.value.filter { it.createdAt < createdAt }
        FileLogger.i("ChatRepository", "deleteMessageAndAfter() completed, remaining messages=${_messages.value.size}")
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun setMessages(messages: List<ChatMessage>) {
        // 如果正在流式接收 AI 回复，跳过覆盖以避免打断实时显示
        val hasStreaming = _messages.value.any { it.isStreaming && it.role == "assistant" }
        if (hasStreaming) {
            FileLogger.i("ChatRepository", "跳过 setMessages：正在流式接收消息")
            return
        }
        _messages.value = messages
        persistMessages()
    }

    /**
     * 从 Room 缓存加载指定会话的消息，并设置为当前消息列表。
     * @return 缓存中的消息列表，如果无缓存则返回空列表
     */
    suspend fun loadCachedMessages(sessionId: String): List<ChatMessage> {
        // 如果当前会话正在流式接收 AI 回复，跳过缓存覆盖，避免打断实时显示
        val hasStreaming = _messages.value.any { it.isStreaming && it.role == "assistant" }
        if (hasStreaming && currentSessionId == sessionId) {
            FileLogger.i("ChatRepository", "跳过 loadCachedMessages：正在流式接收消息 (sessionId=$sessionId)")
            return _messages.value
        }
        currentSessionId = sessionId
        val entities = withContext(Dispatchers.IO) {
            messageDao.getBySessionId(sessionId)
        }
        val cached = entities.map { it.toChatMessage() }
        _messages.value = cached
        FileLogger.i("ChatRepository", "从缓存加载 ${cached.size} 条消息 (sessionId=$sessionId)")
        return cached
    }

    private fun truncateResult(text: String, maxLength: Int = 3000): String {
        return if (text.length > maxLength) {
            text.take(maxLength) + "... [truncated, total=${text.length}]"
        } else {
            text
        }
    }

    private fun handleEvent(event: GatewayEvent) {
        try {
            when (event) {
                is GatewayEvent.MessageStart -> {
                    // 结束所有已有的 assistant streaming 消息，避免服务器发送多个 message.start 时积累空消息
                    // 同时将该消息中仍在 Running 的工具调用标记为完成（兼容 Server 未发送 tool.complete 的情况）
                    _messages.value = _messages.value.map {
                        if (it.isStreaming && it.role == "assistant") {
                            finalizeToolCallsInMessage(
                                it.copy(isStreaming = false, isReasoning = false)
                            )
                        } else it
                    }
                    persistMessages()
                    val msgId = event.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    val msg = ChatMessage(
                        id = msgId,
                        role = event.role.ifBlank { "assistant" },
                        content = "",
                        isStreaming = true,
                        isReasoning = true
                    )
                    _messages.value += msg
                    persistMessage(msg)
                }
                is GatewayEvent.MessageDelta -> {
                    FileLogger.d("ChatRepository", "MessageDelta received, delta='${event.delta.take(50)}', msgCount=${_messages.value.size}")
                    updateLastStreamingAssistantMessage { msg ->
                        FileLogger.d("ChatRepository", "MessageDelta: updating msg id=${msg.id}, content='${msg.content.take(30)}' -> '${(msg.content + event.delta).take(30)}'")
                        msg.copy(content = msg.content + event.delta)
                    }
                }
                is GatewayEvent.MessageComplete -> {
                    val messages = _messages.value.toMutableList()
                    val index = messages.indexOfLast { it.isStreaming && it.role == "assistant" }
                    if (index >= 0) {
                        val msg = messages[index]
                        messages[index] = finalizeToolCallsInMessage(
                            msg.copy(
                                isStreaming = false,
                                isReasoning = false,
                                reasoningText = event.reasoning.ifBlank { msg.reasoningText },
                                content = event.text.ifBlank { msg.content }
                            )
                        )
                        _messages.value = messages
                        persistMessage(messages[index])
                    }
                    wsClient.setLongRunningMode(false)
                }
                is GatewayEvent.ReasoningDelta -> {
                    updateLastStreamingAssistantMessage { it.copy(reasoningText = it.reasoningText + event.text) }
                }
                is GatewayEvent.ThinkingDelta -> {
                    updateLastStreamingAssistantMessage { it.copy(reasoningText = it.reasoningText + event.text) }
                }
                is GatewayEvent.ReasoningAvailable -> {
                    updateLastStreamingAssistantMessage { it.copy(reasoningText = event.text) }
                }
                is GatewayEvent.ToolStart -> {
                    val displayArgs = event.context.ifBlank { event.args }.ifBlank { event.name }
                    val toolCall = ToolCallDetail(
                        id = event.toolId,
                        name = event.name,
                        args = displayArgs,
                        status = ToolCallStatus.Running
                    )
                    appendToolCallToStreamingMessage(toolCall)
                    wsClient.setLongRunningMode(true)
                }
                is GatewayEvent.ToolProgress -> {
                    updateToolCall(event.toolId) { it.copy(result = it.result + event.text) }
                }
                is GatewayEvent.ToolComplete -> {
                    val rawResultText = when (val r = event.result) {
                        is kotlinx.serialization.json.JsonPrimitive -> r.content
                        is kotlinx.serialization.json.JsonArray -> r.joinToString("\n") { it.toString() }
                        is kotlinx.serialization.json.JsonObject -> r.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        else -> r?.toString() ?: ""
                    }
                    val resultText = truncateResult(rawResultText)
                    if (rawResultText.length > 3000) {
                        FileLogger.d("ChatRepository", "ToolComplete result truncated from ${rawResultText.length} to ${resultText.length} for tool=${event.toolId}")
                        FileLogger.d("ChatRepository", "ToolComplete raw result: ${rawResultText.take(2000)}")
                    }
                    val found = _messages.value.any { msg -> msg.toolCalls.any { it.id == event.toolId } }
                    if (found) {
                        updateToolCall(event.toolId) {
                            it.copy(
                                status = ToolCallStatus.Success,
                                result = resultText,
                                duration = event.duration,
                                summary = event.summary
                            )
                        }
                    } else {
                        // Server 可能未发送 tool.start（progress disabled），直接创建并追加
                        val toolCall = ToolCallDetail(
                            id = event.toolId,
                            name = event.name,
                            args = event.name,
                            status = ToolCallStatus.Success,
                            result = resultText,
                            duration = event.duration,
                            summary = event.summary
                        )
                        appendToolCallToStreamingMessage(toolCall)
                    }
                    wsClient.setLongRunningMode(false)
                }
                is GatewayEvent.ApprovalRequest -> {
                    val toolCall = ToolCallDetail(
                        id = event.id,
                        name = event.command,
                        args = event.description,
                        status = ToolCallStatus.Running
                    )
                    appendToolCallToStreamingMessage(toolCall)
                }
                is GatewayEvent.ReviewSummary -> {
                    val messages = _messages.value.toMutableList()
                    val index = messages.indexOfLast { it.role == "assistant" }
                    if (index >= 0) {
                        val msg = messages[index]
                        messages[index] = msg.copy(
                            systemEvents = msg.systemEvents + event.text
                        )
                        _messages.value = messages
                        FileLogger.i("ChatRepository", "ReviewSummary appended: ${event.text}")
                    } else {
                        FileLogger.w("ChatRepository", "ReviewSummary received but no assistant message found")
                    }
                }
                is GatewayEvent.ErrorEvent -> {
                    FileLogger.e("ChatRepository", "Gateway error: ${event.message}")
                    wsClient.setLongRunningMode(false)
                }
                is GatewayEvent.UnknownEvent -> {
                    FileLogger.d("ChatRepository", "未处理事件类型: ${event.type}")
                }
                is GatewayEvent.StatusUpdate -> {
                    FileLogger.d("ChatRepository", "状态更新: kind=${event.kind}, text=${event.text}")
                }
                is GatewayEvent.UnhandledEvent -> {
                    FileLogger.d("ChatRepository", "未处理事件: ${event.eventType}")
                }
                else -> Unit
            }
        } catch (e: Exception) {
            FileLogger.e("ChatRepository", "handleEvent failed for ${event::class.simpleName}", e)
        }
    }

    private fun updateLastStreamingAssistantMessage(transform: (ChatMessage) -> ChatMessage) {
        _messages.value = _messages.value.mapIndexed { index, msg ->
            if (index == _messages.value.lastIndex && msg.isStreaming && msg.role == "assistant") {
                transform(msg)
            } else msg
        }
    }

    private fun appendToolCallToStreamingMessage(toolCall: ToolCallDetail) {
        val messages = _messages.value.toMutableList()
        val index = messages.indexOfLast { it.isStreaming && it.role == "assistant" }
        if (index >= 0) {
            messages[index] = messages[index].copy(toolCalls = messages[index].toolCalls + toolCall)
            _messages.value = messages
            persistMessage(messages[index])
        }
    }

    private fun updateToolCall(id: String, transform: (ToolCallDetail) -> ToolCallDetail) {
        _messages.value = _messages.value.map { msg ->
            val updated = msg.copy(toolCalls = msg.toolCalls.map {
                if (it.id == id) transform(it) else it
            })
            if (updated.toolCalls != msg.toolCalls) {
                persistMessage(updated)
            }
            updated
        }
    }

    /**
     * 将消息中所有仍在 Running 状态的工具调用标记为完成。
     * 用于兼容 Server 在 tool_progress 关闭时不发送 tool.complete 的情况。
     */
    private fun finalizeToolCallsInMessage(msg: ChatMessage): ChatMessage {
        val finalized = msg.toolCalls.map {
            if (it.status == ToolCallStatus.Running) {
                it.copy(
                    status = ToolCallStatus.Success,
                    result = it.result.ifBlank { "已完成" }
                )
            } else it
        }
        return msg.copy(toolCalls = finalized)
    }

    // ==================== 持久化相关 ====================

    /**
     * 将单条消息持久化到 Room
     */
    private fun persistMessage(message: ChatMessage) {
        val sid = currentSessionId ?: return
        scope.launch {
            try {
                messageDao.upsert(message.toEntity(sid))
            } catch (e: Exception) {
                FileLogger.e("ChatRepository", "持久化消息失败: ${message.id}", e)
            }
        }
    }

    /**
     * 将当前所有消息持久化到 Room（用于 setMessages 等批量场景）
     * 会先清空该会话已有缓存，避免服务器返回的消息结构与本地不一致时产生残留。
     */
    private fun persistMessages() {
        val sid = currentSessionId ?: return
        val msgs = _messages.value
        scope.launch {
            try {
                messageDao.replaceAllForSession(sid, msgs.map { it.toEntity(sid) })
            } catch (e: Exception) {
                FileLogger.e("ChatRepository", "批量持久化消息失败", e)
            }
        }
    }

    /**
     * ChatMessage → MessageEntity
     */
    private fun ChatMessage.toEntity(sessionId: String): MessageEntity {
        return MessageEntity(
            id = id,
            sessionId = sessionId,
            role = role,
            content = content,
            reasoningText = reasoningText,
            isReasoning = isReasoning,
            imageUrl = imageUrl,
            toolCallsJson = json.encodeToString(
                kotlinx.serialization.serializer<List<ToolCallDetail>>(),
                toolCalls
            ),
            systemEventsJson = json.encodeToString(
                kotlinx.serialization.serializer<List<String>>(),
                systemEvents
            ),
            isStreaming = isStreaming,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            parentId = parentId
        )
    }

    /**
     * MessageEntity → ChatMessage
     */
    private fun MessageEntity.toChatMessage(): ChatMessage {
        val toolCalls = try {
            json.decodeFromString(
                kotlinx.serialization.serializer<List<ToolCallDetail>>(),
                toolCallsJson
            )
        } catch (e: Exception) {
            FileLogger.e("ChatRepository", "反序列化 toolCalls 失败: $toolCallsJson", e)
            emptyList()
        }
        val systemEvents = try {
            json.decodeFromString(
                kotlinx.serialization.serializer<List<String>>(),
                systemEventsJson
            )
        } catch (e: Exception) {
            FileLogger.e("ChatRepository", "反序列化 systemEvents 失败: $systemEventsJson", e)
            emptyList()
        }
        return ChatMessage(
            id = id,
            role = role,
            content = content,
            isStreaming = isStreaming,
            reasoningText = reasoningText,
            isReasoning = isReasoning,
            toolCalls = toolCalls,
            imageUrl = imageUrl,
            systemEvents = systemEvents,
            parentId = parentId,
            createdAt = createdAt
        )
    }

    // ==================== 图片压缩 ====================
    private fun compressImage(bytes: ByteArray): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        val targetMaxWidth = 1920
        val inSampleSize = calculateInSampleSize(options, targetMaxWidth)

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw IllegalArgumentException("无法解码图片")

        // 精确缩放
        val scaledBitmap = if (bitmap.width > targetMaxWidth) {
            val scaleFactor = targetMaxWidth.toFloat() / bitmap.width
            val newHeight = (bitmap.height * scaleFactor).toInt()
            Bitmap.createScaledBitmap(bitmap, targetMaxWidth, newHeight, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else bitmap

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        scaledBitmap.recycle()

        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * 计算 BitmapFactory 的 inSampleSize，使解码后宽度不超过 targetMaxWidth 的 2 倍
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, targetMaxWidth: Int): Int {
        val width = options.outWidth
        var inSampleSize = 1
        while (width / inSampleSize > targetMaxWidth * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
