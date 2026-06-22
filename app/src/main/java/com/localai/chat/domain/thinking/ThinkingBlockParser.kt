package com.localai.chat.domain.thinking

object ThinkingBlockParser {
    fun parse(raw: String): List<AssistantSegment> {
        if (raw.isEmpty()) return emptyList()
        parseReasoningResponse(raw)?.let { return it }

        val segments = mutableListOf<AssistantSegment>()
        var index = 0

        fun addAnswer(text: String) {
            if (text.isNotEmpty()) segments += AssistantSegment.Answer(text)
        }

        fun addThinking(text: String) {
            segments += AssistantSegment.Thinking(text)
        }

        while (index < raw.length) {
            val openIndex = raw.indexOf(OpenTagStart, startIndex = index, ignoreCase = true)
            if (openIndex < 0) {
                addAnswer(raw.substring(index))
                break
            }

            addAnswer(raw.substring(index, openIndex))

            val openEnd = raw.indexOf('>', startIndex = openIndex)
            if (openEnd < 0) {
                addAnswer(raw.substring(openIndex))
                break
            }

            val openingTag = raw.substring(openIndex, openEnd + 1)
            if (openingTag.equals(SelfClosingThinkTag, ignoreCase = true)) {
                addThinking("")
                index = openEnd + 1
                continue
            }

            val closeIndex = raw.indexOf(CloseTag, startIndex = openEnd + 1, ignoreCase = true)
            if (closeIndex < 0) {
                addThinking(raw.substring(openEnd + 1))
                break
            }

            addThinking(raw.substring(openEnd + 1, closeIndex))
            index = closeIndex + CloseTag.length
        }

        return segments.mergeAdjacentAnswers()
    }

    private fun parseReasoningResponse(raw: String): List<AssistantSegment>? {
        val lines = raw.lines()
        val reasoningIndex = lines.indexOfFirst { it.isLabel(ReasoningLabel) }
        if (reasoningIndex < 0) return null

        val responseIndex = lines
            .drop(reasoningIndex + 1)
            .indexOfFirst { it.isLabel(ResponseLabel) }
            .takeIf { it >= 0 }
            ?.let { it + reasoningIndex + 1 }
            ?: return null

        val before = lines.take(reasoningIndex).joinToString("\n")
        val reasoning = lines.subList(reasoningIndex + 1, responseIndex).joinToString("\n").trim('\n')
        val response = lines.drop(responseIndex + 1).joinToString("\n").trim('\n')

        return buildList {
            if (before.isNotBlank()) add(AssistantSegment.Answer(before.trim('\n')))
            add(AssistantSegment.Thinking(reasoning))
            if (response.isNotBlank()) add(AssistantSegment.Answer(response))
        }
    }

    private fun String.isLabel(label: String): Boolean {
        val normalized = trim()
            .trimStart('#')
            .trim()
            .trim('*')
            .trim()
            .removeSuffix(":")
            .trim()
        return normalized.equals(label, ignoreCase = true)
    }

    private fun List<AssistantSegment>.mergeAdjacentAnswers(): List<AssistantSegment> {
        val merged = mutableListOf<AssistantSegment>()
        forEach { segment ->
            val last = merged.lastOrNull()
            if (segment is AssistantSegment.Answer && last is AssistantSegment.Answer) {
                merged[merged.lastIndex] = AssistantSegment.Answer(last.text + segment.text)
            } else {
                merged += segment
            }
        }
        return merged
    }

    private const val OpenTagStart = "<think"
    private const val CloseTag = "</think>"
    private const val SelfClosingThinkTag = "<think/>"
    private const val ReasoningLabel = "Reasoning"
    private const val ResponseLabel = "Response"
}

sealed interface AssistantSegment {
    data class Answer(val text: String) : AssistantSegment
    data class Thinking(val text: String) : AssistantSegment
}
