package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

private const val URL_TAG = "URL"
private val inlineMarkdown = Regex("(\\[([^]]+)]\\((https?://[^)]+)\\))|(\\*\\*([^*]+)\\*\\*)|(`([^`]+)`)|(\\*([^*]+)\\*)")

internal fun parseInlineMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    inlineMarkdown.findAll(source).forEach { match ->
        append(source.substring(cursor, match.range.first))
        when {
            match.groups[2] != null -> {
                val label = match.groups[2]!!.value
                val url = match.groups[3]!!.value
                pushStringAnnotation(URL_TAG, url)
                pushStyle(SpanStyle(color = AppleColors.accent))
                append(label)
                pop()
                pop()
            }
            match.groups[5] != null -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(match.groups[5]!!.value)
                pop()
            }
            match.groups[7] != null -> {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.White.copy(alpha = 0.08f)))
                append(match.groups[7]!!.value)
                pop()
            }
            match.groups[9] != null -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(match.groups[9]!!.value)
                pop()
            }
        }
        cursor = match.range.last + 1
    }
    append(source.substring(cursor))
}

@Composable
private fun InlineMarkdownText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val annotated = parseInlineMarkdown(text)
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = AppleColors.secondary),
        onClick = { offset ->
            annotated.getStringAnnotations(URL_TAG, offset, offset).firstOrNull()?.let {
                runCatching { uriHandler.openUri(it.item) }
            }
        },
    )
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        markdown.lines().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isBlank() -> Spacer(Modifier.width(1.dp))
                line.startsWith("### ") -> InlineMarkdownText(line.removePrefix("### "), AppleTypography.titleMedium)
                line.startsWith("## ") -> InlineMarkdownText(line.removePrefix("## "), AppleTypography.titleLarge)
                line.startsWith("# ") -> InlineMarkdownText(line.removePrefix("# "), AppleTypography.headlineLarge)
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text("•", color = AppleColors.secondary)
                    Spacer(Modifier.width(8.dp))
                    InlineMarkdownText(line.drop(2), AppleTypography.bodyMedium, Modifier.weight(1f))
                }
                line.matches(Regex("\\d+\\. .+")) -> {
                    val separator = line.indexOf(' ') + 1
                    Row {
                        Text(line.substring(0, separator), color = AppleColors.secondary)
                        Spacer(Modifier.width(8.dp))
                        InlineMarkdownText(line.substring(separator), AppleTypography.bodyMedium, Modifier.weight(1f))
                    }
                }
                else -> InlineMarkdownText(line, AppleTypography.bodyMedium)
            }
        }
    }
}
