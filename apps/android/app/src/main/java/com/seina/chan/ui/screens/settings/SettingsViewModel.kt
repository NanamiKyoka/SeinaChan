package com.seina.chan.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seina.chan.data.model.ConnectionConfig
import com.seina.chan.data.remote.HermesWsClient
import com.seina.chan.data.remote.HermesMethods
import com.seina.chan.data.model.ConnectionProfile
import com.seina.chan.data.repository.ConnectionRepository
import com.seina.chan.data.repository.SettingsRepository
import com.seina.chan.util.FileLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelOption(
    val provider: String,
    val display: String,
    val modelId: String
)

data class SettingsUiState(
    val pageSize: Int = 20,
    val showToolCalls: Boolean = true,
    val showReasoning: Boolean = true,
    val themeMode: String = "system",
    val showTimestamps: Boolean = false,
    val autoExpandReasoning: Boolean = false,
    val autoExpandTools: Boolean = false,
    val connectionIp: String = "",
    val connectionPort: String = "",
    val connectionToken: String = "",
    val connectionProfiles: List<ConnectionProfile> = emptyList(),
    val hiddenToolNames: Set<String> = emptySet(),
    /** 自定义工具链，格式为 "category|tool_name" */
    val customTools: Set<String> = emptySet(),
    /** 用户选择的模型，格式为 "provider/model" 或纯 modelId */
    val selectedModel: String = "",
    /** 从服务端获取的可用模型列表 */
    val availableModels: List<ModelOption> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelError: String? = null,
    /** 工具列表 */
    val tools: List<SettingsViewModel.ToolInfo> = emptyList(),
    val toolsError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val connectionRepository: ConnectionRepository,
    private val wsClient: HermesWsClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            settingsRepository.pageSize.collect { value ->
                _uiState.update { it.copy(pageSize = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showToolCalls.collect { value ->
                _uiState.update { it.copy(showToolCalls = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showReasoning.collect { value ->
                _uiState.update { it.copy(showReasoning = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { value ->
                _uiState.update { it.copy(themeMode = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showTimestamps.collect { value ->
                _uiState.update { it.copy(showTimestamps = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoExpandReasoning.collect { value ->
                _uiState.update { it.copy(autoExpandReasoning = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoExpandTools.collect { value ->
                _uiState.update { it.copy(autoExpandTools = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.connectionIp.collect { value ->
                _uiState.update { it.copy(connectionIp = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.connectionPort.collect { value ->
                _uiState.update { it.copy(connectionPort = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.connectionToken.collect { value ->
                _uiState.update { it.copy(connectionToken = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.connectionProfiles.collect { value ->
                _uiState.update { it.copy(connectionProfiles = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hiddenToolNames.collect { value ->
                _uiState.update { it.copy(hiddenToolNames = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.customTools.collect { value ->
                _uiState.update { it.copy(customTools = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.selectedModel.collect { value ->
                _uiState.update { it.copy(selectedModel = value) }
            }
        }
    }

    fun fetchModelOptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, modelError = null) }
            try {
                val result = wsClient.request(HermesMethods.MODEL_OPTIONS)
                val models = mutableListOf<ModelOption>()
                val providersArray = result.jsonObject["providers"]?.jsonArray
                providersArray?.forEach { providerElement ->
                    val providerObj = providerElement.jsonObject
                    val authenticated = providerObj["authenticated"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    val modelsArray = providerObj["models"]?.jsonArray
                    if (authenticated && modelsArray != null && modelsArray.isNotEmpty()) {
                        val slug = providerObj["slug"]?.jsonPrimitive?.content ?: ""
                        val name = providerObj["name"]?.jsonPrimitive?.content ?: slug
                        modelsArray.forEach { modelElement ->
                            val modelId = modelElement.jsonPrimitive.content
                            models.add(ModelOption(slug, "$name / $modelId", modelId))
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        availableModels = models,
                        isLoadingModels = false,
                        modelError = null
                    )
                }
                FileLogger.i("SettingsViewModel", "获取模型列表成功: ${models.size} 个模型")
            } catch (e: Exception) {
                FileLogger.e("SettingsViewModel", "获取模型列表失败", e)
                _uiState.update {
                    it.copy(
                        isLoadingModels = false,
                        modelError = "获取模型列表失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun setSelectedModel(modelId: String) {
        viewModelScope.launch {
            val option = _uiState.value.availableModels.find { it.modelId == modelId }
            val provider = option?.provider ?: ""
            try {
                if (provider.isNotBlank() && modelId.isNotBlank()) {
                    wsClient.request(HermesMethods.SLASH_EXEC, buildJsonObject {
                        put("command", "model")
                        put("arg", "$provider/$modelId")
                    })
                    FileLogger.i("SettingsViewModel", "设置模型成功: provider=$provider, model=$modelId")
                }
            } catch (e: Exception) {
                FileLogger.e("SettingsViewModel", "设置模型失败", e)
                _uiState.update { it.copy(modelError = "设置模型失败: ${e.message}") }
            }
            settingsRepository.setSelectedModel(modelId)
        }
    }

    fun setPageSize(value: Int) {
        viewModelScope.launch {
            settingsRepository.setPageSize(value)
        }
    }

    fun setShowToolCalls(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowToolCalls(value)
        }
    }

    fun setShowReasoning(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowReasoning(value)
        }
    }

    fun setThemeMode(value: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(value)
        }
    }

    fun setShowTimestamps(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTimestamps(value)
        }
    }

    fun setAutoExpandReasoning(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoExpandReasoning(value)
        }
    }

    fun setAutoExpandTools(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoExpandTools(value)
        }
    }

    fun setConnectionIp(value: String) {
        viewModelScope.launch {
            settingsRepository.setConnectionIp(value)
        }
    }

    fun setConnectionPort(value: String) {
        viewModelScope.launch {
            settingsRepository.setConnectionPort(value)
        }
    }

    fun setConnectionToken(value: String) {
        viewModelScope.launch {
            settingsRepository.setConnectionToken(value)
        }
    }

    fun setHiddenToolNames(value: Set<String>) {
        viewModelScope.launch {
            settingsRepository.setHiddenToolNames(value)
        }
    }

    fun setCustomTools(value: Set<String>) {
        viewModelScope.launch {
            settingsRepository.setCustomTools(value)
        }
    }

    /** 添加自定义工具链 */
    fun addCustomTool(category: String, toolName: String) {
        val entry = "$category|$toolName"
        val current = _uiState.value.customTools
        if (entry !in current) {
            setCustomTools(current + entry)
        }
    }


    /** 删除自定义工具链 */
    fun removeCustomTool(category: String, toolName: String) {
        val entry = "$category|$toolName"
        val current = _uiState.value.customTools
        // 同时从隐藏列表中移除
        val hidden = _uiState.value.hiddenToolNames
        if (toolName in hidden) {
            setHiddenToolNames(hidden - toolName)
        }
        setCustomTools(current - entry)
    }

    /** 工具信息 */
    data class ToolInfo(
        val name: String,
        val enabled: Boolean = true,
        val description: String = ""
    )

    /** 从服务端拉取工具列表 */
    fun listTools() {
        viewModelScope.launch {
            try {
                val result = wsClient.request(HermesMethods.TOOLS_LIST)
                val tools = mutableListOf<ToolInfo>()
                val resultObj = result.jsonObject
                val toolsetArray = resultObj["toolsets"]?.jsonArray
                    ?: resultObj["tools"]?.jsonArray
                    ?: resultObj["items"]?.jsonArray
                if (toolsetArray != null) {
                    for (item in toolsetArray) {
                        val obj = item.jsonObject
                        tools.add(ToolInfo(
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            enabled = obj["enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                            description = obj["description"]?.jsonPrimitive?.content ?: ""
                        ))
                    }
                }
                _uiState.update { it.copy(tools = tools, toolsError = null) }
                FileLogger.i("SettingsViewModel", "工具列表加载成功: ${tools.size} 个工具")
            } catch (e: Exception) {
                FileLogger.e("SettingsViewModel", "加载工具列表失败", e)
                _uiState.update { it.copy(toolsError = "加载失败: ${e.message}") }
            }
        }
    }

    /** 启用/禁用工具 */
    fun configureTool(toolName: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("action", if (enabled) "enable" else "disable")
                    put("names", buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive(toolName))
                    })
                }
                wsClient.request(HermesMethods.TOOLS_CONFIGURE, params)
                FileLogger.i("SettingsViewModel", "工具配置成功: $toolName enabled=$enabled")
                // 刷新列表
                listTools()
            } catch (e: Exception) {
                FileLogger.e("SettingsViewModel", "配置工具失败: $toolName", e)
                _uiState.update { it.copy(toolsError = "配置失败: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionRepository.disconnect()
        }
    }

    fun saveAndReconnect(ip: String, port: String, token: String) {
        setConnectionIp(ip)
        setConnectionPort(port)
        setConnectionToken(token)
        viewModelScope.launch {
            connectionRepository.connect(ConnectionConfig(ip, port, token))
        }
    }

    fun loadProfile(profile: ConnectionProfile) {
        saveAndReconnect(profile.ip, profile.port, profile.token)
    }
}
