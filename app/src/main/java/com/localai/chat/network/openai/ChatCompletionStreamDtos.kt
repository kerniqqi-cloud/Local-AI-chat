package com.localai.chat.network.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionStreamChunkDto(
    @SerialName("choices") val choices: List<ChatCompletionStreamChoiceDto> = emptyList(),
    @SerialName("usage") val usage: ChatCompletionUsageDto? = null,
)

@Serializable
data class ChatCompletionStreamChoiceDto(
    @SerialName("delta") val delta: ChatCompletionStreamDeltaDto? = null,
    @SerialName("message") val message: ChatCompletionMessageDto? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatCompletionStreamDeltaDto(
    @SerialName("content") val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("reasoning") val reasoning: String? = null,
)
