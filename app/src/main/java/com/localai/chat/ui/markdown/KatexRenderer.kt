package com.localai.chat.ui.markdown

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.json.JSONObject

@Composable
fun KatexMathView(
    expression: String,
    displayMode: Boolean,
    fontSizePx: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurface.toCssColor()
    val minHeight = if (displayMode) 88.dp else 40.dp
    val maxHeight = if (compact) 360.dp else 900.dp
    var heightDp by remember(expression, displayMode, fontSizePx, compact) {
        mutableStateOf(minHeight)
    }
    val html = remember(expression, displayMode, fontSizePx, compact, textColor) {
        buildKatexHtml(
            text = expression,
            displayMode = displayMode,
            fontSizePx = fontSizePx,
            autoRender = false,
            compact = compact,
            textColor = textColor,
        )
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowContentAccess = false
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.blockNetworkLoads = true
                webViewClient = heightReportingClient { heightPx ->
                    heightDp = with(density) { heightPx.toDp() }.coerceIn(minHeight, maxHeight)
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(KatexBaseUrl, html, "text/html", "UTF-8", null)
        },
    )
}

@Composable
fun KatexTextView(
    text: String,
    fontSizePx: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurface.toCssColor()
    val minHeight = if (compact) 44.dp else 36.dp
    val maxHeight = if (compact) 360.dp else 320.dp
    var heightDp by remember(text, fontSizePx, compact) { mutableStateOf(minHeight) }
    val html = remember(text, fontSizePx, compact, textColor) {
        buildKatexHtml(
            text = text,
            displayMode = false,
            fontSizePx = fontSizePx,
            autoRender = true,
            compact = compact,
            textColor = textColor,
        )
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowContentAccess = false
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.blockNetworkLoads = true
                webViewClient = heightReportingClient { heightPx ->
                    heightDp = with(density) { heightPx.toDp() }.coerceIn(minHeight, maxHeight)
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(KatexBaseUrl, html, "text/html", "UTF-8", null)
        },
    )
}

private fun heightReportingClient(onHeightChanged: (Int) -> Unit): WebViewClient {
    return object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            listOf(0L, 120L, 350L, 700L).forEach { delayMs ->
                view.postDelayed({ reportHeight(view, onHeightChanged) }, delayMs)
            }
        }
    }
}

private fun reportHeight(
    view: WebView,
    onHeightChanged: (Int) -> Unit,
) {
    view.evaluateJavascript(
        """
        (function() {
          const content = document.getElementById('content');
          const rect = content ? content.getBoundingClientRect() : {height: 0, bottom: 0};
          return Math.ceil(Math.max(
            document.body.scrollHeight,
            document.documentElement.scrollHeight,
            rect.height + 12,
            rect.bottom + 8
          )).toString();
        })()
        """.trimIndent(),
    ) { value ->
        value.trim('"').toIntOrNull()?.takeIf { it > 0 }?.let(onHeightChanged)
    }
}

