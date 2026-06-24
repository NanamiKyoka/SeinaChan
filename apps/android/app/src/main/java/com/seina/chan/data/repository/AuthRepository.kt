package com.seina.chan.data.repository

import com.seina.chan.data.model.AuthMode
import com.seina.chan.util.FileLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PasswordLoginRequest(
    val provider: String,
    val username: String,
    val password: String,
    val next: String = ""
)

@Serializable
data class PasswordLoginResponse(
    val ok: Boolean,
    val error: String? = null
)

@Serializable
data class WsTicketResponse(
    val ticket: String? = null,
    val ttlSeconds: Int? = null,
    val error: String? = null
)

@Singleton
class AuthRepository @Inject constructor(
    private val apiHttpClient: HttpClient,
    private val json: Json
) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * 探测 auth 模式：GET /api/status → 读 auth_required
     * true=OAUTH（需要密码登录），false=TOKEN（直连）
     */
    suspend fun detectAuthMode(httpBaseUrl: String): AuthMode {
        val url = "$httpBaseUrl/api/status"
        FileLogger.i(TAG, "探测 auth 模式: GET $url")
        return try {
            val response = apiHttpClient.get(url)
            val body = response.bodyAsText()
            FileLogger.i(TAG, "/api/status 响应: $body")
            val obj = json.parseToJsonElement(body).jsonObject
            val authRequiredStr = obj["auth_required"]?.jsonPrimitive?.content
            val authRequired = authRequiredStr != null && (authRequiredStr == "true" || authRequiredStr == "1")
            if (authRequired) {
                FileLogger.i(TAG, "auth_required=true → OAUTH 模式")
                AuthMode.OAUTH
            } else {
                FileLogger.i(TAG, "auth_required=$authRequiredStr → TOKEN 模式")
                AuthMode.TOKEN
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "/api/status 探测失败", e)
            throw e
        }
    }

    /**
     * 密码登录：POST /auth/password-login
     * body: {"provider":"basic", "username":"...", "password":"...", "next":""}
     * Set-Cookie 自动被 apiHttpClient 的 HttpCookies 插件存储
     */
    suspend fun passwordLogin(httpBaseUrl: String, username: String, password: String): Boolean {
        val url = "$httpBaseUrl/auth/password-login"
        FileLogger.i(TAG, "密码登录: POST $url, username=$username")
        return try {
            val request = PasswordLoginRequest(
                provider = "basic",
                username = username,
                password = password
            )
            val requestJson = json.encodeToString(PasswordLoginRequest.serializer(), request)
            val response = apiHttpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestJson)
            }
            val body = response.bodyAsText()
            FileLogger.i(TAG, "password-login 响应: $body (status=${response.status})")
            if (response.status.isSuccess()) {
                val parsed = json.decodeFromString(PasswordLoginResponse.serializer(), body)
                if (parsed.ok) {
                    FileLogger.i(TAG, "密码登录成功")
                    true
                } else {
                    FileLogger.w(TAG, "密码登录失败: ${parsed.error ?: "未知错误"}")
                    false
                }
            } else {
                FileLogger.w(TAG, "密码登录 HTTP ${response.status}")
                false
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "密码登录异常", e)
            false
        }
    }

    /**
     * 换取 WS ticket：POST /api/auth/ws-ticket（cookie-authed）
     * 返回 ticket 字符串，或 null（登录过期/未登录）
     */
    suspend fun mintWsTicket(httpBaseUrl: String): String? {
        val url = "$httpBaseUrl/api/auth/ws-ticket"
        FileLogger.i(TAG, "换取 WS ticket: POST $url")
        return try {
            val response = apiHttpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            val body = response.bodyAsText()
            FileLogger.i(TAG, "ws-ticket 响应: $body (status=${response.status})")
            if (response.status.isSuccess()) {
                val parsed = json.decodeFromString(WsTicketResponse.serializer(), body)
                if (parsed.ticket != null) {
                    FileLogger.i(TAG, "获取 WS ticket 成功: ${parsed.ticket.take(8)}...")
                    parsed.ticket
                } else {
                    FileLogger.w(TAG, "ws-ticket 返回无 ticket: ${parsed.error ?: "未知错误"}")
                    null
                }
            } else if (response.status.value == 401) {
                FileLogger.w(TAG, "ws-ticket 返回 401，session 过期")
                null
            } else {
                FileLogger.w(TAG, "ws-ticket HTTP ${response.status}")
                null
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "ws-ticket 异常", e)
            null
        }
    }

    /**
     * 清除 session（退出时调用）
     */
    fun clearSession() {
        FileLogger.i(TAG, "清除 session cookies（内存中自动丢弃）")
    }
}
