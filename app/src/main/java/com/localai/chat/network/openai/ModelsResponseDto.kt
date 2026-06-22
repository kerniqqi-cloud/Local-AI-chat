package com.localai.chat.network.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelsResponseDto(
    @SerialName("data") val data: List<ModelDto> = emptyList(),
)

@Serializable
data class ModelDto(
    @SerialName("id") val id: String = "",
)
