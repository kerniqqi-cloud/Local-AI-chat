package com.localai.chat.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownBlockParserTest {
    @Test
    fun parsesTextAndFencedCodeBlock() {
        val blocks = MarkdownBlockParser.parse(
            """
            Before
            ```kotlin
            val answer = 42
            ```
            After
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MarkdownBlock.Text("Before"),
                MarkdownBlock.Code(language = "kotlin", code = "val answer = 42"),
                MarkdownBlock.Text("After"),
            ),
            blocks,
        )
    }

    @Test
    fun treatsIncompleteFenceAsCodeBlockForStreaming() {
        val blocks = MarkdownBlockParser.parse(
            """
            Text
            ```python
            print("still streaming")
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MarkdownBlock.Text("Text"),
                MarkdownBlock.Code(language = "python", code = "print(\"still streaming\")"),
            ),
            blocks,
        )
    }
}
