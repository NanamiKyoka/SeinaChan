package com.seina.chan.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seina.chan.data.remote.ConnectionState
import com.seina.chan.data.remote.HermesWsClient
import com.seina.chan.data.model.ChatMessage
import com.seina.chan.data.repository.ChatRepository
import com.seina.chan.data.repository.ConnectionRepository
import com.seina.chan.data.repository.SessionRepository
import com.seina.chan.data.repository.SettingsRepository
import com.seina.chan.service.ServiceSessionTracker
import com.seina.chan.util.FileLogger
import com.seina.chan.util.LogContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import com.seina.chan.data.model.SlashCommand
import com.seina.chan.data.remote.HermesMethods
import com.seina.chan.data.remote.GatewayEvent

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository,
    private val wsClient: HermesWsClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _inputState = MutableStateFlow(ChatUiState())

    private val _slashCommands = MutableStateFlow<List<SlashCommand>>(emptyList())
    val slashCommands: StateFlow<List<SlashCommand>> = _slashCommands

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    private var currentDbSessionId: String = ""
        set(value) {
            field = value
            ServiceSessionTracker.setSessionId(value)
        }
    private var currentWsSessionId: String = ""

    /** 上次已完成加载的会话 ID，防止 ChatScreen recompose 后同一会话重复触发 loadMessages */
    private var lastLoadedSessionId: String = ""

    private val _navigateToSession = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToSession: SharedFlow<String> = _navigateToSession.asSharedFlow()

    val events = chatRepository.events

    init {
        viewModelScope.launch {
            try {
                val lastId = connectionRepository.loadLastDbSessionId()
                if (!lastId.isNullOrEmpty() && currentDbSessionId.isEmpty()) {
                    currentDbSessionId = lastId
                    LogContext.sessionId = lastId
                    FileLogger.i("ChatViewModel", "Restored last dbSessionId=$lastId")
                }
            } catch (e: Exception) {
                FileLogger.w("ChatViewModel", "Failed to load last dbSessionId: ${e.message}")
            }
        }
        viewModelScope.launch {
            var previousState: ConnectionState = ConnectionState.Idle
            wsClient.state.collect { state ->
                if ((previousState is ConnectionState.Closed || previousState is ConnectionState.Error)
                    && state is ConnectionState.Open
                    && currentDbSessionId.isNotEmpty()
                ) {
                    FileLogger.i("ChatViewModel", "WebSocket reconnected, resuming session=$currentDbSessionId")
                    try {
                        val (sid, messages) = sessionRepository.resumeSession(currentDbSessionId)
                        currentWsSessionId = sid
                        rpcResumeMessages = currentDbSessionId to messages
                        FileLogger.i("ChatViewModel", "Auto-resume after reconnect succeeded, sid=$sid")
                    } catch (e: Exception) {
                        FileLogger.w("ChatViewModel", "Auto-resume after reconnect failed: ${e.message}")
                        currentWsSessionId = ""
                    }
                }
                previousState = state
            }
        }
        // 应用重建后若 WebSocket 已是 Open，立即 resume（避免 previousState 初始为 Idle 导致漏过状态变化）
        if (wsClient.state.value is ConnectionState.Open && currentDbSessionId.isNotEmpty()) {
            viewModelScope.launch {
                FileLogger.i("ChatViewModel", "App recreated with open WebSocket, resuming session=$currentDbSessionId")
                try {
                    val (sid, messages) = sessionRepository.resumeSession(currentDbSessionId)
                    currentWsSessionId = sid
                    rpcResumeMessages = currentDbSessionId to messages
                    FileLogger.i("ChatViewModel", "Immediate resume after recreation succeeded, sid=$sid")
                } catch (e: Exception) {
                    FileLogger.w("ChatViewModel", "Immediate resume after recreation failed: ${e.message}")
                    currentWsSessionId = ""
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.showToolCalls.collect { value ->
                _inputState.update { it.copy(showToolCalls = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showReasoning.collect { value ->
                _inputState.update { it.copy(showReasoning = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hiddenToolNames.collect { value ->
                _inputState.update { it.copy(hiddenToolNames = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showTimestamps.collect { value ->
                _inputState.update { it.copy(showTimestamps = value) }
            }
        }
        var statusClearJob: kotlinx.coroutines.Job? = null
        viewModelScope.launch {
            chatRepository.events.collect { event ->
                if (event is GatewayEvent.StatusUpdate) {
                    _inputState.update { it.copy(statusText = event.text) }
                    statusClearJob?.cancel()
                    statusClearJob = viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        _inputState.update { it.copy(statusText = "") }
                    }
                }
            }
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        _inputState,
        chatRepository.messages
    ) { inputState, messages ->
        val filtered = messages.filter { msg ->
            val matchesQuery = inputState.searchQuery.isBlank() || msg.content.contains(inputState.searchQuery, ignoreCase = true)
            val matchesRole = !inputState.searchFilterUserOnly || msg.role == "user"
            matchesQuery && matchesRole
        }
        inputState.copy(
            messages = filtered,
            canSend = (inputState.currentInput.isNotBlank() || inputState.selectedImages.isNotEmpty() || inputState.selectedVideo != null || inputState.selectedFiles.isNotEmpty()) && !inputState.isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun onInputChange(text: String) {
        _inputState.update { it.copy(currentInput = text) }
    }

    fun quoteMessage(message: ChatMessage) {
        _inputState.update { it.copy(quotedMessage = message) }
    }

    fun clearQuote() {
        _inputState.update { it.copy(quotedMessage = null) }
    }

    fun toggleSearchMode() {
        _inputState.update { it.copy(isSearchMode = !it.isSearchMode) }
    }

    fun onSearchQueryChange(query: String) {
        _inputState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearchFilterUserOnly() {
        _inputState.update { it.copy(searchFilterUserOnly = !it.searchFilterUserOnly) }
    }

    fun clearSearch() {
        _inputState.update { it.copy(isSearchMode = false, searchQuery = "", searchFilterUserOnly = false) }
    }

    fun stopGenerating() {
        viewModelScope.launch {
            try {
                chatRepository.stopGenerating(currentWsSessionId.ifEmpty { null })
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "stopGenerating() failed", e)
            }
        }
    }

    fun startEditingMessage(message: ChatMessage) {
        if (message.role != "user") return
        _editingMessage.value = message
        viewModelScope.launch {
            try {
                chatRepository.deleteMessageAndAfter(message.id)
                _inputState.update { it.copy(currentInput = message.content) }
                FileLogger.i("ChatViewModel", "startEditingMessage() id=${message.id}")
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "startEditingMessage() failed", e)
                _editingMessage.value = null
            }
        }
    }

    fun cancelEditing() {
        _editingMessage.value = null
        _inputState.update { it.copy(currentInput = "") }
    }

    fun resendMessage(content: String) {
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(content, currentWsSessionId, currentDbSessionId)
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "resendMessage() failed", e)
                _inputState.update { it.copy(error = e.message) }
            }
        }
    }

    suspend fun ensureSession(forceNew: Boolean = false): String {
        if (currentDbSessionId.isNotEmpty() && !forceNew) {
            return try {
                val (sid, messages) = sessionRepository.resumeSession(currentDbSessionId)
                currentWsSessionId = sid
                rpcResumeMessages = currentDbSessionId to messages
                chatRepository.setCurrentSessionId(currentDbSessionId)
                chatRepository.clearMessages()
                FileLogger.i("ChatViewModel", "ensureSession() resumed existing session: dbId=$currentDbSessionId, sid=$sid")
                currentDbSessionId
            } catch (e: Exception) {
                FileLogger.w("ChatViewModel", "ensureSession() stored session expired, creating new: ${e.message}")
                currentDbSessionId = ""
                currentWsSessionId = ""
                doCreateSession()
            }
        } else {
            if (forceNew) {
                currentDbSessionId = ""
                currentWsSessionId = ""
            }
            return doCreateSession()
        }
    }

    /** 创建新会话并保存到本地存储 */
    private suspend fun doCreateSession(): String {
        val result = sessionRepository.createSession()
        currentDbSessionId = result.storedSessionId
        currentWsSessionId = result.sid
        // 立即同步到 ChatRepository，保证后续事件持久化到正确的会话
        chatRepository.setCurrentSessionId(result.storedSessionId)
        // 创建全新会话时清空旧会话残留的消息
        chatRepository.clearMessages()
        try {
            connectionRepository.saveLastDbSessionId(currentDbSessionId)
        } catch (e: Exception) {
            FileLogger.w("ChatViewModel", "ensureSession() failed to save dbSessionId: ${e.message}")
        }
        FileLogger.i("ChatViewModel", "ensureSession() created dbId=${result.storedSessionId}, sid=${result.sid}")
        return currentDbSessionId
    }

    /**
     * 确保 WebSocket 会话已恢复且可用。
     * 如果当前 wsSessionId 有效则立即返回；否则等待 WebSocket 变为 Open 后尝试 resume。
     * 仅在会话已被服务端彻底清理（已不存在）且 resume 明确失败时才创建新会话。
     * @return true 表示已有可用的 wsSessionId，false 表示全部恢复手段均失败
     */
    private suspend fun ensureWsSessionReady(timeoutMs: Long = 15_000): Boolean {
        if (currentWsSessionId.isNotEmpty()) return true
        if (currentDbSessionId.isEmpty()) return false
        try {
            withTimeout(timeoutMs) {
                wsClient.state.first { it is ConnectionState.Open }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            FileLogger.w("ChatViewModel", "ensureWsSessionReady() timed out waiting for WebSocket")
            return false
        }
        return try {
            val (sid, messages) = sessionRepository.resumeSession(currentDbSessionId)
            currentWsSessionId = sid
            rpcResumeMessages = currentDbSessionId to messages
            FileLogger.i("ChatViewModel", "ensureWsSessionReady() resumed sid=$sid")
            true
        } catch (e: Exception) {
            FileLogger.e("ChatViewModel", "ensureWsSessionReady() resume failed: ${e.message}", e)
            false
        }
    }

    /** 最近一次 session.resume RPC 返回的消息列表，按会话 ID 索引 */
    private var rpcResumeMessages: Pair<String, List<ChatMessage>>? = null

    suspend fun resumeSession(): Result<String> {
        if (currentDbSessionId.isEmpty()) {
            return Result.failure(Exception("No session to resume"))
        }
        return try {
            val (sid, messages) = sessionRepository.resumeSession(currentDbSessionId)
            currentWsSessionId = sid
            rpcResumeMessages = currentDbSessionId to messages
            FileLogger.i("ChatViewModel", "resumeSession() succeeded: sid=$sid, rpcMessages=${messages.size}")
            Result.success(sid)
        } catch (e: Exception) {
            FileLogger.e("ChatViewModel", "resumeSession() failed", e)
            rpcResumeMessages = null
            Result.failure(e)
        }
    }

    suspend fun resumeSessionWithId(storedSessionId: String): Result<String> {
        if (currentDbSessionId == storedSessionId && currentWsSessionId.isNotEmpty()) {
            FileLogger.i("ChatViewModel", "resumeSessionWithId() skipped: already resumed for $storedSessionId")
            return Result.success(currentWsSessionId)
        }
        currentDbSessionId = storedSessionId
        LogContext.sessionId = storedSessionId
        chatRepository.setCurrentSessionId(storedSessionId)
        chatRepository.clearMessages()
        return try {
            connectionRepository.saveLastDbSessionId(currentDbSessionId)
            val (sid, messages) = sessionRepository.resumeSession(storedSessionId)
            currentWsSessionId = sid
            rpcResumeMessages = storedSessionId to messages
            FileLogger.i("ChatViewModel", "resumeSessionWithId() succeeded: sid=$sid, rpcMessages=${messages.size}")
            Result.success(sid)
        } catch (e: Exception) {
            FileLogger.e("ChatViewModel", "resumeSessionWithId() failed", e)
            rpcResumeMessages = null
            Result.failure(e)
        }
    }

    fun sendMessage() {
        val text = _inputState.value.currentInput.trim()
        val images = _inputState.value.selectedImages
        val video = _inputState.value.selectedVideo
        val files = _inputState.value.selectedFiles
        if (text.isEmpty() && images.isEmpty() && video == null && files.isEmpty()) return

        FileLogger.i("ChatViewModel", "sendMessage() dbSessionId=$currentDbSessionId, wsSessionId=$currentWsSessionId, textLength=${text.length}, images=${images.size}, video=$video, files=${files.size}")
        _inputState.update { it.copy(isLoading = true, error = null, selectedImages = emptyList(), selectedVideo = null, selectedFiles = emptyList()) }
        viewModelScope.launch {
            try {
                // 确保有可用的 wsSessionId；优先等待重连并 resume
                if (currentDbSessionId.isEmpty() || !ensureWsSessionReady()) {
                    if (currentDbSessionId.isEmpty()) {
                        ensureSession()
                    } else {
                        _inputState.update { it.copy(isLoading = false, error = "会话恢复失败，请稍后重试") }
                        return@launch
                    }
                }
                if (video != null) {
                    try {
                        chatRepository.sendVideo(video, context.contentResolver, currentWsSessionId, currentDbSessionId)
                        FileLogger.i("ChatViewModel", "sendVideo() succeeded for uri=$video")
                    } catch (e: Exception) {
                        FileLogger.e("ChatViewModel", "sendVideo() failed for uri=$video", e)
                    }
                }
                if (images.isNotEmpty()) {
                    sendImagesInternal(images)
                }
                if (files.isNotEmpty()) {
                    val fileContents = StringBuilder()
                    for (uri in files) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bytes = stream.readBytes()
                                val isBinary = bytes.contains(0.toByte())
                                val name = uri.lastPathSegment ?: "未知文件"
                                if (isBinary) {
                                    fileContents.append("[File: $name] (binary file, content not readable)\n\n---\n\n")
                                } else {
                                    val charset = java.nio.charset.Charset.defaultCharset()
                                    val content = String(bytes, charset)
                                    fileContents.append("[File: $name]\n\n$content\n\n---\n\n")
                                }
                            }
                        } catch (e: Exception) {
                            val name = uri.lastPathSegment ?: "未知文件"
                            fileContents.append("[File: $name] (binary file, content not readable)\n\n---\n\n")
                        }
                    }
                    val combinedText = if (text.isNotEmpty()) {
                        fileContents.toString() + text
                    } else {
                        fileContents.toString().trimEnd()
                    }
                    if (combinedText.isNotBlank()) {
                        chatRepository.sendMessage(combinedText, currentWsSessionId, currentDbSessionId, uiState.value.quotedMessage?.id)
                    }
                } else if (text.isNotEmpty()) {
                    chatRepository.sendMessage(text, currentWsSessionId, currentDbSessionId, uiState.value.quotedMessage?.id)
                } else if (images.isNotEmpty() || video != null) {
                    // 纯图片/视频场景：发送空 prompt 触发 assistant 回复
                    chatRepository.submitPrompt(currentWsSessionId)
                }
                clearQuote()
                _editingMessage.value = null
                _inputState.update { it.copy(currentInput = "", isLoading = false) }
                FileLogger.i("ChatViewModel", "sendMessage() succeeded")
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "sendMessage() failed", e)
                _inputState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * 设置选中的图片列表
     */
    fun onImagesSelected(uris: List<Uri>) {
        _inputState.update { it.copy(selectedImages = uris) }
    }

    /**
     * 移除一张选中的图片
     */
    fun removeSelectedImage(uri: Uri) {
        _inputState.update { it.copy(selectedImages = it.selectedImages.filter { u -> u != uri }) }
    }

    fun onVideoSelected(uri: Uri) {
        _inputState.update { it.copy(selectedVideo = uri) }
    }

    fun removeSelectedVideo() {
        _inputState.update { it.copy(selectedVideo = null) }
    }

    fun onFileSelected(uri: Uri) {
        _inputState.update { it.copy(selectedFiles = it.selectedFiles + uri) }
    }

    fun removeSelectedFile(uri: Uri) {
        _inputState.update { it.copy(selectedFiles = it.selectedFiles.filter { u -> u != uri }) }
    }

    private suspend fun sendImagesInternal(uris: List<Uri>) {
        for (uri in uris) {
            try {
                chatRepository.sendImage(uri, context.contentResolver, currentWsSessionId, currentDbSessionId)
                FileLogger.i("ChatViewModel", "sendImage() succeeded for uri=$uri")
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "sendImage() failed for uri=$uri", e)
                // 继续发送其余图片，不中断
            }
        }
    }

    /**
     * 发送图片消息
     * @param uri 选择的图片 URI
     */
    fun sendImage(uri: Uri) {
        FileLogger.i("ChatViewModel", "sendImage() dbSessionId=$currentDbSessionId, wsSessionId=$currentWsSessionId, uri=$uri")
        _inputState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                if (currentDbSessionId.isEmpty() || !ensureWsSessionReady()) {
                    if (currentDbSessionId.isEmpty()) {
                        ensureSession()
                    } else {
                        _inputState.update { it.copy(isLoading = false, error = "会话恢复失败，请稍后重试") }
                        return@launch
                    }
                }
                chatRepository.sendImage(uri, context.contentResolver, currentWsSessionId, currentDbSessionId)
                _inputState.update { it.copy(isLoading = false) }
                FileLogger.i("ChatViewModel", "sendImage() succeeded")
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "sendImage() failed", e)
                _inputState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadSlashCommands() {
        viewModelScope.launch {
            try {
                val result = wsClient.request(HermesMethods.COMMANDS_CATALOG)
                val obj = result.jsonObject
                val pairs = obj["pairs"]?.jsonArray ?: return@launch
                val commands = pairs.mapNotNull { element ->
                    val arr = element.jsonArray
                    if (arr.size >= 2) {
                        val name = arr[0].jsonPrimitive.content
                        val desc = arr[1].jsonPrimitive.content
                        SlashCommand(name, desc)
                    } else null
                }
                _slashCommands.value = commands
                FileLogger.i("ChatViewModel", "Loaded ${commands.size} slash commands")
            } catch (e: Exception) {
                FileLogger.w("ChatViewModel", "Failed to load slash commands: ${e.message}")
            }
        }
    }

    fun loadMessages(dbSessionId: String) {
        FileLogger.i("ChatViewModel", "loadMessages() dbSessionId=$dbSessionId")
        if (dbSessionId.isEmpty()) {
            _inputState.update { it.copy(error = "sessionId is empty, cannot load messages") }
            return
        }
        // 同一会话已加载过 → 跳过，防止 ChatScreen recompose 时用服务端/缓存数据覆盖实时流式消息
        if (lastLoadedSessionId == dbSessionId) {
            FileLogger.i("ChatViewModel", "loadMessages() skipped: already loaded for dbSessionId=$dbSessionId")
            return
        }
        val isSessionSwitch = currentDbSessionId != dbSessionId
        currentDbSessionId = dbSessionId
        LogContext.sessionId = dbSessionId
        // 仅切换会话时才重置 wsSessionId，同会话重建时保留以支持 resumeSessionWithId 跳过
        if (isSessionSwitch) {
            currentWsSessionId = ""
        }
        viewModelScope.launch {
            try {
                connectionRepository.saveLastDbSessionId(currentDbSessionId)
            } catch (e: Exception) {
                FileLogger.w("ChatViewModel", "loadMessages() failed to save dbSessionId: ${e.message}")
            }
        }
        _inputState.update { it.copy(isLoading = true, error = null) }

        // 单协程顺序执行：先加载缓存快速展示，再从服务端拉取最新消息。
        // 避免两个并发 launch 中 loadCachedMessages 在 setMessages 的异步持久化
        // 完成前读到旧数据，覆盖掉刚拉回的服务端消息。
        viewModelScope.launch {
            try {
                val cached = chatRepository.loadCachedMessages(dbSessionId)
                if (cached.isNotEmpty()) {
                    FileLogger.i("ChatViewModel", "loadMessages() showed ${cached.size} cached messages")
                    _inputState.update { it.copy(isLoading = false, error = null) }
                }
            } catch (e: Exception) {
                FileLogger.w("ChatViewModel", "loadMessages() cache load failed: ${e.message}")
            }

            try {
                val history = sessionRepository.fetchMessages(dbSessionId)
                FileLogger.i("ChatViewModel", "loadMessages() fetched ${history.size} messages from server")
                val finalMessages = rpcResumeMessages?.let { (rpcSid, rpcMsgs) ->
                    if (rpcSid == dbSessionId && rpcMsgs.size > history.size) {
                        // REST 消息有完整 tool call 数据，保留为基础；
                        // RPC 多出的消息（纯文本格式）补在末尾
                        val extra = rpcMsgs.drop(history.size)
                        FileLogger.w("ChatViewModel", "loadMessages() REST ${history.size} + RPC extra ${extra.size} = ${history.size + extra.size} total")
                        history + extra
                    } else null
                } ?: history
                chatRepository.setMessages(finalMessages)
                _inputState.update { it.copy(isLoading = false, error = null) }
                lastLoadedSessionId = dbSessionId
            } catch (e: Exception) {
                FileLogger.i("ChatViewModel", "loadMessages() no messages for new session $dbSessionId")
                lastLoadedSessionId = dbSessionId
                _inputState.update { it.copy(isLoading = false, error = null) }
            }
        }
    }

    fun getCurrentDbSessionId(): String = currentDbSessionId

    fun respondApproval(requestId: String, approved: Boolean, allowPermanent: Boolean = false) {
        FileLogger.i("ChatViewModel", "respondApproval() requestId=$requestId, approved=$approved, allowPermanent=$allowPermanent")
        viewModelScope.launch {
            try {
                chatRepository.respondApproval(requestId, approved, allowPermanent)
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "respondApproval() failed", e)
                _inputState.update { it.copy(error = e.message) }
            }
        }
    }

    fun respondClarify(requestId: String, response: String) {
        FileLogger.i("ChatViewModel", "respondClarify() requestId=$requestId")
        viewModelScope.launch {
            try {
                chatRepository.respondClarify(requestId, response)
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "respondClarify() failed", e)
                _inputState.update { it.copy(error = e.message) }
            }
        }
    }

    fun respondSecret(requestId: String, secret: String) {
        FileLogger.i("ChatViewModel", "respondSecret() requestId=$requestId")
        viewModelScope.launch {
            try {
                chatRepository.respondSecret(requestId, secret)
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "respondSecret() failed", e)
                _inputState.update { it.copy(error = e.message) }
            }
        }
    }

    fun respondSudo(requestId: String, password: String) {
        FileLogger.i("ChatViewModel", "respondSudo() requestId=$requestId")
        viewModelScope.launch {
            try {
                chatRepository.respondSudo(requestId, password)
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "respondSudo() failed", e)
                _inputState.update { it.copy(error = e.message) }
            }
        }
    }

    fun branchFromMessage(messageId: String) {
        FileLogger.i("ChatViewModel", "branchFromMessage() messageId=$messageId")
        viewModelScope.launch {
            try {
                val newSessionId = sessionRepository.branchFromMessage(messageId)
                if (!newSessionId.isNullOrEmpty()) {
                    currentDbSessionId = newSessionId
                    currentWsSessionId = newSessionId
                    try {
                        connectionRepository.saveLastDbSessionId(currentDbSessionId)
                    } catch (e: Exception) {
                        FileLogger.w("ChatViewModel", "branchFromMessage() failed to save dbSessionId: ${e.message}")
                    }
                    _navigateToSession.tryEmit(newSessionId)
                    FileLogger.i("ChatViewModel", "branchFromMessage() navigated to newSessionId=$newSessionId")
                }
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "branchFromMessage() failed", e)
                _inputState.update { it.copy(error = e.message) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        LogContext.sessionId = null
    }
}
