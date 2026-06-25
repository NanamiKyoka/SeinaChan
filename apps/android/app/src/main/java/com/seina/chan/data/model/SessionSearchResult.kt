package com.seina.chan.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SessionSearchResult(
    val sessionId: String,
    val title: String?,
    val previewSnippet: String?,
    val matchedKeyword: String?
)
