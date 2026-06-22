package com.localai.chat.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId", "orderIndex"]),
        Index(value = ["chatId", "isDeleted"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val rawContent: String = content,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val parentMessageId: String? = null,
    val orderIndex: Long,
    val isDeleted: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val generationDurationMs: Long? = null,
    val attachmentsJson: String? = null,
)
