package com.seina.chan.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seina.chan.data.model.Session
import com.seina.chan.data.remote.ConnectionState
import com.seina.chan.data.repository.ConnectionRepository
import com.seina.chan.data.repository.SessionRepository
import com.seina.chan.data.repository.SettingsRepository
import com.seina.chan.util.FileLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    val searchQuery = MutableStateFlow("")

    val filteredSessions: StateFlow<List<Session>> = combine(
        _sessions,
        searchQuery
    ) { sessions, query ->
        if (query.isBlank()) {
            sessions
        } else {
            val normalizedQuery = query.trim().lowercase()
            sessions.filter { session ->
                session.title?.lowercase()?.contains(normalizedQuery) == true ||
                session.preview?.lowercase()?.contains(normalizedQuery) == true
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val connectionState: StateFlow<ConnectionState> = connectionRepository.connectionState

    private val _navigateToSession = MutableSharedFlow<String>()
    val navigateToSession: SharedFlow<String> = _navigateToSession.asSharedFlow()

    private val _newSessionCreated = MutableSharedFlow<String>()
    val newSessionCreated: SharedFlow<String> = _newSessionCreated.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // 分页状态
    private var offset = 0
    private var limit = 20

    init {
        viewModelScope.launch {
            settingsRepository.pageSize.collect { pageSize ->
                limit = pageSize
            }
        }
        // 搜索时自动加载所有会话，确保本地过滤可以覆盖全部会话
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .collect { query ->
                    if (query.isNotBlank() && _sessions.value.isEmpty()) {
                        loadAllSessions()
                    }
                }
        }
    }

    /**
     * 加载会话列表
     * @param refresh 是否为刷新操作：true 则重置 offset 并清空列表，false 则追加加载更多
     */
    fun loadSessions(refresh: Boolean = false) {
        viewModelScope.launch {
            val wasEmpty = _sessions.value.isEmpty()

            if (refresh) {
                offset = 0
                _sessions.value = emptyList()
                _isRefreshing.value = true
            } else {
                // 初始加载：先展示 Room 缓存，offset 保持 0
                if (wasEmpty) {
                    val cached = sessionRepository.getCachedSessions()
                    if (cached.isNotEmpty()) {
                        _sessions.value = cached
                        FileLogger.i("SessionListViewModel", "loadSessions() loaded ${cached.size} sessions from cache")
                    }
                }
                // 非初始加载（加载更多）才推进 offset
                if (!wasEmpty) {
                    offset += limit
                }
                _isLoading.value = true
            }
            _error.value = null
            try {
                val result = sessionRepository.fetchSessions(limit = limit, offset = offset)
                FileLogger.i("SessionListViewModel", "loadSessions(refresh=$refresh) succeeded, count=${result.sessions.size}, total=${result.total}")
                // 初始或刷新时替换列表，避免 cache + network 重复；加载更多时追加
                if (wasEmpty || refresh) {
                    _sessions.value = result.sessions
                } else {
                    _sessions.value = _sessions.value + result.sessions
                }
                _hasMore.value = result.hasMore
            } catch (e: Exception) {
                FileLogger.e("SessionListViewModel", "loadSessions(refresh=$refresh) failed", e)
                if (!refresh && _sessions.value.isNotEmpty()) {
                    offset -= limit
                    if (offset < 0) offset = 0
                }
                if (_sessions.value.isEmpty()) {
                    _sessions.value = emptyList()
                }
                _error.value = e.message
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 加载更多会话
     */
    fun loadMore() {
        if (!_hasMore.value || _isLoadingMore.value || _isLoading.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            offset += limit
            _error.value = null
            try {
                val result = sessionRepository.fetchSessions(limit = limit, offset = offset)
                FileLogger.i("SessionListViewModel", "loadMore() succeeded, count=${result.sessions.size}, total=${result.total}")
                _sessions.value = _sessions.value + result.sessions
                _hasMore.value = result.hasMore
            } catch (e: Exception) {
                FileLogger.e("SessionListViewModel", "loadMore() failed", e)
                offset -= limit
                if (offset < 0) offset = 0
                _error.value = e.message
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
    private fun loadAllSessions() {
        viewModelScope.launch {
            var currentOffset = 0
            var more = true
            while (more) {
                try {
                    val result = sessionRepository.fetchSessions(limit = limit, offset = currentOffset)
                    _sessions.value = _sessions.value + result.sessions
                    more = result.hasMore
                    currentOffset += limit
                } catch (e: Exception) {
                    FileLogger.e("SessionListViewModel", "loadAllSessions() failed at offset=$currentOffset", e)
                    break
                }
            }
        }
    }

    /**
     * 下拉刷新
     */
    fun refresh() {
        loadSessions(refresh = true)
    }

    fun createNewSession() {
        viewModelScope.launch {
            FileLogger.i("SessionListViewModel", "createNewSession() started")
            try {
                val result = sessionRepository.createSession()
                FileLogger.i("SessionListViewModel", "createNewSession() succeeded, storedId=${result.storedSessionId}, sid=${result.sid}")
                _newSessionCreated.emit(result.storedSessionId)
                loadSessions(refresh = true)
            } catch (e: Exception) {
                FileLogger.e("SessionListViewModel", "createNewSession() failed", e)
            }
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            _navigateToSession.emit(sessionId)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionRepository.disconnect()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            FileLogger.i("SessionListViewModel", "deleteSession() id=$sessionId")
            try {
                sessionRepository.deleteSession(sessionId)
                FileLogger.i("SessionListViewModel", "deleteSession() succeeded")
                loadSessions(refresh = true)
            } catch (e: Exception) {
                FileLogger.e("SessionListViewModel", "deleteSession() failed", e)
            }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch {
            FileLogger.i("SessionListViewModel", "renameSession() id=$sessionId, title=$title")
            try {
                sessionRepository.renameSession(sessionId, title)
                FileLogger.i("SessionListViewModel", "renameSession() succeeded")
                loadSessions(refresh = true)
            } catch (e: Exception) {
                FileLogger.e("SessionListViewModel", "renameSession() failed", e)
            }
        }
    }

    fun undoSession(sessionId: String) {
        viewModelScope.launch {
            FileLogger.i("SessionListViewModel", "undoSession() id=$sessionId")
            try {
                sessionRepository.undoSession(sessionId)
                FileLogger.i("SessionListViewModel", "undoSession() succeeded")
                loadSessions(refresh = true)
            } catch (e: Exception) {
                FileLogger.e("SessionListViewModel", "undoSession() failed", e)
            }
        }
    }

    fun compressSession(sessionId: String) {
        viewModelScope.launch {
            FileLogger.i("SessionListViewModel", "compressSession() id=$sessionId")
            try {
                sessionRepository.compressSession(sessionId)
                FileLogger.i("SessionListViewModel", "compressSession() succeeded")
                loadSessions(refresh = true)
            } catch (e: Exception) {
                FileLogger.e("SessionListViewModel", "compressSession() failed", e)
            }
        }
    }

    fun disconnectAndClear() {
        viewModelScope.launch {
            connectionRepository.disconnect()
            connectionRepository.clearConfig()
        }
    }
}
