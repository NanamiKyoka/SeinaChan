package com.seina.chan.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index("sessionId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val reasoningText: String = "",
    val isReasoning: Boolean = false,
    val imageUrl: String? = null,
    val toolCallsJson: String = "[]",
    val systemEventsJson: String = "[]",
    val isStreaming: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val parentId: String? = null
)
