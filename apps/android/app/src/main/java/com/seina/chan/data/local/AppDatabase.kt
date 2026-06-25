package com.seina.chan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.seina.chan.data.local.dao.MessageDao
import com.seina.chan.data.local.dao.SentImageDao
import com.seina.chan.data.local.dao.SessionDao
import com.seina.chan.data.local.entity.MessageEntity
import com.seina.chan.data.local.entity.SentImageEntity
import com.seina.chan.data.local.entity.SessionEntity

@Database(entities = [SentImageEntity::class, MessageEntity::class, SessionEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sentImageDao(): SentImageDao
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id TEXT PRIMARY KEY NOT NULL,
                        sessionId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        reasoningText TEXT NOT NULL DEFAULT '',
                        isReasoning INTEGER NOT NULL DEFAULT 0,
                        imageUrl TEXT,
                        toolCallsJson TEXT NOT NULL DEFAULT '[]',
                        systemEventsJson TEXT NOT NULL DEFAULT '[]',
                        isStreaming INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN parentId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sessions` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT,
                        `preview` TEXT,
                        `messageCount` INTEGER NOT NULL DEFAULT 0,
                        `lastActiveAt` TEXT
                    )
                """)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_sessionId ON messages(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_createdAt ON messages(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_lastActiveAt ON sessions(lastActiveAt)")
            }
        }
    }
}

