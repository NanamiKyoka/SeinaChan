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
            // 以下事件类型仅记录，不进行业务处理
            HermesEventTypes.NOTIFICATION_SHOW,
            HermesEventTypes.NOTIFICATION_CLEAR,
            HermesEventTypes.BACKGROUND_COMPLETE,
            HermesEventTypes.SKIN_CHANGED,
            HermesEventTypes.SUBAGENT_START,
            HermesEventTypes.SUBAGENT_THINKING,
            HermesEventTypes.SUBAGENT_PROGRESS,
            HermesEventTypes.SUBAGENT_TOOL,
            HermesEventTypes.SUBAGENT_COMPLETE,
            HermesEventTypes.VOICE_TRANSCRIPT,
            HermesEventTypes.VOICE_STATUS,
            HermesEventTypes.BROWSER_PROGRESS,
            HermesEventTypes.TERMINAL_READ_REQUEST -> GatewayEvent.UnhandledEvent.serializer()
            else -> {
                FileLogger.w("GatewayEventSerializer", "未知事件类型: $eventType")
                GatewayEvent.UnknownEvent.serializer()
            }
        }
    }
}
