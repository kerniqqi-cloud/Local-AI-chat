package com.localai.chat.domain.thinking

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingBlockParserTest {
    @Test
    fun separatesThinkingFromAnswer() {
        val segments = ThinkingBlockParser.parse("<think>reason</think>answer")

        assertEquals(
            listOf(
                AssistantSegment.Thinking("reason"),
                AssistantSegment.Answer("answer"),
            ),
            segments,
        )
    }

    @Test
    fun handlesMissingClosingTagAsThinking() {
        val segments = ThinkingBlockParser.parse("Answer first <think>partial reason")

        assertEquals(
            listOf(
                AssistantSegment.Answer("Answer first "),
                AssistantSegment.Thinking("partial reason"),
            ),
            segments,
        )
    }

    @Test
    fun handlesRepeatedThinkingBlocks() {
        val segments = ThinkingBlockParser.parse("a<think>b</think>c<think>d</think>e")

        assertEquals(
            listOf(
                AssistantSegment.Answer("a"),
                AssistantSegment.Thinking("b"),
                AssistantSegment.Answer("c"),
                AssistantSegment.Thinking("d"),
                AssistantSegment.Answer("e"),
            ),
            segments,
        )
    }

    @Test
    fun preservesMalformedPartialOpeningTagAsAnswer() {
        val segments = ThinkingBlockParser.parse("hello <thi")

        assertEquals(listOf(AssistantSegment.Answer("hello <thi")), segments)
    }

    @Test
    fun separatesReasoningResponseFormat() {
        val segments = ThinkingBlockParser.parse(
            """
            Reasoning
            1. Analyze the user's input.
            2. Choose a friendly reply.
            Response
            Hey there! How can I help you today?
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                AssistantSegment.Thinking("1. Analyze the user's input.\n2. Choose a friendly reply."),
                AssistantSegment.Answer("Hey there! How can I help you today?"),
            ),
            segments,
        )
    }

    @Test
    fun separatesMarkdownReasoningResponseLabels() {
        val segments = ThinkingBlockParser.parse(
            """
            ### Reasoning:
            Hidden notes
            **Response:**
            Visible answer
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                AssistantSegment.Thinking("Hidden notes"),
                AssistantSegment.Answer("Visible answer"),
            ),
            segments,
        )
    }
}
