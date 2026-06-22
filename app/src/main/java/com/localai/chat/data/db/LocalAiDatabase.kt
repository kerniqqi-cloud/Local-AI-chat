package com.localai.chat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.localai.chat.data.db.dao.ChatDao
import com.localai.chat.data.db.dao.MessageDao
import com.localai.chat.data.db.entities.ChatEntity
import com.localai.chat.data.db.entities.GenerationEntity
import com.localai.chat.data.db.entities.MessageEntity
import com.localai.chat.data.db.entities.MessageStatsEntity
import com.localai.chat.data.db.entities.ModelEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        ModelEntity::class,
        GenerationEntity::class,
        MessageStatsEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class LocalAiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun messageDao(): MessageDao
}