private fun buildKatexHtml(
    text: String,
    displayMode: Boolean,
    fontSizePx: Int,
    autoRender: Boolean,
    compact: Boolean,
    textColor: String,
): String {
    val normalizedText = normalizeMathDelimiters(text)
    val quotedText = JSONObject.quote(normalizedText)
    val quotedHtml = JSONObject.quote(buildInlineMarkdownHtml(normalizedText))
    val safeFontSize = if (compact) fontSizePx.coerceIn(11, 18) else fontSizePx.coerceIn(12, 32)
    val katexScale = if (compact) "0.86" else "1"
    val displayModeJs = if (displayMode) "true" else "false"
    val renderScript = if (autoRender) {
        """
        const content = document.getElementById('content');
        content.innerHTML = $quotedHtml;
        try {
          renderMathInElement(content, {
            delimiters: [
              {left: '$$', right: '$$', display: true},
              {left: '$', right: '$', display: false},
              {left: '\\(', right: '\\)', display: false},
              {left: '\\[', right: '\\]', display: true}
            ],
            throwOnError: false
          });
        } catch (error) {
          content.innerHTML = $quotedHtml;
          content.classList.add('fallback');
        }
        """.trimIndent()
    } else {
        """
        const content = document.getElementById('content');
        try {
          katex.render($quotedText, content, {
            displayMode: $displayModeJs,
            throwOnError: false
          });
        } catch (error) {
          content.textContent = $quotedText;
          content.classList.add('fallback');
        }
        """.trimIndent()
    }

    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link rel="stylesheet" href="katex.min.css">
          <style>
            html, body {
              margin: 0;
              padding: 0;
              background: transparent;
              color: $textColor;
              overflow-x: auto;
              overflow-y: visible;
              font-size: ${safeFontSize}px;
            }
            body { padding: 4px 0; }
            #content {
              display: block;
              line-height: 1.45;
              white-space: normal;
              overflow-wrap: anywhere;
            }
            .fallback {
              font-family: monospace;
              white-space: pre-wrap;
            }
            .katex {
              font-size: ${katexScale}em;
            }
            strong { font-weight: 700; }
            em { font-style: italic; }
            code {
              font-family: monospace;
              background: rgba(127, 127, 127, 0.16);
              border-radius: 4px;
              padding: 0 3px;
            }
            .katex-display {
              margin: 0;
              overflow-x: auto;
              overflow-y: visible;
              padding: 2px 0 6px;
            }
            .katex-display > .katex {
              max-width: 100%;
            }
          </style>
          <script src="katex.min.js"></script>
          <script src="auto-render.min.js"></script>
        </head>
        <body>
          <span id="content"></span>
          <script>$renderScript</script>
        </body>
        </html>
    """.trimIndent()
}

internal fun buildInlineMarkdownHtml(text: String): String {
    val output = StringBuilder()
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end > index) {
                    output.append("<code>")
                    output.append(escapeHtml(text.substring(index + 1, end)))
                    output.append("</code>")
                    index = end + 1
                } else {
                    output.append(escapeHtml(text[index].toString()))
                    index += 1
                }
            }
            text.startsWith("$$", index) -> {
                val end = text.indexOf("$$", startIndex = index + 2)
                if (end > index) {
                    output.append(escapeHtml(text.substring(index, end + 2)))
                    index = end + 2
                } else {
                    output.append(escapeHtml(text[index].toString()))
                    index += 1
                }
            }
            text.startsWith("$", index) -> {
                val end = text.indexOf('$', startIndex = index + 1)
                if (end > index) {
                    output.append(escapeHtml(text.substring(index, end + 1)))
                    index = end + 1
                } else {
                    output.append(escapeHtml(text[index].toString()))
                    index += 1
                }
            }
            text.startsWith("\\(", index) -> {
                val end = text.indexOf("\\)", startIndex = index + 2)
                if (end > index) {
                    output.append(escapeHtml(text.substring(index, end + 2)))
                    index = end + 2
                } else {
                    output.append(escapeHtml(text[index].toString()))
                    index += 1
                }
            }
            text.startsWith("\\[", index) -> {
                val end = text.indexOf("\\]", startIndex = index + 2)
                if (end > index) {
                    output.append(escapeHtml(text.substring(index, end + 2)))
                    index = end + 2
                } else {
                    output.append(escapeHtml(text[index].toString()))
                    index += 1
                }
            }
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end > index) {
                    output.append("<strong>")
                    output.append(escapeHtml(text.substring(index + 2, end)))
                    output.append("</strong>")
                    index = end + 2
                } else {
                    output.append(escapeHtml(text[index].toString()))
                    index += 1
                }
            }
            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", startIndex = index + 2)
                if (end > index) {
                    output.append("<s>")
                    output.append(escapeHtml(text.substring(index + 2, end)))
                    output.append("</s>")
                    index = end + 2
                } else {
                    output.append(escapeHtml(text[index].toString()))
                    index += 1
                }
            }
            text.startsWith("*", index) -> {
                val end = text.indexOf('*', startIndex = index + 1)
                if (end > index) {
                    output.append("<em>")
                    output.append(escapeHtml(text.substring(index + 1, end)))
                    output.append("</em>")
                    index = end + 1
                } else {
                    output.append(escapeHtml(text[index].toString()))
                    index += 1
                }
            }
            else -> {
                output.append(escapeHtml(text[index].toString()))
                index += 1
            }
        }
    }
    return output.toString()
}

private fun escapeHtml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

private fun normalizeMathDelimiters(text: String): String {
    var normalized = text.trim()
    if (normalized.startsWith("$$") && normalized.endsWith("$$") && normalized.length > 4) {
        normalized = normalized.removePrefix("$$").removeSuffix("$$").trim()
    }
    if (normalized.startsWith("\\[") && normalized.endsWith("\\]") && normalized.length > 4) {
        normalized = normalized.removePrefix("\\[").removeSuffix("\\]").trim()
    }
    if (normalized.startsWith("\\(") && normalized.endsWith("\\)") && normalized.length > 4) {
        normalized = normalized.removePrefix("\\(").removeSuffix("\\)").trim()
    }
    return normalized
}

private fun Color.toCssColor(): String {
    val red = (red * 255).roundToInt().coerceIn(0, 255)
    val green = (green * 255).roundToInt().coerceIn(0, 255)
    val blue = (blue * 255).roundToInt().coerceIn(0, 255)
    return "rgb($red, $green, $blue)"
}

private const val KatexBaseUrl = "file:///android_asset/katex/"
