package com.localai.chat.ui.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localai.chat.domain.markdown.MarkdownBlock
import com.localai.chat.domain.markdown.MarkdownBlockParser

@Composable
fun MarkdownRenderer(
    markdown: String,
    fontSizePx: Int,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { MarkdownBlockParser.parse(markdown) }

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Text -> MarkdownTextBlock(block.text, fontSizePx)
                    is MarkdownBlock.Code -> MarkdownCodeBlock(block, fontSizePx)
                }
            }
        }
    }
}

@Composable
private fun MarkdownTextBlock(
    text: String,
    fontSizePx: Int,
) {
    val lines = remember(text) { text.lines() }
    val baseSize = fontSizePx.coerceIn(12, 32)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> index += 1
                isTableStart(lines, index) -> {
                    val (rows, nextIndex) = parseTable(lines, index)
                    MarkdownTable(rows, baseSize)
                    index = nextIndex
                }
                trimmed.startsWith("$$") -> {
                    val (math, nextIndex) = parseBlockMath(lines, index)
                    MarkdownMathBlock(math, baseSize)
                    index = nextIndex
                }
                isHorizontalRule(trimmed) -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    index += 1
                }
                trimmed.startsWith("### ") -> MarkdownInlineText(
                    text = trimmed.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = (baseSize + 2).sp),
                ).also { index += 1 }
                trimmed.startsWith("## ") -> MarkdownInlineText(
                    text = trimmed.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = (baseSize + 4).sp),
                ).also { index += 1 }
                trimmed.startsWith("# ") -> MarkdownInlineText(
                    text = trimmed.removePrefix("# "),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = (baseSize + 6).sp),
                ).also { index += 1 }
                trimmed.startsWith("###### ") -> MarkdownInlineText(
                    text = trimmed.removePrefix("###### "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = baseSize.sp, fontWeight = FontWeight.Bold),
                ).also { index += 1 }
                trimmed.startsWith("##### ") -> MarkdownInlineText(
                    text = trimmed.removePrefix("##### "),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseSize.sp, fontWeight = FontWeight.Bold),
                ).also { index += 1 }
                trimmed.startsWith("#### ") -> MarkdownInlineText(
                    text = trimmed.removePrefix("#### "),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = (baseSize + 1).sp),
                ).also { index += 1 }
                trimmed.startsWith(">") -> Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    text = trimmed.removePrefix(">").trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = baseSize.sp, fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ).also { index += 1 }
                trimmed.matches(UnorderedListRegex) -> MarkdownInlineText(
                    text = "• ${trimmed.drop(2)}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseSize.sp),
                ).also { index += 1 }
                trimmed.matches(OrderedListRegex) -> MarkdownInlineText(
                    text = trimmed,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseSize.sp),
                ).also { index += 1 }
                else -> MarkdownInlineText(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseSize.sp),
                ).also { index += 1 }
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    compactMath: Boolean = false,
) {
    if (containsLikelyInlineMath(text)) {
        KatexTextView(
            modifier = modifier,
            text = text,
            fontSizePx = style.fontSize.value.takeIf { it.isFinite() }?.toInt() ?: 18,
            compact = compactMath,
        )
        return
    }

    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(text, linkColor, codeBackground) {
        buildInlineMarkdown(text, linkColor, codeBackground)
    }

    @Suppress("DEPRECATION")
    ClickableText(
        modifier = modifier,
        text = annotated,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = LinkTag, start = offset, end = offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        },
    )
}

@Composable
private fun MarkdownCodeBlock(
    block: MarkdownBlock.Code,
    fontSizePx: Int,
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = block.language ?: "code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(block.code)) },
                ) {
                    Text(text = "Copy")
                }
            }
            Text(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                text = block.code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizePx.coerceIn(12, 32).sp,
                ),
            )
        }
    }
}

@Composable
private fun MarkdownTable(
    rows: List<List<String>>,
    fontSizePx: Int,
) {
    val normalizedRows = remember(rows) { normalizeTableRows(rows) }
    if (normalizedRows.isEmpty()) return

    val scrollState = rememberScrollState()
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val columnCount = normalizedRows.first().size

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnWidth = tableColumnWidth(columnCount, maxWidth)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .border(1.dp, outline, RoundedCornerShape(10.dp)),
        ) {
            normalizedRows.forEachIndexed { rowIndex, row ->
                EqualHeightTableRow(
                    row = row,
                    rowIndex = rowIndex,
                    columnWidth = columnWidth,
                    outline = outline,
                    fontSizePx = fontSizePx,
                )
            }
        }
    }
}

