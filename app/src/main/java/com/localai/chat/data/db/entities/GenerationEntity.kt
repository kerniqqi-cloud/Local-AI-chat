package com.localai.chat.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "generations",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["assistantMessageId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["assistantMessageId"]),
        Index(value = ["status"]),
    ],
)
data class GenerationEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val assistantMessageId: String?,
    val model: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val cancelledAt: Long? = null,
    val error: String? = null,
    val requestContextHash: String? = null,
    val approxPromptChars: Int? = null,
    val approxPromptTokens: Int? = null,
)
