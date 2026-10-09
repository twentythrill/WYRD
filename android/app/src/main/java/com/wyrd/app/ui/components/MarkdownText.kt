package com.wyrd.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextGray
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = 17.sp,
    textColor: Color = TextWhite
) {
    val context = LocalContext.current
    val blocks = parseMarkdownBlocks(markdown)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> (baseFontSize.value * 1.35f).sp
                        2 -> (baseFontSize.value * 1.20f).sp
                        else -> (baseFontSize.value * 1.10f).sp
                    }
                    Text(
                        text = parseInlineMarkdown(block.text),
                        fontFamily = SpaceGrotesk,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = White,
                        lineHeight = (fontSize.value * 1.35f).sp
                    )
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        code = block.code,
                        language = block.language,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Code", block.code))
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(TextMuted)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = parseInlineMarkdown(block.text),
                            fontFamily = SpaceGrotesk,
                            fontSize = baseFontSize,
                            fontStyle = FontStyle.Italic,
                            color = TextGray,
                            lineHeight = (baseFontSize.value * 1.35f).sp
                        )
                    }
                }

                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (block.number != null) "${block.number}. " else "• ",
                            fontFamily = SpaceGrotesk,
                            fontSize = baseFontSize,
                            fontWeight = FontWeight.Bold,
                            color = TextGray
                        )
                        Text(
                            text = parseInlineMarkdown(block.text),
                            fontFamily = SpaceGrotesk,
                            fontSize = baseFontSize,
                            color = textColor,
                            lineHeight = (baseFontSize.value * 1.35f).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text),
                        fontFamily = SpaceGrotesk,
                        fontSize = baseFontSize,
                        color = textColor,
                        lineHeight = (baseFontSize.value * 1.35f).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    code: String,
    language: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
    ) {
        // Top header bar: language name & copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" },
                fontFamily = SpaceGrotesk,
                fontSize = 12.sp,
                color = TextGray
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    tint = TextGray,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Code content with horizontal scrolling
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.5.sp,
                color = TextWhite,
                lineHeight = 20.sp
            )
        }
    }
}

private sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class ListItem(val number: Int?, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.split("\n")
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Code block fence (```)
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeBuilder = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (codeBuilder.isNotEmpty()) codeBuilder.append("\n")
                codeBuilder.append(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(language, codeBuilder.toString()))
            i++
            continue
        }

        // Skip blank lines
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 2. Headers (# Header)
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                val headerText = trimmed.drop(level + 1).trim()
                blocks.add(MarkdownBlock.Header(level, headerText))
                i++
                continue
            }
        }

        // 3. Blockquotes (> Quote)
        if (trimmed.startsWith(">")) {
            val quoteText = trimmed.removePrefix(">").trim()
            blocks.add(MarkdownBlock.Blockquote(quoteText))
            i++
            continue
        }

        // 4. Unordered list (- Item or * Item)
        if ((trimmed.startsWith("- ") || trimmed.startsWith("* ")) && trimmed.length > 2) {
            val itemText = trimmed.drop(2).trim()
            blocks.add(MarkdownBlock.ListItem(number = null, text = itemText))
            i++
            continue
        }

        // 5. Ordered list (1. Item)
        val orderedMatch = Regex("^(\\d+)\\.\\s+(.*)$").matchEntire(trimmed)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1].toIntOrNull()
            val itemText = orderedMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.ListItem(number = num, text = itemText))
            i++
            continue
        }

        // 6. Regular paragraph (collect contiguous non-empty lines)
        val paraBuilder = StringBuilder(line)
        i++
        while (i < lines.size) {
            val nextLine = lines[i]
            val nextTrimmed = nextLine.trim()
            if (nextTrimmed.isEmpty() ||
                nextTrimmed.startsWith("```") ||
                nextTrimmed.startsWith("#") ||
                nextTrimmed.startsWith(">") ||
                nextTrimmed.startsWith("- ") ||
                nextTrimmed.startsWith("* ") ||
                Regex("^\\d+\\.\\s+.*$").matches(nextTrimmed)
            ) {
                break
            }
            paraBuilder.append("\n").append(nextLine)
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paraBuilder.toString()))
    }

    return blocks
}

@Composable
private fun parseInlineMarkdown(text: String): AnnotatedString {
    val inlineCodeBg = DarkSurfaceElevated
    val inlineCodeColor = White
    val boldColor = White
    val strikeColor = TextGray

    return buildAnnotatedString {
        var cursor = 0
        val len = text.length

        while (cursor < len) {
            // Inline code: `code`
            if (text[cursor] == '`') {
                val end = text.indexOf('`', cursor + 1)
                if (end != -1) {
                    val codeContent = text.substring(cursor + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            color = inlineCodeColor,
                            fontSize = 14.sp
                        )
                    )
                    append(" $codeContent ")
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Bold: **text** or __text__
            if (cursor + 1 < len && ((text[cursor] == '*' && text[cursor + 1] == '*') || (text[cursor] == '_' && text[cursor + 1] == '_'))) {
                val marker = text.substring(cursor, cursor + 2)
                val end = text.indexOf(marker, cursor + 2)
                if (end != -1) {
                    val boldContent = text.substring(cursor + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor))
                    append(boldContent)
                    pop()
                    cursor = end + 2
                    continue
                }
            }

            // Italic: *text* or _text_
            if ((text[cursor] == '*' || text[cursor] == '_') && (cursor + 1 < len && text[cursor + 1] != ' ' && text[cursor + 1] != text[cursor])) {
                val marker = text[cursor]
                val end = text.indexOf(marker, cursor + 1)
                if (end != -1 && end > cursor + 1) {
                    val italicContent = text.substring(cursor + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicContent)
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Strikethrough: ~~text~~
            if (cursor + 1 < len && text[cursor] == '~' && text[cursor + 1] == '~') {
                val end = text.indexOf("~~", cursor + 2)
                if (end != -1) {
                    val strikeContent = text.substring(cursor + 2, end)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = strikeColor))
                    append(strikeContent)
                    pop()
                    cursor = end + 2
                    continue
                }
            }

            // Markdown link: [label](url)
            if (text[cursor] == '[') {
                val closeBracket = text.indexOf(']', cursor + 1)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = text.substring(cursor + 1, closeBracket)
                        pushStyle(
                            SpanStyle(
                                color = Color(0xFF64B5F6),
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(label)
                        pop()
                        cursor = closeParen + 1
                        continue
                    }
                }
            }

            // Normal character
            append(text[cursor])
            cursor++
        }
    }
}
