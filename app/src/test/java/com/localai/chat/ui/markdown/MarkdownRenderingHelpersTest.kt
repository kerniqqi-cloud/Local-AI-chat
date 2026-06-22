package com.localai.chat.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRenderingHelpersTest {
    @Test
    fun inlineMarkdownHtmlPreservesBoldAroundMath() {
        val html = buildInlineMarkdownHtml("**Input:** \$A + B = C\$")

        assertTrue(html.contains("<strong>Input:</strong>"))
        assertTrue(html.contains("\$A + B = C\$"))
    }

    @Test
    fun inlineMarkdownHtmlDoesNotRenderMathInsideCode() {
        val html = buildInlineMarkdownHtml("Use `\$A + B\$` literally")

        assertTrue(html.contains("<code>\$A + B\$</code>"))
        assertFalse(html.contains("<code><strong>"))
    }

    @Test
    fun normalizeTableRowsPadsMissingCellsToHeaderCount() {
        val rows = normalizeTableRows(
            listOf(
                listOf("Name", "Status", "Notes"),
                listOf("API", "Done"),
            ),
        )

        assertEquals(listOf("API", "Done", ""), rows[1])
    }

    @Test
    fun normalizeTableRowsMergesExtraCellsIntoLastColumn() {
        val rows = normalizeTableRows(
            listOf(
                listOf("Name", "Status", "Notes"),
                listOf("API", "Done", "First", "Second"),
            ),
        )

        assertEquals(listOf("API", "Done", "First | Second"), rows[1])
    }
}
