package com.seina.chan.ui.screens.chat

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import com.seina.chan.data.model.ChatMessage

@Immutable
data class SubagentDetail(
    val id: String,
    val goal: String,
    val taskIndex: Int = 0,
    val taskCount: Int = 0,
    val model: String? = null,
    val thinking: String = "",
    val progressMessages: List<String> = emptyList(),
    val toolCalls: List<String> = emptyList(),
    val status: String? = null,
    val summary: String = "",
    val durationSeconds: Double? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
)

@Immutable
data class TerminalRequest(
    val requestId: String,
    val prompt: String?,
    val start: Int?,
    val count: Int?
)


@Stable
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val currentInput: String = "",
    val canSend: Boolean = true,
    val error: String? = null,
    val selectedImages: List<Uri> = emptyList(),
    val selectedFiles: List<Uri> = emptyList(),
    val showToolCalls: Boolean = true,
    val showReasoning: Boolean = true,
    val showTimestamps: Boolean = false,
    val hiddenToolNames: Set<String> = emptySet(),
    val quotedMessage: ChatMessage? = null,
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val searchFilterUserOnly: Boolean = false,
    val statusText: String = "",
    val subagents: Map<String, SubagentDetail> = emptyMap(),
    val terminalRequest: TerminalRequest? = null
)
