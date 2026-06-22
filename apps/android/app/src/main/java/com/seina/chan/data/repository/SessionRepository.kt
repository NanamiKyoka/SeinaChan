package com.seina.chan.data.repository

import com.seina.chan.data.local.dao.SentImageDao
import com.seina.chan.data.local.entity.SentImageEntity
import com.seina.chan.data.model.ChatMessage
import com.seina.chan.data.model.Session
import com.seina.chan.data.model.ToolCallDetail
import com.seina.chan.data.model.ToolCallStatus
import com.seina.chan.data.remote.HermesMethods
import com.seina.chan.data.remote.HermesWsClient
import com.seina.chan.util.FileLogger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class CreateSessionResult(
    val sid: String,
    val storedSessionId: String
)

/**
 * 会话分页结果
 */
data class SessionsPageResult(
    val sessions: List<Session>,
    val total: Int,
    val hasMore: Boolean
)

class SessionRepository(
    private val wsClient: HermesWsClient,
    private val sentImageDao: SentImageDao
) {
    suspend fun fetchSessions(limit: Int = 20, offset: Int = 0): SessionsPageResult {
        val result = wsClient.request(HermesMethods.SESSION_LIST, buildJsonObject {
            put("limit", limit)
            put("offset", offset)
        })
        val sessionsArray = result.jsonObject["sessions"]?.jsonArray
        val sessions = sessionsArray?.mapNotNull { item ->
            if (item !is JsonObject) return@mapNotNull null
            Session(
                id = item["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                title = item["title"]?.jsonPrimitive?.content ?: "",
                preview = item["preview"]?.jsonPrimitive?.content,
                messageCount = item["message_count"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: item["messageCount"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: 0,
                lastActiveAt = item["last_active"]?.jsonPrimitive?.content
                    ?: item["lastActiveAt"]?.jsonPrimitive?.content
                    ?: item["started_at"]?.jsonPrimitive?.content
            )
        } ?: emptyList()
        val total = result.jsonObject["total"]?.jsonPrimitive?.content?.toIntOrNull() ?: sessions.size
        val hasMore = sessions.size >= limit
        return SessionsPageResult(
            sessions = sessions,
            total = total,
            hasMore = hasMore
        )
    }

    suspend fun fetchMessages(sessionId: String): List<ChatMessage> {
        val result = wsClient.request(HermesMethods.SESSION_HISTORY, buildJsonObject {
            put("session_id", sessionId)
        })
        val messagesArray = result.jsonObject["messages"]?.jsonArray ?: return emptyList()
        val raw = messagesArray.mapNotNull { it as? JsonObject }

        // 收集 tool 角色的结果（按 tool_call_id 索引，用于原始 OpenAI 格式）
        val toolResults = raw.filter {
            it["role"]?.jsonPrimitive?.content == "tool"
        }.associate { obj ->
            val resultText = obj["text"]?.jsonPrimitive?.content ?: ""
            val toolCallId = obj["tool_call_id"]?.jsonPrimitive?.content
                ?: obj["id"]?.jsonPrimitive?.content ?: ""
            toolCallId to resultText
        }

        // 收集简化格式的 tool 消息（按 nonToolRaw 索引分组，tool 关联到前一个非 tool 消息）
        val simplifiedToolCallGroups = mutableMapOf<Int, MutableList<ToolCallDetail>>()
        val nonToolRaw = mutableListOf<JsonObject>()
        for (obj in raw) {
            val isTool = obj["role"]?.jsonPrimitive?.content == "tool"
            if (isTool) {
                val name = obj["name"]?.jsonPrimitive?.content
                if (!name.isNullOrBlank()) {
                    val targetGroupIdx = nonToolRaw.size - 1
                    if (targetGroupIdx >= 0) {
                        val toolCall = ToolCallDetail(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            status = ToolCallStatus.Success,
                            args = obj["context"]?.jsonPrimitive?.content ?: "",
                            result = obj["text"]?.jsonPrimitive?.content
                                ?: obj["context"]?.jsonPrimitive?.content ?: ""
                        )
                        simplifiedToolCallGroups.getOrPut(targetGroupIdx) { mutableListOf() }.add(toolCall)
                    }
                }
            } else {
                nonToolRaw.add(obj)
            }
        }

        val parsed = nonToolRaw.mapIndexed { index, obj ->
            val content = obj["text"]?.jsonPrimitive?.content ?: ""
            val reasoningText = obj["reasoning"]?.jsonPrimitive?.content
                ?: obj["reasoning_content"]?.jsonPrimitive?.content ?: ""
            val role = obj["role"]?.jsonPrimitive?.content ?: "assistant"
            val (displayContent, imageUrls) = parseImageContent(content)
            val createdAt = System.currentTimeMillis() - (nonToolRaw.size - index) * 1000

            // 解析 toolCalls 并尝试关联 tool 结果
            val parsedToolCalls = parseToolCalls(obj["tool_calls"]).map { call ->
                val result = toolResults[call.id]
                if (result != null && call.result.isBlank()) {
                    call.copy(result = result)
                } else {
                    call
                }
            }
            val effectiveToolCalls = if (role == "assistant" && parsedToolCalls.isEmpty()) {
                simplifiedToolCallGroups[index].orEmpty()
            } else {
                parsedToolCalls
            }

            if (imageUrls.size > 1 && role == "user") {
                // 单条消息包含多张图片时拆分为多条，确保每条只带一张图
                imageUrls.mapIndexed { imgIndex, url ->
                    ChatMessage(
                        id = "${index}_img_$imgIndex",
                        role = role,
                        content = if (imgIndex == 0) displayContent else "",
                        isStreaming = false,
                        reasoningText = reasoningText,
                        isReasoning = false,
                        toolCalls = if (imgIndex == 0) effectiveToolCalls else emptyList(),
                        imageUrl = url,
                        createdAt = createdAt + imgIndex
                    )
                }
            } else {
                listOf(
                    ChatMessage(
                        id = index.toString(),
                        role = role,
                        content = displayContent,
                        isStreaming = false,
                        reasoningText = reasoningText,
                        isReasoning = false,
                        toolCalls = effectiveToolCalls,
                        imageUrl = imageUrls.firstOrNull(),
                        createdAt = createdAt
                    )
                )
            }
        }.flatten()

        // 合并相邻的 assistant 消息（服务端可能将 reasoning/toolCall/content 拆成多条）
        val merged = mutableListOf<ChatMessage>()
        for (msg in parsed) {
            if (msg.role == "assistant" && merged.isNotEmpty() && merged.last().role == "assistant") {
                val last = merged.last()
                merged[merged.size - 1] = last.copy(
                    content = when {
                        msg.content.isNotBlank() && last.content.isNotBlank() -> last.content + "\n\n" + msg.content
                        msg.content.isNotBlank() -> msg.content
                        else -> last.content
                    },
                    reasoningText = when {
                        last.reasoningText.isNotBlank() && msg.reasoningText.isNotBlank() ->
                            last.reasoningText + "\n\n" + msg.reasoningText
                        msg.reasoningText.isNotBlank() -> msg.reasoningText
                        else -> last.reasoningText
                    },
                    toolCalls = last.toolCalls + msg.toolCalls
                )
            } else {
                merged.add(msg)
            }
        }

        return merged
    }

    private fun parseToolCalls(toolCallsElement: JsonElement?): List<ToolCallDetail> {
        if (toolCallsElement == null) return emptyList()
        return when (toolCallsElement) {
            is JsonArray -> toolCallsElement.mapNotNull { parseSingleToolCall(it) }
            is JsonObject -> listOfNotNull(parseSingleToolCall(toolCallsElement))
            else -> emptyList()
        }
    }

    private fun parseSingleToolCall(element: JsonElement): ToolCallDetail? {
        return try {
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: ""

            // OpenAI 格式：function.name / function.arguments
            val function = obj["function"]?.jsonObject
            val name = function?.get("name")?.jsonPrimitive?.content
                ?: obj["toolName"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content ?: ""

            val args = function?.get("arguments")?.jsonPrimitive?.content
                ?: obj["input"]?.jsonPrimitive?.content
                ?: obj["args"]?.jsonPrimitive?.content ?: ""

            val result = obj["output"]?.jsonPrimitive?.content
                ?: obj["result"]?.jsonPrimitive?.content ?: ""
            val statusStr = obj["status"]?.jsonPrimitive?.content ?: ""
            val status = when (statusStr.lowercase()) {
                "success" -> ToolCallStatus.Success
                "failed", "error" -> ToolCallStatus.Failed
                else -> ToolCallStatus.Success
            }
            ToolCallDetail(id = id, name = name, args = args, result = result, status = status)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析消息内容中的图片残留文字，尝试从本地 Room 缓存查询对应的 content:// URI。
     * 服务端实际存储格式如：
     *   [You can examine it with vision_analyze using image_url: /path]
     *   [You can examine it with vision_analyze using image_urls: ["/path"]]
     *   [User sent an image at: /path]
     *   任何包含 .hermes/images/ 路径的文本
     * 有缓存则返回空内容 + imageUrl；无缓存则替换为 📷 图片 占位符。
     * 支持单条消息中包含多张图片，返回所有匹配的本地 URI 列表。
     */
    private suspend fun parseImageContent(content: String): Pair<String, List<String>> {
        if (content.isBlank()) return Pair(content, emptyList())

        val regex = Regex("""\[[^\]]*\.hermes/images/[^\]\s"]+[^\]]*\]|(?:image_url[s]?:\s*\[?"?|sent an image at:\s*)(/[^\]\s"]*\.hermes/images/[^\]\s"]+)""")
        val pathRegex = Regex("""(/[^\]\s"]*\.hermes/images/[^\]\s"]+)""")

        val localUris = mutableListOf<String>()
        var cleanedContent = content

        regex.findAll(content).forEach { match ->
            // 从匹配块中提取所有图片路径（支持 image_urls: ["/a", "/b"] 这种单块多路径）
            val paths = pathRegex.findAll(match.value).map { it.groupValues[1] }.toList()
            if (paths.isNotEmpty()) {
                var hasLocalUri = false
                paths.forEach { serverPath ->
                    val normalizedPath = serverPath.substring(serverPath.lastIndexOf(".hermes/images/"))
                    val localUri = sentImageDao.getUriByServerPath(normalizedPath)
                    if (localUri != null) {
                        localUris.add(localUri)
                        hasLocalUri = true
                    }
                }
                cleanedContent = if (hasLocalUri) {
                    cleanedContent.replace(match.value, "")
                } else {
                    cleanedContent.replace(match.value, "📷 图片")
                }
            } else {
                cleanedContent = cleanedContent.replace(match.value, "📷 图片")
            }
        }

        val trimmed = cleanedContent.trim()
        // 如果清理后只剩无意义的标点/空白符号，直接置空
        val meaningful = if (localUris.isNotEmpty() && trimmed.matches(Regex("""^[\p{P}\s]+$"""))) "" else trimmed
        return Pair(meaningful, localUris)
    }

    suspend fun createSession(): CreateSessionResult {
        val result = wsClient.request(HermesMethods.SESSION_CREATE)
        val sid = when {
            result is JsonObject && result.containsKey("session_id") -> result["session_id"]!!.jsonPrimitive.content
            result is JsonObject && result.containsKey("id") -> result["id"]!!.jsonPrimitive.content
            result is JsonObject && result.containsKey("sessionId") -> result["sessionId"]!!.jsonPrimitive.content
            else -> result.toString()
        }
        val storedSessionId = if (result is JsonObject && result.containsKey("stored_session_id")) {
            result["stored_session_id"]!!.jsonPrimitive.content
        } else {
            sid
        }
        return CreateSessionResult(sid = sid, storedSessionId = storedSessionId)
    }

    suspend fun resumeSession(sessionId: String): Pair<String, List<ChatMessage>> {
        val params = buildJsonObject {
            put("session_id", sessionId)
        }
        val result = wsClient.request(HermesMethods.SESSION_RESUME, params)
        val sid = when {
            result is JsonObject && result.containsKey("session_id") -> result["session_id"]!!.jsonPrimitive.content
            else -> result.toString()
        }
        // 解析 RPC 返回的消息列表（格式: [{"role":"user","text":"..."}, ...]）
        val rpcMessages = mutableListOf<ChatMessage>()
        if (result is JsonObject) {
            val messagesArray = result["messages"]?.jsonArray
            if (messagesArray != null) {
                var idx = 0L
                for (item in messagesArray) {
                    if (item !is JsonObject) continue
                    val role = item["role"]?.jsonPrimitive?.content ?: continue
                    if (role !in setOf("user", "assistant")) continue
                    val text = item["text"]?.jsonPrimitive?.content ?: ""
                    val reasoning = item["reasoning"]?.jsonPrimitive?.content
                        ?: item["reasoning_content"]?.jsonPrimitive?.content ?: ""
                    rpcMessages.add(
                        ChatMessage(
                            id = "rpc_${idx++}",
                            role = role,
                            content = text,
                            reasoningText = reasoning,
                            isStreaming = false
                        )
                    )
                }
            }
        }
        return Pair(sid, rpcMessages)
    }

    suspend fun deleteSession(sessionId: String) {
        FileLogger.i("SessionRepository", "deleteSession() sessionId=$sessionId")
        wsClient.request(HermesMethods.SESSION_DELETE, buildJsonObject {
            put("session_id", sessionId)
        })
        FileLogger.i("SessionRepository", "deleteSession() succeeded")
    }

    suspend fun renameSession(sessionId: String, title: String) {
        FileLogger.i("SessionRepository", "renameSession() sessionId=$sessionId, title=$title")
        wsClient.request(HermesMethods.SESSION_TITLE, buildJsonObject {
            put("session_id", sessionId)
            put("title", title)
        })
        FileLogger.i("SessionRepository", "renameSession() succeeded")
    }

    suspend fun undoSession() {
        FileLogger.i("SessionRepository", "undoSession()")
        wsClient.request(HermesMethods.SESSION_UNDO)
        FileLogger.i("SessionRepository", "undoSession() succeeded")
    }

    suspend fun compressSession() {
        FileLogger.i("SessionRepository", "compressSession()")
        wsClient.request(HermesMethods.SESSION_COMPRESS)
        FileLogger.i("SessionRepository", "compressSession() succeeded")
    }

    suspend fun branchFromMessage(messageId: String): String? {
        FileLogger.i("SessionRepository", "branchFromMessage() messageId=$messageId")
        val params = buildJsonObject {
            put("message_id", messageId)
        }
        val result = wsClient.request(HermesMethods.SESSION_BRANCH, params)
        val newSessionId = when {
            result is JsonObject && result.containsKey("session_id") -> result["session_id"]!!.jsonPrimitive.content
            result is JsonObject && result.containsKey("id") -> result["id"]!!.jsonPrimitive.content
            result is JsonObject && result.containsKey("sessionId") -> result["sessionId"]!!.jsonPrimitive.content
            else -> null
        }
        FileLogger.i("SessionRepository", "branchFromMessage() result=$newSessionId")
        return newSessionId
    }

}
