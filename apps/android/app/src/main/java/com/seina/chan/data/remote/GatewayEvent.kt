package com.seina.chan.data.remote

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import com.seina.chan.util.FileLogger
import kotlinx.serialization.json.jsonPrimitive

@Serializable(GatewayEventSerializer::class)
sealed class GatewayEvent {
    @Serializable
    @SerialName(HermesEventTypes.GATEWAY_READY)
    data object GatewayReady : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.SESSION_INFO)
    data class SessionInfo(
        @SerialName("session_id") val sessionId: String? = null,
        val id: String? = null,
        val title: String? = null
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.MESSAGE_START)
    data class MessageStart(
        val id: String = "",
        @SerialName("parent_id") val parentId: String? = null,
        val role: String = "assistant"
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.MESSAGE_DELTA)
    data class MessageDelta(
        val id: String = "",
        @SerialName("text") val delta: String
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.MESSAGE_COMPLETE)
    data class MessageComplete(
        val id: String = "",
        val reasoning: String = "",
        val text: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.REASONING_DELTA)
    data class ReasoningDelta(
        val text: String
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.THINKING_DELTA)
    data class ThinkingDelta(
        val text: String
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.REASONING_AVAILABLE)
    data class ReasoningAvailable(
        val text: String
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.TOOL_GENERATING)
    data class ToolGenerating(val name: String) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.TOOL_START)
    data class ToolStart(
        @SerialName("tool_id") val toolId: String,
        val name: String,
        @SerialName("args_text") val args: String = "",
        @SerialName("context") val context: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.TOOL_PROGRESS)
    data class ToolProgress(
        @SerialName("tool_id") val toolId: String,
        val text: String
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.TOOL_COMPLETE)
    data class ToolComplete(
        @SerialName("tool_id") val toolId: String,
        val name: String,
        val result: JsonElement? = null,
        @SerialName("duration_s") val duration: Float? = null,
        val summary: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.APPROVAL_REQUEST)
    data class ApprovalRequest(
        @SerialName("request_id") val id: String = "",
        val command: String = "",
        val description: String = "",
        @SerialName("allow_permanent") val allowPermanent: Boolean = false
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.CLARIFY_REQUEST)
    data class ClarifyRequest(
        @SerialName("request_id") val id: String = "",
        val question: String = "",
        val choices: List<String>? = null
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.SECRET_REQUEST)
    data class SecretRequest(
        @SerialName("request_id") val id: String = "",
        val prompt: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.SUDO_REQUEST)
    data class SudoRequest(
        @SerialName("request_id") val id: String = "",
        val prompt: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.REVIEW_SUMMARY)
    data class ReviewSummary(
        val text: String
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.ERROR)
    data class ErrorEvent(
        val message: String
    ) : GatewayEvent()


    // ──── P2 协议补全事件类型 ────

    @Serializable
    @SerialName(HermesEventTypes.SUBAGENT_START)
    data class SubagentStart(
        val id: String = "",
        val goal: String = "",
        @SerialName("task_index") val taskIndex: Int = 0,
        @SerialName("task_count") val taskCount: Int = 0,
        val model: String? = null
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.SUBAGENT_THINKING)
    data class SubagentThinking(
        val id: String = "",
        val thinking: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.SUBAGENT_PROGRESS)
    data class SubagentProgress(
        val id: String = "",
        val message: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.SUBAGENT_TOOL)
    data class SubagentTool(
        val id: String = "",
        @SerialName("tool_name") val toolName: String = "",
        @SerialName("tool_call_id") val toolCallId: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.SUBAGENT_COMPLETE)
    data class SubagentComplete(
        val id: String = "",
        val status: String = "",
        val summary: String = "",
        @SerialName("duration_seconds") val durationSeconds: Double? = null,
        @SerialName("input_tokens") val inputTokens: Int? = null,
        @SerialName("output_tokens") val outputTokens: Int? = null,
        @SerialName("cost_usd") val costUsd: Double? = null,
        @SerialName("files_read") val filesRead: Int? = null,
        @SerialName("files_written") val filesWritten: Int? = null
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.NOTIFICATION_SHOW)
    data class NotificationShow(
        val key: String = "",
        val text: String = "",
        val level: String? = null,
        val kind: String? = null,
        @SerialName("ttl_ms") val ttlMs: Int? = null
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.NOTIFICATION_CLEAR)
    data class NotificationClear(
        val key: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.TERMINAL_READ_REQUEST)
    data class TerminalReadRequest(
        @SerialName("request_id") val requestId: String = "",
        val prompt: String? = null,
        val start: Int? = null,
        val count: Int? = null
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.SKIN_CHANGED)
    data class SkinChanged(
        val name: String? = null,
        val colors: Map<String, String>? = null,
        val branding: Map<String, String>? = null
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.BACKGROUND_COMPLETE)
    data class BackgroundComplete(
        @SerialName("task_id") val taskId: String = "",
        val text: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.BROWSER_PROGRESS)
    data class BrowserProgress(
        @SerialName("tool_call_id") val toolCallId: String? = null,
        val message: String = "",
        val level: String? = null
    ) : GatewayEvent()

    @Serializable
    @SerialName("__unknown__")
    data class UnknownEvent(val type: String = "") : GatewayEvent()

    @Serializable
    @SerialName(HermesEventTypes.STATUS_UPDATE)
    data class StatusUpdate(
        val kind: String = "",
        val text: String = ""
    ) : GatewayEvent()

    @Serializable
    @SerialName("__unhandled__")
    data class UnhandledEvent(
        val eventType: String = "",
        val rawPayload: JsonElement? = null
    ) : GatewayEvent()
}

