package com.seina.chan.ui.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seina.chan.data.model.ConnectionConfig
import com.seina.chan.data.model.ConnectionProfile
import com.seina.chan.data.remote.ConnectionState
import com.seina.chan.data.repository.ConnectionRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TestStatus {
    object None : TestStatus()
    object Testing : TestStatus()
    data class Success(val message: String) : TestStatus()
    data class Error(val message: String) : TestStatus()
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    data class ConnectUiState(
        val ip: String = "",
        val port: String = "9119",
        val token: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val testStatus: TestStatus = TestStatus.None
    )

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private val _navigateToChat = MutableSharedFlow<Boolean>()
    val navigateToChat: SharedFlow<Boolean> = _navigateToChat.asSharedFlow()

    init {
        viewModelScope.launch {
            FileLogger.i("ConnectViewModel", "init - loading config")
            val config = connectionRepository.loadConfig()
            if (config != null) {
                val ip = config.ip
                val port = config.port.ifBlank { "9119" }
                _uiState.value = _uiState.value.copy(ip = ip, port = port, token = config.token)
                FileLogger.i("ConnectViewModel", "init - loaded config ip=$ip, port=$port")

                // 若当前已连接且已有配置，自动跳转到聊天界面
                if (connectionRepository.connectionState.value is ConnectionState.Open) {
                    FileLogger.i("ConnectViewModel", "init - already connected, auto-navigate to chat")
                    _navigateToChat.emit(true)
                }
            } else {
                FileLogger.i("ConnectViewModel", "init - no saved config")
            }
        }
    }

    fun onIpChange(ip: String) {
        _uiState.value = _uiState.value.copy(ip = ip, error = null, testStatus = TestStatus.None)
    }

    fun onPortChange(port: String) {
        _uiState.value = _uiState.value.copy(port = port, error = null, testStatus = TestStatus.None)
    }

    fun onTokenChange(token: String) {
        _uiState.value = _uiState.value.copy(token = token, error = null)
    }

    fun testConnection() {
        viewModelScope.launch {
            val ip = _uiState.value.ip.trim()
            val port = _uiState.value.port.trim()
            FileLogger.i("ConnectViewModel", "testConnection() ip=$ip, port=$port")

            if (ip.isBlank() || port.isBlank()) {
                _uiState.value = _uiState.value.copy(testStatus = TestStatus.Error("请输入 IP 和端口"))
                return@launch
            }

            _uiState.value = _uiState.value.copy(testStatus = TestStatus.Testing)
            val result = connectionRepository.testConnection(ip, port)
            if (result.isSuccess) {
                FileLogger.i("ConnectViewModel", "testConnection() succeeded")
                _uiState.value = _uiState.value.copy(testStatus = TestStatus.Success(result.getOrDefault("连接成功")))
            } else {
                val message = result.exceptionOrNull()?.message ?: "连接失败"
                FileLogger.e("ConnectViewModel", "testConnection() failed: $message")
                _uiState.value = _uiState.value.copy(testStatus = TestStatus.Error(message))
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            val ip = _uiState.value.ip.trim()
            val port = _uiState.value.port.trim()
            val token = _uiState.value.token.trim()
            FileLogger.i("ConnectViewModel", "connect() ip=$ip, port=$port, tokenPrefix=${token.take(4)}")

            if (ip.isBlank()) {
                _uiState.value = _uiState.value.copy(error = "请输入服务器地址")
                return@launch
            }
            if (port.isBlank()) {
                _uiState.value = _uiState.value.copy(error = "请输入端口")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val config = ConnectionConfig(ip = ip, port = port, token = token)
            val result = connectionRepository.connect(config)

            if (result.isSuccess) {
                FileLogger.i("ConnectViewModel", "connect() succeeded")
                connectionRepository.saveConfig(config)
                _uiState.value = _uiState.value.copy(isLoading = false)
                _navigateToChat.emit(true)
            } else {
                val message = result.exceptionOrNull()?.message ?: "连接失败"
                FileLogger.e("ConnectViewModel", "connect() failed: $message")
                _uiState.value = _uiState.value.copy(isLoading = false, error = message)
            }
        }
    }

    val connectionProfiles: StateFlow<List<ConnectionProfile>> =
        settingsRepository.connectionProfiles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveCurrentAsProfile(name: String) {
        viewModelScope.launch {
            val current = _uiState.value
            val profile = ConnectionProfile(
                name = name,
                ip = current.ip.trim(),
                port = current.port.trim(),
                token = current.token.trim()
            )
            settingsRepository.addConnectionProfile(profile)
            FileLogger.i("ConnectViewModel", "saveCurrentAsProfile() name=$name")
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            settingsRepository.deleteConnectionProfile(profileId)
            FileLogger.i("ConnectViewModel", "deleteProfile() id=$profileId")
        }
    }

    fun loadProfile(profile: ConnectionProfile) {
        _uiState.value = _uiState.value.copy(
            ip = profile.ip,
            port = profile.port.ifBlank { "9119" },
            token = profile.token,
            error = null,
            testStatus = TestStatus.None
        )
        FileLogger.i("ConnectViewModel", "loadProfile() name=${profile.name}")
    }

    fun updateProfile(profile: ConnectionProfile) {
        viewModelScope.launch {
            settingsRepository.updateConnectionProfile(profile)
            FileLogger.i("ConnectViewModel", "updateProfile() name=${profile.name}")
        }
    }

    fun renameProfile(profileId: String, newName: String) {
        viewModelScope.launch {
            val profiles = connectionProfiles.value
            val target = profiles.find { it.id == profileId } ?: return@launch
            settingsRepository.updateConnectionProfile(target.copy(name = newName))
            FileLogger.i("ConnectViewModel", "renameProfile() id=$profileId, newName=$newName")
        }
    }

    fun clearSavedConfig() {
        viewModelScope.launch {
            FileLogger.i("ConnectViewModel", "clearSavedConfig() called")
            connectionRepository.disconnect()
            connectionRepository.clearConfig()
            _uiState.value = ConnectUiState()
        }
    }
}
