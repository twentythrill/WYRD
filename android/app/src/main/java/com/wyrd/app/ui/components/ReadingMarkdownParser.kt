package com.wyrd.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

enum class TableColumnAlignment {
    LEFT, CENTER, RIGHT
}

data class MarkdownTableCell(
    val rawText: String,
    val annotatedText: AnnotatedString,
    val plainText: String,
    val startOffset: Int,
    val endOffset: Int
)

data class MarkdownTableRow(
    val cells: List<MarkdownTableCell>
)

data class MarkdownCodeData(
    val language: String,
    val rawCode: String,
    val startOffset: Int,
    val endOffset: Int
)

data class MarkdownTableData(
    val headers: List<MarkdownTableCell>,
    val alignments: List<TableColumnAlignment>,
    val rows: List<MarkdownTableRow>,
    val plainText: String,
    val startOffset: Int,
    val endOffset: Int
)

sealed class MessageContentBlock {
    data class Text(
        val formatted: FormattedReadingContent,
        val startOffset: Int
    ) : MessageContentBlock()

    data class Table(
        val table: MarkdownTableData,
        val startOffset: Int
    ) : MessageContentBlock()

    data class Code(
        val code: MarkdownCodeData,
        val startOffset: Int
    ) : MessageContentBlock()
}

data class FormattedReadingContent(
    val plainText: String,
    val baseAnnotatedString: AnnotatedString,
    val blocks: List<MessageContentBlock> = emptyList()
)

private val TABLE_SEPARATOR_REGEX = Regex("^:?-+:?$")
private val ORDERED_LIST_REGEX = Regex("^(\\d+)\\.\\s+(.*)$")
private val SAFE_URL_PROTOCOL_REGEX = Regex("^(https?://|mailto:)", RegexOption.IGNORE_CASE)

private fun parseTableCells(line: String): List<String> {
    var trimmed = line.trim()
    if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
    if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length - 1)
    return trimmed.split("|").map { it.trim() }
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("-") || !trimmed.contains("|")) return false
    val cells = parseTableCells(trimmed)
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        val c = cell.trim()
        c.isNotEmpty() && TABLE_SEPARATOR_REGEX.matches(c)
    }
}

private fun parseTableAlignments(line: String): List<TableColumnAlignment> {
    val cells = parseTableCells(line)
    return cells.map { cell ->
        val trimmed = cell.trim()
        val startsWithColon = trimmed.startsWith(":")
        val endsWithColon = trimmed.endsWith(":")
        when {
            startsWithColon && endsWithColon -> TableColumnAlignment.CENTER
            endsWithColon -> TableColumnAlignment.RIGHT
            else -> TableColumnAlignment.LEFT
        }
    }
}

