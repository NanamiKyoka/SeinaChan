package com.seina.chan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.seina.chan.data.model.ConnectionProfile
import com.seina.chan.util.FileLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {

    val pageSize: Flow<Int> = dataStore.data.map { it[PAGE_SIZE_KEY] ?: 20 }
    val showToolCalls: Flow<Boolean> = dataStore.data.map { it[SHOW_TOOL_CALLS_KEY] ?: true }
    val showReasoning: Flow<Boolean> = dataStore.data.map { it[SHOW_REASONING_KEY] ?: true }
    val themeMode: Flow<String> = dataStore.data.map { it[THEME_MODE_KEY] ?: "system" }
    val showTimestamps: Flow<Boolean> = dataStore.data.map { it[SHOW_TIMESTAMPS_KEY] ?: false }
    val autoExpandReasoning: Flow<Boolean> = dataStore.data.map { it[AUTO_EXPAND_REASONING_KEY] ?: false }
    val autoExpandTools: Flow<Boolean> = dataStore.data.map { it[AUTO_EXPAND_TOOLS_KEY] ?: false }
    val connectionIp: Flow<String> = dataStore.data.map { it[CONNECTION_IP_KEY] ?: "" }
    val connectionPort: Flow<String> = dataStore.data.map { it[CONNECTION_PORT_KEY] ?: "" }
    val connectionToken: Flow<String> = dataStore.data.map { it[CONNECTION_TOKEN_KEY] ?: "" }
    val hiddenToolNames: Flow<Set<String>> = dataStore.data.map { it[HIDDEN_TOOL_NAMES_KEY] ?: emptySet() }
    /** 自定义工具链，格式为 "category|tool_name" 的 Set */
    val customTools: Flow<Set<String>> = dataStore.data.map { it[CUSTOM_TOOLS_KEY] ?: emptySet() }
    /** 用户选择的模型，格式为 "provider/model" */
    val selectedModel: Flow<String> = dataStore.data.map { it[SELECTED_MODEL_KEY] ?: "" }

    private val json = Json { ignoreUnknownKeys = true }

    val connectionProfiles: Flow<List<ConnectionProfile>> = dataStore.data.map { prefs ->
        val jsonStr = prefs[CONNECTION_PROFILES_KEY] ?: "[]"
        try {
            json.decodeFromString(serializer<List<ConnectionProfile>>(), jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeProfiles(prefs: Preferences): List<ConnectionProfile> {
        return try {
            json.decodeFromString(serializer<List<ConnectionProfile>>(), prefs[CONNECTION_PROFILES_KEY] ?: "[]")
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encodeProfiles(profiles: List<ConnectionProfile>): String {
        return json.encodeToString(serializer<List<ConnectionProfile>>(), profiles)
    }

    private suspend fun safeEdit(block: suspend (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (e: Exception) {
            FileLogger.e("SettingsRepository", "设置写入失败", e)
        }
    }

    suspend fun setPageSize(value: Int) {
        safeEdit { prefs -> prefs[PAGE_SIZE_KEY] = value }
    }

    suspend fun setShowToolCalls(value: Boolean) {
        safeEdit { prefs -> prefs[SHOW_TOOL_CALLS_KEY] = value }
    }

    suspend fun setShowReasoning(value: Boolean) {
        safeEdit { prefs -> prefs[SHOW_REASONING_KEY] = value }
    }

    suspend fun setThemeMode(value: String) {
        safeEdit { prefs -> prefs[THEME_MODE_KEY] = value }
    }

    suspend fun setShowTimestamps(value: Boolean) {
        safeEdit { prefs -> prefs[SHOW_TIMESTAMPS_KEY] = value }
    }

    suspend fun setAutoExpandReasoning(value: Boolean) {
        safeEdit { prefs -> prefs[AUTO_EXPAND_REASONING_KEY] = value }
    }

    suspend fun setAutoExpandTools(value: Boolean) {
        safeEdit { prefs -> prefs[AUTO_EXPAND_TOOLS_KEY] = value }
    }

    suspend fun setConnectionIp(value: String) {
        safeEdit { prefs -> prefs[CONNECTION_IP_KEY] = value }
    }

    suspend fun setConnectionPort(value: String) {
        safeEdit { prefs -> prefs[CONNECTION_PORT_KEY] = value }
    }

    suspend fun setConnectionToken(value: String) {
        safeEdit { prefs -> prefs[CONNECTION_TOKEN_KEY] = value }
    }

    suspend fun setHiddenToolNames(value: Set<String>) {
        safeEdit { prefs -> prefs[HIDDEN_TOOL_NAMES_KEY] = value }
    }

    suspend fun setCustomTools(value: Set<String>) {
        safeEdit { prefs -> prefs[CUSTOM_TOOLS_KEY] = value }
    }

    suspend fun setSelectedModel(value: String) {
        safeEdit { prefs -> prefs[SELECTED_MODEL_KEY] = value }
    }

    suspend fun saveConnectionProfiles(profiles: List<ConnectionProfile>) {
        safeEdit { prefs -> prefs[CONNECTION_PROFILES_KEY] = encodeProfiles(profiles) }
    }

    suspend fun addConnectionProfile(profile: ConnectionProfile) {
        safeEdit { prefs -> prefs[CONNECTION_PROFILES_KEY] = encodeProfiles(decodeProfiles(prefs) + profile) }
    }

    suspend fun updateConnectionProfile(profile: ConnectionProfile) {
        safeEdit { prefs ->
            prefs[CONNECTION_PROFILES_KEY] = encodeProfiles(
                decodeProfiles(prefs).map { if (it.id == profile.id) profile else it }
            )
        }
    }

    suspend fun deleteConnectionProfile(profileId: String) {
        safeEdit { prefs -> prefs[CONNECTION_PROFILES_KEY] = encodeProfiles(decodeProfiles(prefs).filter { it.id != profileId }) }
    }

    companion object {
        private val PAGE_SIZE_KEY = intPreferencesKey("page_size")
        private val SHOW_TOOL_CALLS_KEY = booleanPreferencesKey("show_tool_calls")
        private val SHOW_REASONING_KEY = booleanPreferencesKey("show_reasoning")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val SHOW_TIMESTAMPS_KEY = booleanPreferencesKey("show_timestamps")
        private val AUTO_EXPAND_REASONING_KEY = booleanPreferencesKey("auto_expand_reasoning")
        private val AUTO_EXPAND_TOOLS_KEY = booleanPreferencesKey("auto_expand_tools")
        private val CONNECTION_IP_KEY = stringPreferencesKey("ip")
        private val CONNECTION_PORT_KEY = stringPreferencesKey("port")
        private val CONNECTION_TOKEN_KEY = stringPreferencesKey("token")
        private val HIDDEN_TOOL_NAMES_KEY = stringSetPreferencesKey("hidden_tool_names")
        private val CUSTOM_TOOLS_KEY = stringSetPreferencesKey("custom_tools")
        private val CONNECTION_PROFILES_KEY = stringPreferencesKey("connection_profiles")
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
    }
}