@Composable
private fun EqualHeightTableRow(
    row: List<String>,
    rowIndex: Int,
    columnWidth: Dp,
    outline: androidx.compose.ui.graphics.Color,
    fontSizePx: Int,
) {
    val tableFontSize = fontSizePx.coerceAtMost(18)
    val background = if (rowIndex == 0) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
    }
    val style = if (rowIndex == 0) {
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = tableFontSize.sp,
            fontWeight = FontWeight.Bold,
        )
    } else {
        MaterialTheme.typography.bodyMedium.copy(fontSize = tableFontSize.sp)
    }

    Layout(
        content = {
            RowGridBackground(
                columnCount = row.size,
                columnWidth = columnWidth,
                background = background,
                outline = outline,
            )
            row.forEach { cell ->
                MarkdownInlineText(
                    modifier = Modifier
                        .width(columnWidth)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    text = cell,
                    style = style,
                    compactMath = true,
                )
            }
        },
    ) { measurables, constraints ->
        val columnWidthPx = columnWidth.roundToPx()
        val backgroundMeasurable = measurables.firstOrNull()
        val cellMeasurables = measurables.drop(1)
        val cellConstraints = constraints.copy(
            minWidth = columnWidthPx,
            maxWidth = columnWidthPx,
            minHeight = 0,
            maxHeight = constraints.maxHeight,
        )
        val cellPlaceables = cellMeasurables.map { measurable -> measurable.measure(cellConstraints) }
        val rowHeight = cellPlaceables.maxOfOrNull { it.height } ?: 0
        val rowWidth = columnWidthPx * cellMeasurables.size
        val backgroundPlaceable = backgroundMeasurable?.measure(
            constraints.copy(
                minWidth = rowWidth,
                maxWidth = rowWidth,
                minHeight = rowHeight,
                maxHeight = rowHeight,
            ),
        )
        layout(
            width = rowWidth,
            height = rowHeight,
        ) {
            backgroundPlaceable?.placeRelative(0, 0)
            var x = 0
            cellPlaceables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += columnWidthPx
            }
        }
    }
}

@Composable
private fun RowGridBackground(
    columnCount: Int,
    columnWidth: Dp,
    background: androidx.compose.ui.graphics.Color,
    outline: androidx.compose.ui.graphics.Color,
) {
    val strokeWidth = 0.5.dp
    Canvas(modifier = Modifier) {
        drawRect(color = background)
        val columnWidthPx = columnWidth.toPx()
        val strokeWidthPx = strokeWidth.toPx()
        for (columnIndex in 0..columnCount) {
            val x = columnIndex * columnWidthPx
            drawLine(
                color = outline,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidthPx,
            )
        }
        drawLine(
            color = outline,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = strokeWidthPx,
        )
        drawLine(
            color = outline,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = strokeWidthPx,
        )
    }
}

@Composable
private fun MarkdownMathBlock(
    math: String,
    fontSizePx: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        KatexMathView(
            modifier = Modifier
                .padding(12.dp),
            expression = math,
            displayMode = true,
            fontSizePx = fontSizePx,
        )
    }
}

private fun buildInlineMarkdown(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end > index) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index++])
                }
            }
            text.startsWith("$", index) && !text.startsWith("$$", index) -> {
                val end = text.indexOf('$', startIndex = index + 1)
                if (end > index) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index++])
                }
            }
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end > index) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(text[index++])
                }
            }
            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", startIndex = index + 2)
                if (end > index) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(text[index++])
                }
            }
            text.startsWith("*", index) -> {
                val end = text.indexOf('*', startIndex = index + 1)
                if (end > index) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index++])
                }
            }
            text.startsWith("[", index) -> {
                val labelEnd = text.indexOf(']', startIndex = index + 1)
                val urlStart = if (labelEnd >= 0) labelEnd + 1 else -1
                if (labelEnd > index && urlStart < text.length && text[urlStart] == '(') {
                    val urlEnd = text.indexOf(')', startIndex = urlStart + 1)
                    if (urlEnd > urlStart) {
                        val label = text.substring(index + 1, labelEnd)
                        val url = text.substring(urlStart + 1, urlEnd)
                        pushStringAnnotation(tag = LinkTag, annotation = url)
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        append(label)
                        pop()
                        pop()
                        index = urlEnd + 1
                    } else {
                        append(text[index++])
                    }
                } else {
                    append(text[index++])
                }
            }
            else -> append(text[index++])
        }
    }
}

