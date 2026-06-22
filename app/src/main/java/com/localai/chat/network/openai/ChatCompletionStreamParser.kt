package com.localai.chat.network.openai

import javax.inject.Inject
import kotlinx.serialization.json.Json

class ChatCompletionStreamParser @Inject constructor(
    private val json: Json,
) {
    fun parseData(data: String): ChatStreamEvent {
        val trimmed = data.trim()
        if (trimmed == "[DONE]") return ChatStreamEvent.Done
        if (trimmed.isEmpty()) return ChatStreamEvent.Empty

        val chunk = json.decodeFromString<ChatCompletionStreamChunkDto>(trimmed)
        val content = chunk.choices.firstNotNullOfOrNull { choice ->
            choice.delta?.content?.takeIf(String::isNotEmpty)
                ?: choice.message?.content?.takeIf(String::isNotEmpty)
                ?: choice.text?.takeIf(String::isNotEmpty)
        }
        val reasoning = chunk.choices.firstNotNullOfOrNull { choice ->
            choice.delta?.reasoningContent?.takeIf(String::isNotEmpty)
                ?: choice.delta?.reasoning?.takeIf(String::isNotEmpty)
                ?: choice.message?.reasoningContent?.takeIf(String::isNotEmpty)
                ?: choice.message?.reasoning?.takeIf(String::isNotEmpty)
        }

        return when {
            content != null -> ChatStreamEvent.ContentDelta(content, reasoning)
            reasoning != null -> ChatStreamEvent.ReasoningDelta(reasoning)
            chunk.usage != null -> ChatStreamEvent.Usage(chunk.usage)
            chunk.choices.any { it.finishReason != null } -> ChatStreamEvent.Done
            else -> ChatStreamEvent.Empty
        }
    }
}

sealed interface ChatStreamEvent {
    data class ContentDelta(
        val content: String,
        val reasoningContent: String? = null,
    ) : ChatStreamEvent

    data class ReasoningDelta(val content: String) : ChatStreamEvent

    data class Usage(val usage: ChatCompletionUsageDto) : ChatStreamEvent
    data object Done : ChatStreamEvent
    data object Empty : ChatStreamEvent
}
