package com.localai.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["lastMessageAt"]),
        Index(value = ["isPinned", "lastMessageAt"]),
        Index(value = ["isArchived", "isDeleted"]),
    ],
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String? = null,
    val model: String? = null,
    val systemPrompt: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val sortOrder: Long = 0,
)