private fun appendInlineFormatted(
    builder: AnnotatedString.Builder,
    plainBuilder: StringBuilder,
    text: String,
    boldColor: Color = Color(0xFFCECDC4),
    italicColor: Color = boldColor,
    inlineCodeBg: Color = Color(0xFF222222),
    inlineCodeColor: Color = Color(0xFFCECDC4)
) {
    var cursor = 0
    val len = text.length

    while (cursor < len) {
        // Inline code: `code`
        if (text[cursor] == '`') {
            val end = text.indexOf('`', cursor + 1)
            if (end != -1) {
                val code = text.substring(cursor + 1, end)
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBg,
                        color = inlineCodeColor,
                        fontSize = 15.sp
                    )
                )
                builder.append(code)
                builder.pop()
                plainBuilder.append(code)
                cursor = end + 1
                continue
            }
        }

        // Strikethrough: ~~text~~
        if (cursor + 1 < len && text[cursor] == '~' && text[cursor + 1] == '~') {
            val end = text.indexOf("~~", cursor + 2)
            if (end != -1) {
                val strikeText = text.substring(cursor + 2, end)
                builder.pushStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        color = italicColor.copy(alpha = 0.75f)
                    )
                )
                appendInlineFormatted(
                    builder = builder,
                    plainBuilder = plainBuilder,
                    text = strikeText,
                    boldColor = boldColor,
                    italicColor = italicColor,
                    inlineCodeBg = inlineCodeBg,
                    inlineCodeColor = inlineCodeColor
                )
                builder.pop()
                cursor = end + 2
                continue
            }
        }

        // Bold: **text** or __text__
        if (cursor + 1 < len && ((text[cursor] == '*' && text[cursor + 1] == '*') || (text[cursor] == '_' && text[cursor + 1] == '_'))) {
            val marker = text.substring(cursor, cursor + 2)
            val end = text.indexOf(marker, cursor + 2)
            if (end != -1) {
                val boldText = text.substring(cursor + 2, end)
                builder.pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = boldColor
                    )
                )
                appendInlineFormatted(
                    builder = builder,
                    plainBuilder = plainBuilder,
                    text = boldText,
                    boldColor = boldColor,
                    italicColor = italicColor,
                    inlineCodeBg = inlineCodeBg,
                    inlineCodeColor = inlineCodeColor
                )
                builder.pop()
                cursor = end + 2
                continue
            }
        }

        // Italic: *text* or _text_
        if ((text[cursor] == '*' || text[cursor] == '_') && cursor + 1 < len && text[cursor + 1] != ' ' && text[cursor + 1] != text[cursor]) {
            val marker = text[cursor]
            val end = text.indexOf(marker, cursor + 1)
            if (end != -1 && end > cursor + 1) {
                val italicText = text.substring(cursor + 1, end)
                builder.pushStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = italicColor
                    )
                )
                appendInlineFormatted(
                    builder = builder,
                    plainBuilder = plainBuilder,
                    text = italicText,
                    boldColor = boldColor,
                    italicColor = italicColor,
                    inlineCodeBg = inlineCodeBg,
                    inlineCodeColor = inlineCodeColor
                )
                builder.pop()
                cursor = end + 1
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
                    val url = text.substring(closeBracket + 2, closeParen).trim()
                    val isSafeUrl = SAFE_URL_PROTOCOL_REGEX.containsMatchIn(url)
                    if (isSafeUrl) {
                        builder.pushStringAnnotation(tag = "URL", annotation = url)
                    }
                    builder.pushStyle(
                        SpanStyle(
                            color = boldColor,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    appendInlineFormatted(
                        builder = builder,
                        plainBuilder = plainBuilder,
                        text = label.ifEmpty { url },
                        boldColor = boldColor,
                        italicColor = italicColor,
                        inlineCodeBg = inlineCodeBg,
                        inlineCodeColor = inlineCodeColor
                    )
                    builder.pop()
                    if (isSafeUrl) {
                        builder.pop()
                    }
                    cursor = closeParen + 1
                    continue
                }
            }
        }

        // Normal character
        builder.append(text[cursor])
        plainBuilder.append(text[cursor])
        cursor++
    }
}

private fun formatCellContent(
    text: String,
    boldColor: Color,
    italicColor: Color,
    inlineCodeBg: Color,
    inlineCodeColor: Color
): Pair<AnnotatedString, String> {
    val builder = AnnotatedString.Builder()
    val plainBuilder = StringBuilder()
    appendInlineFormatted(
        builder = builder,
        plainBuilder = plainBuilder,
        text = text.trim(),
        boldColor = boldColor,
        italicColor = italicColor,
        inlineCodeBg = inlineCodeBg,
        inlineCodeColor = inlineCodeColor
    )
    return Pair(builder.toAnnotatedString(), plainBuilder.toString())
}

