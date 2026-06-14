package com.seina.chan.ui.screens.chat

import android.net.Uri
import com.seina.chan.data.model.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val currentInput: String = "",
    val canSend: Boolean = true,
    val error: String? = null,
    val selectedImages: List<Uri> = emptyList(),
    val selectedVideo: Uri? = null,
    val selectedFiles: List<Uri> = emptyList(),
    val showToolCalls: Boolean = true,
    val showReasoning: Boolean = true,
    val showTimestamps: Boolean = false,
    val hiddenToolNames: Set<String> = emptySet(),
    val quotedMessage: ChatMessage? = null,
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val searchFilterUserOnly: Boolean = false,
    val statusText: String = ""
)
