package com.localai.chat.network.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatCompletionRequestDto(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<ChatCompletionRequestMessageDto>,
    @SerialName("stream") val stream: Boolean = false,
)

@Serializable
data class ChatCompletionRequestMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: JsonElement,
)

@Serializable
data class ChatCompletionMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String = "",
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("reasoning") val reasoning: String? = null,
)

@Serializable
data class ChatCompletionResponseDto(
    @SerialName("choices") val choices: List<ChatCompletionChoiceDto> = emptyList(),
    @SerialName("usage") val usage: ChatCompletionUsageDto? = null,
)

@Serializable
data class ChatCompletionChoiceDto(
    @SerialName("message") val message: ChatCompletionMessageDto? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatCompletionUsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
)

data class ChatCompletionResult(
    val content: String,
    val rawContent: String,
    val usage: ChatCompletionUsageDto?,
)
