package com.seina.chan.data.remote

object HermesMethods {
    const val SESSION_CREATE = "session.create"
    const val SESSION_RESUME = "session.resume"
    const val SESSION_INTERRUPT = "session.interrupt"
    const val SESSION_UNDO = "session.undo"
    const val SESSION_COMPRESS = "session.compress"
    const val SESSION_BRANCH = "session.branch"
    const val PROMPT_SUBMIT = "prompt.submit"
    const val IMAGE_ATTACH_BYTES = "image.attach_bytes"
    const val APPROVAL_RESPOND = "approval.respond"
    const val CLARIFY_RESPOND = "clarify.respond"
    const val SECRET_RESPOND = "secret.respond"
    const val COMMANDS_CATALOG = "commands.catalog"
}

object HermesEventTypes {
    const val GATEWAY_READY = "gateway.ready"
    const val SESSION_INFO = "session.info"
    const val MESSAGE_START = "message.start"
    const val MESSAGE_DELTA = "message.delta"
    const val MESSAGE_COMPLETE = "message.complete"
    const val REASONING_DELTA = "reasoning.delta"
    const val THINKING_DELTA = "thinking.delta"
    const val REASONING_AVAILABLE = "reasoning.available"
    const val TOOL_GENERATING = "tool.generating"
    const val TOOL_START = "tool.start"
    const val TOOL_PROGRESS = "tool.progress"
    const val TOOL_COMPLETE = "tool.complete"
    const val APPROVAL_REQUEST = "approval.request"
    const val CLARIFY_REQUEST = "clarify.request"
    const val SECRET_REQUEST = "secret.request"
    const val REVIEW_SUMMARY = "review.summary"
    const val ERROR = "error"
}
