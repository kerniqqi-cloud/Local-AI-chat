package com.localai.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: String,
    val lastSeenAt: Long,
    val isAvailable: Boolean = true,
    val metadataJson: String? = null,
)
