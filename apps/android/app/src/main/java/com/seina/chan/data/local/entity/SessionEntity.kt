package com.seina.chan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.seina.chan.data.model.Session

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val title: String?,
    val preview: String?,
    val messageCount: Int = 0,
    val lastActiveAt: String? = null
) {
    fun toSession(): Session = Session(
        id = id,
        title = title,
        preview = preview,
        messageCount = messageCount,
        lastActiveAt = lastActiveAt
    )
}

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title,
    preview = preview,
    messageCount = messageCount,
    lastActiveAt = lastActiveAt
)
