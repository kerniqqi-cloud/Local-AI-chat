package com.localai.chat.ui.thinking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localai.chat.data.preferences.ThinkingDisplayMode
import com.localai.chat.domain.thinking.AssistantSegment
import com.localai.chat.domain.thinking.ThinkingBlockParser
import com.localai.chat.ui.markdown.MarkdownRenderer

@Composable
fun AssistantMessageRenderer(
    messageId: String,
    content: String,
    thinkingDisplayMode: ThinkingDisplayMode,
    fontSizePx: Int,
    modifier: Modifier = Modifier,
) {
    val segments = remember(content) { ThinkingBlockParser.parse(content) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is AssistantSegment.Answer -> MarkdownRenderer(
                    markdown = segment.text,
                    fontSizePx = fontSizePx,
                )
                is AssistantSegment.Thinking -> ThinkingBlock(
                    text = segment.text,
                    messageId = messageId,
                    index = index,
                    displayMode = thinkingDisplayMode,
                    fontSizePx = fontSizePx,
                )
            }
        }
    }
}

@Composable
private fun ThinkingBlock(
    text: String,
    messageId: String,
    index: Int,
    displayMode: ThinkingDisplayMode,
    fontSizePx: Int,
) {
    if (displayMode == ThinkingDisplayMode.Hidden) return

    var expanded by remember(messageId, index, displayMode) {
        mutableStateOf(displayMode == ThinkingDisplayMode.Expanded)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(text = if (expanded) "Hide thinking" else "Show thinking")
            }
            if (expanded) {
                Text(
                    text = text.ifBlank { "No thinking content." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = fontSizePx.coerceIn(12, 32).sp,
                        fontStyle = FontStyle.Italic,
                    ),
                )
            }
        }
    }
}