private fun formatTextSegment(
    lines: List<String>,
    headerColor: Color,
    inlineCodeBg: Color,
    inlineCodeColor: Color,
    boldColor: Color,
    italicColor: Color,
    quoteColor: Color,
    bulletColor: Color
): FormattedReadingContent {
    val builder = AnnotatedString.Builder()
    val plainBuilder = StringBuilder()
    var inCodeBlock = false
    var lineIndex = 0

    while (lineIndex < lines.size) {
        val line = lines[lineIndex]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            lineIndex++
            continue
        }

        if (inCodeBlock) {
            builder.pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = inlineCodeBg,
                    color = inlineCodeColor,
                    fontSize = 14.sp
                )
            )
            builder.append(line)
            builder.pop()
            plainBuilder.append(line)
            if (lineIndex < lines.size - 1) {
                builder.append("\n")
                plainBuilder.append("\n")
            }
            lineIndex++
            continue
        }

        // Headers
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                val headerContent = trimmed.drop(level + 1).trim()
                val fontSize = when (level) {
                    1 -> 22.sp
                    2 -> 20.sp
                    else -> 18.sp
                }
                builder.pushStyle(
                    SpanStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = headerColor
                    )
                )
                appendInlineFormatted(
                    builder = builder,
                    plainBuilder = plainBuilder,
                    text = headerContent,
                    boldColor = boldColor,
                    italicColor = italicColor,
                    inlineCodeBg = inlineCodeBg,
                    inlineCodeColor = inlineCodeColor
                )
                builder.pop()
                if (lineIndex < lines.size - 1) {
                    builder.append("\n")
                    plainBuilder.append("\n")
                }
                lineIndex++
                continue
            }
        }

        // Blockquotes
        if (trimmed.startsWith(">")) {
            val quoteContent = trimmed.removePrefix(">").trim()
            builder.pushStyle(
                SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = quoteColor
                )
            )
            appendInlineFormatted(
                builder = builder,
                plainBuilder = plainBuilder,
                text = quoteContent,
                boldColor = boldColor,
                italicColor = italicColor,
                inlineCodeBg = inlineCodeBg,
                inlineCodeColor = inlineCodeColor
            )
            builder.pop()
            if (lineIndex < lines.size - 1) {
                builder.append("\n")
                plainBuilder.append("\n")
            }
            lineIndex++
            continue
        }

        // Unordered List
        if ((trimmed.startsWith("- ") || trimmed.startsWith("* ")) && trimmed.length > 2) {
            val itemContent = trimmed.drop(2).trim()
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bulletColor))
            builder.append("• ")
            builder.pop()
            plainBuilder.append("• ")
            appendInlineFormatted(
                builder = builder,
                plainBuilder = plainBuilder,
                text = itemContent,
                boldColor = boldColor,
                italicColor = italicColor,
                inlineCodeBg = inlineCodeBg,
                inlineCodeColor = inlineCodeColor
            )
            if (lineIndex < lines.size - 1) {
                // Blank line between consecutive bullet points for airy reading rhythm.
                val nextTrimmed = lines[lineIndex + 1].trim()
                val nextIsBullet = (nextTrimmed.startsWith("- ") || nextTrimmed.startsWith("* ")) && nextTrimmed.length > 2
                if (nextIsBullet) {
                    builder.append("\n\n")
                    plainBuilder.append("\n\n")
                } else {
                    builder.append("\n")
                    plainBuilder.append("\n")
                }
            }
            lineIndex++
            continue
        }

        // Ordered List
        val numMatch = ORDERED_LIST_REGEX.matchEntire(trimmed)
        if (numMatch != null) {
            val num = numMatch.groupValues[1]
            val itemContent = numMatch.groupValues[2].trim()
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bulletColor))
            builder.append("$num. ")
            builder.pop()
            plainBuilder.append("$num. ")
            appendInlineFormatted(
                builder = builder,
                plainBuilder = plainBuilder,
                text = itemContent,
                boldColor = boldColor,
                italicColor = italicColor,
                inlineCodeBg = inlineCodeBg,
                inlineCodeColor = inlineCodeColor
            )
            if (lineIndex < lines.size - 1) {
                builder.append("\n")
                plainBuilder.append("\n")
            }
            lineIndex++
            continue
        }

        if (trimmed.isEmpty()) {
            if (lineIndex < lines.size - 1) {
                builder.append("\n")
                plainBuilder.append("\n")
            }
            lineIndex++
            continue
        }

        // Normal paragraph text
        appendInlineFormatted(
            builder = builder,
            plainBuilder = plainBuilder,
            text = line,
            boldColor = boldColor,
            italicColor = italicColor,
            inlineCodeBg = inlineCodeBg,
            inlineCodeColor = inlineCodeColor
        )
        if (lineIndex < lines.size - 1) {
            builder.append("\n")
            plainBuilder.append("\n")
        }
        lineIndex++
    }

    return FormattedReadingContent(
        plainText = plainBuilder.toString(),
        baseAnnotatedString = builder.toAnnotatedString()
    )
}