/**
 * 统一事件反序列化器，根据 JSON 中的 "type" 字段路由到具体的 GatewayEvent 子类。
 * 输入格式：{"type": "message.delta", "id": "...", "text": "..."}
 */
object GatewayEventSerializer : JsonContentPolymorphicSerializer<GatewayEvent>(GatewayEvent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<GatewayEvent> {
        val eventType = element.jsonObject["type"]?.jsonPrimitive?.content
        return when (eventType) {
            HermesEventTypes.GATEWAY_READY -> GatewayEvent.GatewayReady.serializer()
            HermesEventTypes.SESSION_INFO -> GatewayEvent.SessionInfo.serializer()
            HermesEventTypes.MESSAGE_START -> GatewayEvent.MessageStart.serializer()
            HermesEventTypes.MESSAGE_DELTA -> GatewayEvent.MessageDelta.serializer()
            HermesEventTypes.MESSAGE_COMPLETE -> GatewayEvent.MessageComplete.serializer()
            HermesEventTypes.REASONING_DELTA -> GatewayEvent.ReasoningDelta.serializer()
            HermesEventTypes.THINKING_DELTA -> GatewayEvent.ThinkingDelta.serializer()
            HermesEventTypes.REASONING_AVAILABLE -> GatewayEvent.ReasoningAvailable.serializer()
            HermesEventTypes.TOOL_GENERATING -> GatewayEvent.ToolGenerating.serializer()
            HermesEventTypes.TOOL_START -> GatewayEvent.ToolStart.serializer()
            HermesEventTypes.TOOL_PROGRESS -> GatewayEvent.ToolProgress.serializer()
            HermesEventTypes.TOOL_COMPLETE -> GatewayEvent.ToolComplete.serializer()
            HermesEventTypes.APPROVAL_REQUEST -> GatewayEvent.ApprovalRequest.serializer()
            HermesEventTypes.CLARIFY_REQUEST -> GatewayEvent.ClarifyRequest.serializer()
            HermesEventTypes.SECRET_REQUEST -> GatewayEvent.SecretRequest.serializer()
            HermesEventTypes.SUDO_REQUEST -> GatewayEvent.SudoRequest.serializer()
            HermesEventTypes.REVIEW_SUMMARY -> GatewayEvent.ReviewSummary.serializer()
            HermesEventTypes.ERROR -> GatewayEvent.ErrorEvent.serializer()
            HermesEventTypes.STATUS_UPDATE -> GatewayEvent.StatusUpdate.serializer()
            // ──── P2 协议补全：新事件路由到具体类型 ────
            HermesEventTypes.SUBAGENT_START -> GatewayEvent.SubagentStart.serializer()
            HermesEventTypes.SUBAGENT_THINKING -> GatewayEvent.SubagentThinking.serializer()
            HermesEventTypes.SUBAGENT_PROGRESS -> GatewayEvent.SubagentProgress.serializer()
            HermesEventTypes.SUBAGENT_TOOL -> GatewayEvent.SubagentTool.serializer()
            HermesEventTypes.SUBAGENT_COMPLETE -> GatewayEvent.SubagentComplete.serializer()
            HermesEventTypes.NOTIFICATION_SHOW -> GatewayEvent.NotificationShow.serializer()
            HermesEventTypes.NOTIFICATION_CLEAR -> GatewayEvent.NotificationClear.serializer()
            HermesEventTypes.TERMINAL_READ_REQUEST -> GatewayEvent.TerminalReadRequest.serializer()
            HermesEventTypes.SKIN_CHANGED -> GatewayEvent.SkinChanged.serializer()
            HermesEventTypes.BACKGROUND_COMPLETE -> GatewayEvent.BackgroundComplete.serializer()
            HermesEventTypes.BROWSER_PROGRESS -> GatewayEvent.BrowserProgress.serializer()
            // Voice 模式暂缓，仍映射为 UnhandledEvent
            HermesEventTypes.VOICE_TRANSCRIPT,
            HermesEventTypes.VOICE_STATUS -> GatewayEvent.UnhandledEvent.serializer()
            else -> {
                FileLogger.w("GatewayEventSerializer", "未知事件类型: $eventType")
                GatewayEvent.UnknownEvent.serializer()
            }
        }
    }
}
