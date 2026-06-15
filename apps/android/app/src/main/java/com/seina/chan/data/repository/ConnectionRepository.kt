package com.seina.chan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.seina.chan.data.model.ConnectionConfig
import com.seina.chan.data.remote.ConnectionState
import com.seina.chan.data.remote.HermesWsClient
import com.seina.chan.util.FileLogger
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class ConnectionRepository(
    private val wsClient: HermesWsClient,
    private val dataStore: DataStore<Preferences>
) {
    val connectionState: StateFlow<ConnectionState> = wsClient.state

    suspend fun connect(config: ConnectionConfig): Result<Unit> {
        val wsUrl = parseConnectionUrls(config.ip, config.port)
        FileLogger.i("ConnectionRepository", "connect() wsUrl=$wsUrl")
        return try {
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

    suspend fun testConnection(ip: String, port: String, token: String = ""): Result<String> {
        val wsUrl = parseConnectionUrls(ip, port)
        FileLogger.i("ConnectionRepository", "testConnection() wsUrl=$wsUrl")
        return try {
            val connected = wsClient.connect(wsUrl, token)
            if (connected) {
                // 等待一小段时间让 gateway.ready 有机会到达，或直接用连接成功作为判断
                wsClient.disconnect()
                FileLogger.i("ConnectionRepository", "testConnection() succeeded")
                Result.success("连接成功")
            } else {
                FileLogger.e("ConnectionRepository", "testConnection() failed")
                Result.failure(Exception("WebSocket 连接失败"))
            }
        } catch (e: Exception) {
            FileLogger.e("ConnectionRepository", "testConnection() exception", e)
            Result.failure(e)
        }
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

    private fun parseConnectionUrls(ip: String, port: String): String {
        val trimmedIp = ip.trim()
        val (wsScheme, cleanHost) = when {
            trimmedIp.startsWith("wss://", ignoreCase = true) ->
                Pair("wss", trimmedIp.removePrefix("wss://"))
            trimmedIp.startsWith("ws://", ignoreCase = true) ->
                Pair("ws", trimmedIp.removePrefix("ws://"))
            trimmedIp.startsWith("https://", ignoreCase = true) ->
                Pair("wss", trimmedIp.removePrefix("https://"))
            trimmedIp.startsWith("http://", ignoreCase = true) ->
                Pair("ws", trimmedIp.removePrefix("http://"))
            else -> Pair("ws", trimmedIp)
        }
        var host = cleanHost.removeSuffix("/")
        // 自动为裸 IPv6 地址添加方括号（域名不含冒号，不受影响）
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[$host]"
        }
        return "$wsScheme://$host:$port/api/ws"
    }

    companion object {
        private val IP_KEY = stringPreferencesKey("ip")
        private val PORT_KEY = stringPreferencesKey("port")
        private val TOKEN_KEY = stringPreferencesKey("token")
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val LAST_DB_SESSION_ID_KEY = stringPreferencesKey("last_db_session_id")
    }
}