private fun isHorizontalRule(trimmed: String): Boolean {
    return trimmed.length >= 3 && trimmed.all { it == '-' || it == '*' || it == '_' }
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    return lines[index].contains('|') && isTableSeparator(lines[index + 1])
}

private fun isTableSeparator(line: String): Boolean {
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { cell ->
        val trimmed = cell.trim()
        trimmed.length >= 3 && trimmed.all { it == '-' || it == ':' }
    }
}

private fun parseTable(lines: List<String>, startIndex: Int): Pair<List<List<String>>, Int> {
    val rows = mutableListOf<List<String>>()
    rows += splitTableRow(lines[startIndex])
    var index = startIndex + 2
    while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
        rows += splitTableRow(lines[index])
        index += 1
    }
    return rows to index
}

private fun splitTableRow(line: String): List<String> {
    return line.trim().trim('|').split('|').map { it.trim() }
}

private fun containsLikelyInlineMath(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val start = text.indexOf('$', index)
        if (start < 0 || start == text.lastIndex) return false
        if (text.getOrNull(start + 1) == '$') {
            index = start + 2
            continue
        }
        val end = text.indexOf('$', start + 1)
        if (end <= start + 1) return false
        val inner = text.substring(start + 1, end).trim()
        if (isLikelyMath(inner)) return true
        index = end + 1
    }
    return false
}

private fun isLikelyMath(value: String): Boolean {
    if (value.isBlank()) return false
    if (value.matches(PlainNumberRegex)) return false
    return true
}

internal fun normalizeTableRows(rows: List<List<String>>): List<List<String>> {
    val columnCount = rows.firstOrNull()?.size ?: return emptyList()
    if (columnCount == 0) return emptyList()
    return rows.map { row ->
        when {
            row.size == columnCount -> row
            row.size < columnCount -> List(columnCount) { index -> row.getOrNull(index).orEmpty() }
            else -> {
                val fixedCells = row.take(columnCount - 1).toMutableList()
                fixedCells += row.drop(columnCount - 1).joinToString(" | ").trim()
                fixedCells
            }
        }
    }
}

private fun tableColumnWidth(
    columnCount: Int,
    availableWidth: Dp,
): Dp {
    val fittedWidth = if (availableWidth.value.isFinite() && availableWidth > 0.dp) {
        availableWidth / columnCount.toFloat()
    } else {
        144.dp
    }

    return when {
        columnCount <= 1 -> fittedWidth.coerceIn(180.dp, 320.dp)
        columnCount == 2 -> fittedWidth.coerceIn(160.dp, 260.dp)
        columnCount == 3 -> fittedWidth.coerceIn(150.dp, 220.dp)
        else -> fittedWidth.coerceIn(128.dp, 190.dp)
    }
}

private fun parseBlockMath(lines: List<String>, startIndex: Int): Pair<String, Int> {
    val first = lines[startIndex].trim()
    val sameLine = first.removePrefix("$$")
    if (sameLine.endsWith("$$") && sameLine.length > 2) {
        return sameLine.removeSuffix("$$").trim() to startIndex + 1
    }

    val buffer = StringBuilder()
    val firstRemainder = first.removePrefix("$$")
    if (firstRemainder.isNotBlank()) buffer.appendLine(firstRemainder)
    var index = startIndex + 1
    while (index < lines.size) {
        val line = lines[index]
        if (line.trim().endsWith("$$")) {
            buffer.appendLine(line.replace("$$", "").trimEnd())
            return buffer.toString().trim() to index + 1
        }
        buffer.appendLine(line)
        index += 1
    }
    return buffer.toString().trim() to index
}

private val UnorderedListRegex = Regex("^[-*+]\\s+.+")
private val OrderedListRegex = Regex("^\\d+\\.\\s+.+")
private val PlainNumberRegex = Regex("^[0-9.,\\s]+$")
private const val LinkTag = "URL"
