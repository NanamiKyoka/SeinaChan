package com.seina.chan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.seina.chan.data.local.entity.SessionEntity

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY lastActiveAt DESC")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Transaction
    suspend fun replaceAll(sessions: List<SessionEntity>) {
        clearAll()
        upsertSessions(sessions)
    }
}
