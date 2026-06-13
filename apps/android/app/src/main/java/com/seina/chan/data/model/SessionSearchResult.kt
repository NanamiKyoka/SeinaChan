package com.seina.chan.data.model

data class SessionSearchResult(
    val sessionId: String,
    val title: String?,
    val previewSnippet: String?,
    val matchedKeyword: String?
)
