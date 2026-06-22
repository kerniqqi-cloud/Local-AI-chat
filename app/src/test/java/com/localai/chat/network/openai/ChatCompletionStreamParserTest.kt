package com.localai.chat.network.openai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class ChatCompletionStreamParserTest {
    private val parser = ChatCompletionStreamParser(
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
    )

    @Test
    fun parsesDoneSentinel() {
        assertEquals(ChatStreamEvent.Done, parser.parseData("[DONE]"))
    }

    @Test
    fun parsesDeltaContent() {
        val event = parser.parseData(
            """
            {"choices":[{"delta":{"content":"hello"}}]}
            """.trimIndent(),
        )

        assertEquals(ChatStreamEvent.ContentDelta("hello"), event)
    }

    @Test
    fun parsesMessageContentFallback() {
        val event = parser.parseData(
            """
            {"choices":[{"message":{"role":"assistant","content":"fallback"}}]}
            """.trimIndent(),
        )

        assertEquals(ChatStreamEvent.ContentDelta("fallback"), event)
    }

    @Test
    fun parsesTextFallback() {
        val event = parser.parseData(
            """
            {"choices":[{"text":"text fallback"}]}
            """.trimIndent(),
        )

        assertEquals(ChatStreamEvent.ContentDelta("text fallback"), event)
    }

    @Test
    fun parsesReasoningContentDelta() {
        val event = parser.parseData(
            """
            {"choices":[{"delta":{"reasoning_content":"hidden reasoning"}}]}
            """.trimIndent(),
        )

        assertEquals(ChatStreamEvent.ReasoningDelta("hidden reasoning"), event)
    }

    @Test
    fun parsesUsageChunk() {
        val event = parser.parseData(
            """
            {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14}}
            """.trimIndent(),
        )

        assertTrue(event is ChatStreamEvent.Usage)
    }
}
