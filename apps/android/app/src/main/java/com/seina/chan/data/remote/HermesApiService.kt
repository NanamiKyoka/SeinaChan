package com.seina.chan.data.remote

import com.seina.chan.util.FileLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HermesStatus(
    val status: String,
    val version: String? = null
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)

@Serializable
data class MessagesResponse(
    val session_id: String = "",
    val messages: List<MessageDto> = emptyList()
)

@Serializable
data class SessionDto(
    val id: String,
    val title: String? = null,
    val preview: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("last_active") val lastActiveAt: Double? = null
)

@Serializable
data class MessageDto(
    val id: Long = 0,
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: JsonElement? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val timestamp: Double? = null
)

@Serializable
data class ToolCallDto(
    val id: String,
    val toolName: String,
    val input: String = "",
    val output: String = "",
    val status: String? = null
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String? = null,
    val provider: String? = null
)

@Serializable
data class ProviderRow(
    val slug: String,
    val name: String? = null,
    val models: List<String> = emptyList(),
    val authenticated: Boolean = false
)

@Serializable
data class ModelOptionsResponse(
    val providers: List<ProviderRow> = emptyList(),
    val model: String = "",
    val provider: String = ""
)

@Serializable
data class ModelAssignment(
    val scope: String = "main",
    val provider: String,
    val model: String
)

@Serializable
data class RenameResponse(
    val ok: Boolean,
    val title: String? = null
)

@Serializable
data class SessionSearchResultDto(
    val id: String,
    val title: String? = null,
    val preview: String? = null,
    val snippet: String? = null,
    val keyword: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("last_active") val lastActiveAt: Double? = null
)

@Serializable
data class SessionsSearchResponse(
    val sessions: List<SessionSearchResultDto> = emptyList(),
    val total: Int = 0
)

class HermesApiService(
    private val client: HttpClient
) {
    private var baseUrl: String = ""
    private var sessionToken: String = ""

    fun setConfig(baseUrl: String, token: String) {
        this.baseUrl = baseUrl.removeSuffix("/")
        this.sessionToken = token
    }

    private suspend inline fun <reified T> get(path: String): T {
        FileLogger.d("HermesApiService", "GET $path")
        return try {
            val response = client.get("$baseUrl$path") {
                header("X-Hermes-Session-Token", sessionToken)
            }
            FileLogger.d("HermesApiService", "GET $path -> status=${response.status}")
            response.body()
        } catch (e: Exception) {
            FileLogger.e("HermesApiService", "GET $path failed", e)
            throw e
        }
    }

    private suspend inline fun <reified T> patch(path: String, body: Any): T {
        FileLogger.d("HermesApiService", "PATCH $path")
        return try {
            val response = client.patch("$baseUrl$path") {
                header("X-Hermes-Session-Token", sessionToken)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            FileLogger.d("HermesApiService", "PATCH $path -> status=${response.status}")
            response.body()
        } catch (e: Exception) {
            FileLogger.e("HermesApiService", "PATCH $path failed", e)
            throw e
        }
    }

    private suspend fun delete(path: String): Boolean {
        FileLogger.d("HermesApiService", "DELETE $path")
        return try {
            val response = client.delete("$baseUrl$path") {
                header("X-Hermes-Session-Token", sessionToken)
            }
            FileLogger.d("HermesApiService", "DELETE $path -> status=${response.status}")
            response.status.value in 200..299
        } catch (e: Exception) {
            FileLogger.e("HermesApiService", "DELETE $path failed", e)
            throw e
        }
    }

    suspend fun getStatus(): HermesStatus = get("/api/status")
    suspend fun getSessions(limit: Int = 20, offset: Int = 0): SessionsResponse = get("/api/sessions?limit=$limit&offset=$offset")
    suspend fun getSessionMessages(sessionId: String): MessagesResponse = get("/api/sessions/$sessionId/messages")
    suspend fun getModelInfo(): ModelInfo = get("/api/model/info")
    suspend fun getModelOptions(): ModelOptionsResponse = get("/api/model/options")

    suspend fun setModel(assignment: ModelAssignment): ModelAssignment {
        FileLogger.d("HermesApiService", "POST /api/model/set provider=${assignment.provider}, model=${assignment.model}")
        return try {
            val response = client.post("$baseUrl/api/model/set") {
                header("X-Hermes-Session-Token", sessionToken)
                contentType(ContentType.Application.Json)
                setBody(assignment)
            }
            FileLogger.d("HermesApiService", "POST /api/model/set -> status=${response.status}")
            response.body()
        } catch (e: Exception) {
            FileLogger.e("HermesApiService", "POST /api/model/set failed", e)
            throw e
        }
    }

    suspend fun deleteSession(sessionId: String): Boolean =
        delete("/api/sessions/$sessionId")

    suspend fun renameSession(sessionId: String, title: String): RenameResponse =
        patch("/api/sessions/$sessionId", mapOf("title" to title))

    suspend fun searchSessions(query: String): SessionsSearchResponse =
        get("/api/sessions/search?q=${query}")
}
