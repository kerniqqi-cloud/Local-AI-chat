package com.localai.chat.domain.markdown

object MarkdownBlockParser {
    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isEmpty()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val textBuffer = StringBuilder()
        val codeBuffer = StringBuilder()
        var inCodeBlock = false
        var language: String? = null

        fun flushText() {
            if (textBuffer.isEmpty()) return
            blocks += MarkdownBlock.Text(textBuffer.toString().trimEnd('\n'))
            textBuffer.clear()
        }

        fun flushCode() {
            blocks += MarkdownBlock.Code(
                language = language,
                code = codeBuffer.toString().trimEnd('\n'),
            )
            codeBuffer.clear()
            language = null
        }

        markdown.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith(CodeFence)) {
                if (inCodeBlock) {
                    flushCode()
                    inCodeBlock = false
                } else {
                    flushText()
                    inCodeBlock = true
                    language = trimmed.removePrefix(CodeFence).trim().takeIf(String::isNotEmpty)
                }
            } else if (inCodeBlock) {
                codeBuffer.appendLine(line)
            } else {
                textBuffer.appendLine(line)
            }
        }

        if (inCodeBlock) flushCode() else flushText()

        return blocks.filterNot {
            it is MarkdownBlock.Text && it.text.isBlank()
        }
    }

    private const val CodeFence = "```"
}

sealed interface MarkdownBlock {
    data class Text(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
}