fun formatMarkdownForReading(
    markdown: String,
    textColor: Color = Color(0xFFCECDC4),
    headerColor: Color = Color(0xFFCECDC4),
    inlineCodeBg: Color = Color(0xFF222222),
    inlineCodeColor: Color = textColor,
    boldColor: Color = headerColor,
    italicColor: Color = textColor,
    quoteColor: Color = textColor.copy(alpha = 0.75f),
    bulletColor: Color = textColor.copy(alpha = 0.65f)
): FormattedReadingContent {
    val lines = markdown.split("\n")
    val blocks = mutableListOf<MessageContentBlock>()
    val overallPlainBuilder = StringBuilder()
    val overallAnnotatedBuilder = AnnotatedString.Builder()

    val currentTextLines = mutableListOf<String>()
    var lineIndex = 0

    fun flushCurrentText() {
        if (currentTextLines.isEmpty()) return
        val textStartOffset = overallPlainBuilder.length
        val textContent = formatTextSegment(
            lines = currentTextLines,
            headerColor = headerColor,
            inlineCodeBg = inlineCodeBg,
            inlineCodeColor = inlineCodeColor,
            boldColor = boldColor,
            italicColor = italicColor,
            quoteColor = quoteColor,
            bulletColor = bulletColor
        )
        if (textContent.plainText.isNotEmpty() || textContent.baseAnnotatedString.isNotEmpty()) {
            overallPlainBuilder.append(textContent.plainText)
            overallAnnotatedBuilder.append(textContent.baseAnnotatedString)
            blocks.add(MessageContentBlock.Text(textContent, textStartOffset))
        }
        currentTextLines.clear()
    }

    while (lineIndex < lines.size) {
        val line = lines[lineIndex]
        val trimmed = line.trim()

        // Check if this line starts a code block
        if (trimmed.startsWith("```")) {
            flushCurrentText()

            if (overallPlainBuilder.isNotEmpty() && !overallPlainBuilder.endsWith("\n")) {
                overallPlainBuilder.append("\n")
                overallAnnotatedBuilder.append("\n")
            }

            val codeStartOffset = overallPlainBuilder.length
            val lang = trimmed.removePrefix("```").trim()
            lineIndex++ // skip opening fence

            val codeLines = mutableListOf<String>()
            while (lineIndex < lines.size) {
                val nextLine = lines[lineIndex]
                if (nextLine.trim().startsWith("```")) {
                    lineIndex++ // skip closing fence
                    break
                }
                codeLines.add(nextLine)
                lineIndex++
            }

            val rawCode = codeLines.joinToString("\n")
            overallPlainBuilder.append(rawCode)
            overallAnnotatedBuilder.append(rawCode)
            val codeEndOffset = overallPlainBuilder.length

            val codeData = MarkdownCodeData(
                language = lang,
                rawCode = rawCode,
                startOffset = codeStartOffset,
                endOffset = codeEndOffset
            )
            blocks.add(MessageContentBlock.Code(codeData, codeStartOffset))

            if (lineIndex < lines.size) {
                if (!overallPlainBuilder.endsWith("\n\n")) overallPlainBuilder.append("\n")
                if (!overallAnnotatedBuilder.toAnnotatedString().text.endsWith("\n\n")) overallAnnotatedBuilder.append("\n")
            }
            continue
        }

        // Check if this line starts a table
        if (trimmed.contains("|") && lineIndex + 1 < lines.size && isTableSeparator(lines[lineIndex + 1])) {
            flushCurrentText()

            val tableStartOffset = overallPlainBuilder.length
            val headerCellsRaw = parseTableCells(line)
            val sepLine = lines[lineIndex + 1]
            val alignments = parseTableAlignments(sepLine)
            lineIndex += 2 // skip header and separator lines

            val dataRowsRaw = mutableListOf<List<String>>()
            while (lineIndex < lines.size) {
                val nextLine = lines[lineIndex].trim()
                if (nextLine.isEmpty() || !nextLine.contains("|") || nextLine.startsWith("```") || nextLine.startsWith("#") || nextLine.startsWith(">")) {
                    break
                }
                dataRowsRaw.add(parseTableCells(nextLine))
                lineIndex++
            }

            val numCols = maxOf(headerCellsRaw.size, dataRowsRaw.maxOfOrNull { it.size } ?: 0)
            if (numCols > 0) {
                val tablePlainBuilder = StringBuilder()

                val parsedHeaders = (0 until numCols).map { cIdx ->
                    val cellText = headerCellsRaw.getOrNull(cIdx) ?: ""
                    val (annotated, plain) = formatCellContent(cellText, boldColor, italicColor, inlineCodeBg, inlineCodeColor)
                    val cellStart = tableStartOffset + tablePlainBuilder.length
                    tablePlainBuilder.append(plain)
                    if (cIdx < numCols - 1) tablePlainBuilder.append(" | ") else tablePlainBuilder.append("\n")
                    val cellEnd = tableStartOffset + tablePlainBuilder.length
                    MarkdownTableCell(cellText, annotated, plain, cellStart, cellEnd)
                }

                val parsedRows = dataRowsRaw.map { rowCells ->
                    val cells = (0 until numCols).map { cIdx ->
                        val cellText = rowCells.getOrNull(cIdx) ?: ""
                        val (annotated, plain) = formatCellContent(cellText, boldColor, italicColor, inlineCodeBg, inlineCodeColor)
                        val cellStart = tableStartOffset + tablePlainBuilder.length
                        tablePlainBuilder.append(plain)
                        if (cIdx < numCols - 1) tablePlainBuilder.append(" — ") else tablePlainBuilder.append("\n")
                        val cellEnd = tableStartOffset + tablePlainBuilder.length
                        MarkdownTableCell(cellText, annotated, plain, cellStart, cellEnd)
                    }
                    MarkdownTableRow(cells)
                }

                val tablePlainText = tablePlainBuilder.toString()
                overallPlainBuilder.append(tablePlainText)
                val tableEndOffset = overallPlainBuilder.length
                val tableData = MarkdownTableData(parsedHeaders, alignments, parsedRows, tablePlainText, tableStartOffset, tableEndOffset)
                blocks.add(MessageContentBlock.Table(tableData, tableStartOffset))

                overallAnnotatedBuilder.append(tablePlainText)

                if (lineIndex < lines.size) {
                    if (!overallPlainBuilder.endsWith("\n\n")) overallPlainBuilder.append("\n")
                    if (!overallAnnotatedBuilder.toAnnotatedString().text.endsWith("\n\n")) overallAnnotatedBuilder.append("\n")
                }
            }
            continue
        }

        currentTextLines.add(line)
        lineIndex++
    }

    flushCurrentText()

    if (blocks.isEmpty()) {
        val emptyContent = FormattedReadingContent("", AnnotatedString(""))
        blocks.add(MessageContentBlock.Text(emptyContent, 0))
    }

    return FormattedReadingContent(
        plainText = overallPlainBuilder.toString(),
        baseAnnotatedString = overallAnnotatedBuilder.toAnnotatedString(),
        blocks = blocks
    )
}

fun getHighlightedAnnotatedString(
    formatted: FormattedReadingContent,
    highlightRange: Pair<Int, Int>?,
    highlightBg: Color = Color(0xFFCECDC4),
    highlightTextColor: Color = Color(0xFF000000)
): AnnotatedString {
    if (highlightRange == null) return formatted.baseAnnotatedString

    val (start, end) = highlightRange
    val totalLen = formatted.baseAnnotatedString.length
    val safeStart = start.coerceIn(0, totalLen)
    val safeEnd = end.coerceIn(safeStart, totalLen)
    if (safeStart >= safeEnd) return formatted.baseAnnotatedString

    return buildAnnotatedString {
        append(formatted.baseAnnotatedString)
        addStyle(
            style = SpanStyle(
                background = highlightBg,
                color = highlightTextColor
            ),
            start = safeStart,
            end = safeEnd
        )
    }
}
