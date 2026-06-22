package com.localai.chat.data.repository

import kotlinx.serialization.Serializable

@Serializable
data class ImageAttachment(
    val id: String,
    val localPath: String,
    val mimeType: String,
    val displayName: String,
)
