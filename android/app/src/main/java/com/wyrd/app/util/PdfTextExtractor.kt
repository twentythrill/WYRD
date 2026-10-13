package com.wyrd.app.util

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater

/**
 * Lightweight, zero-dependency PDF text extractor using standard JDK Deflate decompression
 * and PDF content stream / CMap parsing, with PdfRenderer page-count fallback for scanned PDFs.
 */
object PdfTextExtractor {

    private const val MAX_EXTRACTED_CHARS = 80_000

    private val STREAM_MARKER = "stream".toByteArray(Charsets.ISO_8859_1)
    private val ENDSTREAM_MARKER = "endstream".toByteArray(Charsets.ISO_8859_1)

    private val BFCHAR_BLOCK_REGEX = Regex("""beginbfchar([\s\S]*?)endbfchar""")
    private val BFRANGE_BLOCK_REGEX = Regex("""beginbfrange([\s\S]*?)endbfrange""")
    private val BFCHAR_PAIR_REGEX = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
    private val BFRANGE_TRIPLE_REGEX = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
    private val BT_ET_REGEX = Regex("""\bBT\b([\s\S]*?)\bET\b""")
    private val TOKEN_REGEX = Regex(
        """\[(?:[^\[\]\\]|\\.|\\\r?\n|\((?:[^()\\]|\\.)*\))*\]\s*TJ|\((?:[^()\\]|\\.)*\)\s*(?:Tj|'|")|<[0-9A-Fa-f\s]+>\s*(?:Tj|'|")|-?\d+(?:\.\d+)?\s+-?\d+(?:\.\d+)?\s+T[dD]\b|\bT\*"""
    )
    private val STRING_ELEMENT_REGEX = Regex("""\((?:[^()\\]|\\.)*\)|<[0-9A-Fa-f\s]+>|-?\d+(?:\.\d+)?""")

    fun extractText(file: File): String {
        if (!file.exists() || file.length() == 0L) return ""
        return try {
            val bytes = file.readBytes()
            val streams = extractDecodedStreams(bytes)
            val cmap = HashMap<Int, String>()

            // Pass 1: Collect ToUnicode CMap mappings across all decoded streams
            for (streamText in streams) {
                if (streamText.contains("beginbfchar") || streamText.contains("beginbfrange")) {
                    parseToUnicodeCMap(streamText, cmap)
                }
            }

            // Pass 2: Extract text from BT ... ET content blocks
            val sb = StringBuilder()
            for (streamText in streams) {
                if (!streamText.contains("BT")) continue
                for (match in BT_ET_REGEX.findAll(streamText)) {
                    val block = match.groupValues[1]
                    extractTextFromBtBlock(block, cmap, sb)
                    if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                        sb.append('\n')
                    }
                    if (sb.length >= MAX_EXTRACTED_CHARS) break
                }
                if (sb.length >= MAX_EXTRACTED_CHARS) break
            }

            val cleaned = sb.toString()
                .replace(Regex("""[ \t]+"""), " ")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()

            if (cleaned.isNotBlank()) {
                if (cleaned.length > MAX_EXTRACTED_CHARS) {
                    cleaned.take(MAX_EXTRACTED_CHARS) + "\n\n... [Content truncated due to length] ..."
                } else {
                    cleaned
                }
            } else {
                val pageCount = getPageCount(file)
                if (pageCount > 0) {
                    "[Attached PDF Document: ${file.name} ($pageCount pages, ${AttachmentHelper.formatSize(file.length())}) - Scanned/Image PDF]"
                } else {
                    "[Attached PDF Document: ${file.name} (${AttachmentHelper.formatSize(file.length())})]"
                }
            }
        } catch (_: Throwable) {
            "[Attached PDF Document: ${file.name} (${AttachmentHelper.formatSize(file.length())})]"
        }
    }

    private fun getPageCount(file: File): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (_: Throwable) {
            0
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    private fun extractDecodedStreams(pdfBytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var pos = 0
        while (pos < pdfBytes.size) {
            val streamIdx = indexOfBytes(pdfBytes, STREAM_MARKER, pos)
            if (streamIdx == -1) break

            // Ensure "stream" is not the suffix of "endstream"
            if (streamIdx >= 3 &&
                pdfBytes[streamIdx - 3] == 'e'.code.toByte() &&
                pdfBytes[streamIdx - 2] == 'n'.code.toByte() &&
                pdfBytes[streamIdx - 1] == 'd'.code.toByte()
            ) {
                pos = streamIdx + STREAM_MARKER.size
                continue
            }

            var dataStart = streamIdx + STREAM_MARKER.size
            // Skip optional horizontal whitespace before EOL
            while (dataStart < pdfBytes.size && (pdfBytes[dataStart] == ' '.code.toByte() || pdfBytes[dataStart] == '\t'.code.toByte())) {
                dataStart++
            }
            if (dataStart < pdfBytes.size && pdfBytes[dataStart] == '\r'.code.toByte()) {
                dataStart++
            }
            if (dataStart < pdfBytes.size && pdfBytes[dataStart] == '\n'.code.toByte()) {
                dataStart++
            }

            val endStreamIdx = indexOfBytes(pdfBytes, ENDSTREAM_MARKER, dataStart)
            if (endStreamIdx == -1) break

            var dataEnd = endStreamIdx
            if (dataEnd > dataStart && pdfBytes[dataEnd - 1] == '\n'.code.toByte()) dataEnd--
            if (dataEnd > dataStart && pdfBytes[dataEnd - 1] == '\r'.code.toByte()) dataEnd--

            if (dataEnd > dataStart) {
                // Check dictionary header preceding "stream" for /FlateDecode
                val dictStart = (streamIdx - 320).coerceAtLeast(0)
                val dictHeader = String(pdfBytes, dictStart, streamIdx - dictStart, Charsets.ISO_8859_1)
                val rawSlice = pdfBytes.copyOfRange(dataStart, dataEnd)

                val decodedBytes = if (dictHeader.contains("/FlateDecode") || dictHeader.contains("/Fl")) {
                    inflateStream(rawSlice)
                } else {
                    rawSlice
                }

                if (decodedBytes != null && decodedBytes.isNotEmpty()) {
                    result.add(String(decodedBytes, Charsets.ISO_8859_1))
                }
            }

            pos = endStreamIdx + ENDSTREAM_MARKER.size
        }
        return result
    }

    private fun inflateStream(data: ByteArray): ByteArray? {
        return inflateWithMode(data, nowrap = false) ?: inflateWithMode(data, nowrap = true)
    }

    private fun inflateWithMode(data: ByteArray, nowrap: Boolean): ByteArray? {
        val inflater = Inflater(nowrap)
        return try {
            inflater.setInput(data)
            val bos = ByteArrayOutputStream(data.size * 2)
            val buf = ByteArray(4096)
            var total = 0
            while (!inflater.finished() && total < 2_000_000) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    bos.write(buf, 0, count)
                    total += count
                }
            }
            val out = bos.toByteArray()
            if (out.isNotEmpty()) out else null
        } catch (_: Throwable) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun parseToUnicodeCMap(streamText: String, cmap: MutableMap<Int, String>) {
        for (block in BFCHAR_BLOCK_REGEX.findAll(streamText)) {
            for (pair in BFCHAR_PAIR_REGEX.findAll(block.groupValues[1])) {
                val srcCode = pair.groupValues[1].toIntOrNull(16) ?: continue
                val dstStr = decodeHexToUnicode(pair.groupValues[2])
                if (dstStr.isNotEmpty()) {
                    cmap[srcCode] = dstStr
                }
            }
        }
        for (block in BFRANGE_BLOCK_REGEX.findAll(streamText)) {
            for (triple in BFRANGE_TRIPLE_REGEX.findAll(block.groupValues[1])) {
                val startCode = triple.groupValues[1].toIntOrNull(16) ?: continue
                val endCode = triple.groupValues[2].toIntOrNull(16) ?: continue
                val targetStart = triple.groupValues[3].toIntOrNull(16) ?: continue
                val span = (endCode - startCode).coerceIn(0, 1024)
                for (offset in 0..span) {
                    val codePoint = targetStart + offset
                    if (codePoint in 1..0x10FFFF) {
                        cmap[startCode + offset] = String(Character.toChars(codePoint))
                    }
                }
            }
        }
    }

    private fun extractTextFromBtBlock(block: String, cmap: Map<Int, String>, out: StringBuilder) {
        for (tokenMatch in TOKEN_REGEX.findAll(block)) {
            val token = tokenMatch.value.trim()
            when {
                token == "T*" -> {
                    out.append('\n')
                }
                token.endsWith("Td") || token.endsWith("TD") -> {
                    val parts = token.split(Regex("""\s+"""))
                    val ty = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    if (kotlin.math.abs(ty) > 1.5) {
                        out.append('\n')
                    } else if (out.isNotEmpty() && !out.endsWith(" ") && !out.endsWith("\n")) {
                        out.append(' ')
                    }
                }
                token.endsWith("TJ") -> {
                    val arrayContent = token.removeSuffix("TJ").trim().removePrefix("[").removeSuffix("]")
                    for (elemMatch in STRING_ELEMENT_REGEX.findAll(arrayContent)) {
                        val elem = elemMatch.value
                        when {
                            elem.startsWith("(") && elem.endsWith(")") -> {
                                out.append(decodePdfLiteralString(elem.substring(1, elem.length - 1), cmap))
                            }
                            elem.startsWith("<") && elem.endsWith(">") -> {
                                out.append(decodePdfHexString(elem.substring(1, elem.length - 1), cmap))
                            }
                            else -> {
                                val kerning = elem.toDoubleOrNull() ?: 0.0
                                if (kerning < -120.0 && out.isNotEmpty() && !out.endsWith(" ") && !out.endsWith("\n")) {
                                    out.append(' ')
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (token.endsWith("'") || token.endsWith("\"")) {
                        out.append('\n')
                    }
                    val strPart = token.replace(Regex("""\s*(?:Tj|'|")$"""), "").trim()
                    if (strPart.startsWith("(") && strPart.endsWith(")")) {
                        out.append(decodePdfLiteralString(strPart.substring(1, strPart.length - 1), cmap))
                    } else if (strPart.startsWith("<") && strPart.endsWith(">")) {
                        out.append(decodePdfHexString(strPart.substring(1, strPart.length - 1), cmap))
                    }
                }
            }
        }
    }

    private fun decodePdfLiteralString(raw: String, cmap: Map<Int, String>): String {
        val bytes = ByteArrayOutputStream(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                when {
                    next == 'n' -> { bytes.write('\n'.code); i += 2 }
                    next == 'r' -> { bytes.write('\r'.code); i += 2 }
                    next == 't' -> { bytes.write('\t'.code); i += 2 }
                    next == 'b' -> { bytes.write('\b'.code); i += 2 }
                    next == 'f' -> { bytes.write(12); i += 2 }
                    next == '(' || next == ')' || next == '\\' -> { bytes.write(next.code); i += 2 }
                    next == '\r' -> {
                        i += if (i + 2 < raw.length && raw[i + 2] == '\n') 3 else 2
                    }
                    next == '\n' -> { i += 2 }
                    next in '0'..'7' -> {
                        var oct = next.toString()
                        var step = 2
                        if (i + 2 < raw.length && raw[i + 2] in '0'..'7') {
                            oct += raw[i + 2]
                            step++
                            if (i + 3 < raw.length && raw[i + 3] in '0'..'7') {
                                oct += raw[i + 3]
                                step++
                            }
                        }
                        bytes.write(oct.toInt(8) and 0xFF)
                        i += step
                    }
                    else -> {
                        bytes.write(next.code and 0xFF)
                        i += 2
                    }
                }
            } else {
                bytes.write(c.code and 0xFF)
                i++
            }
        }
        val b = bytes.toByteArray()
        if (b.size >= 2 && (b[0].toInt() and 0xFF) == 0xFE && (b[1].toInt() and 0xFF) == 0xFF) {
            return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        }
        val sb = StringBuilder(b.size)
        for (byteVal in b) {
            val code = byteVal.toInt() and 0xFF
            val mapped = cmap[code]
            if (mapped != null) {
                sb.append(mapped)
            } else if (code in 32..126 || code == 10 || code == 9 || code >= 160) {
                sb.append(code.toChar())
            }
        }
        return sb.toString()
    }

    private fun decodePdfHexString(rawHex: String, cmap: Map<Int, String>): String {
        val hex = rawHex.replace(Regex("""\s+"""), "")
        if (hex.isEmpty()) return ""
        val padded = if (hex.length % 2 != 0) "${hex}0" else hex

        if (padded.startsWith("FEFF", ignoreCase = true)) {
            return decodeHexToUnicode(padded.substring(4))
        }

        // Check if 2-byte big-endian codes exist in CMap
        if (padded.length % 4 == 0 && cmap.keys.any { it > 0xFF }) {
            val sb2 = StringBuilder()
            var allMatched = true
            var idx = 0
            while (idx < padded.length) {
                val code = padded.substring(idx, idx + 4).toIntOrNull(16) ?: 0
                val mapped = cmap[code]
                if (mapped != null) {
                    sb2.append(mapped)
                } else {
                    allMatched = false
                    break
                }
                idx += 4
            }
            if (allMatched && sb2.isNotEmpty()) return sb2.toString()
        }

        val sb = StringBuilder()
        var idx = 0
        while (idx < padded.length) {
            val code = padded.substring(idx, idx + 2).toIntOrNull(16) ?: 0
            val mapped = cmap[code]
            if (mapped != null) {
                sb.append(mapped)
            } else if (code in 32..126 || code == 10 || code == 9 || code >= 160) {
                sb.append(code.toChar())
            }
            idx += 2
        }
        return sb.toString()
    }

    private fun decodeHexToUnicode(hex: String): String {
        val clean = hex.trim()
        if (clean.isEmpty()) return ""
        if (clean.length == 2) {
            val code = clean.toIntOrNull(16) ?: return ""
            return code.toChar().toString()
        }
        val bytes = ByteArray(clean.length / 2)
        for (i in bytes.indices) {
            bytes[i] = (clean.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0).toByte()
        }
        return String(bytes, Charsets.UTF_16BE)
    }

    private fun indexOfBytes(source: ByteArray, target: ByteArray, startIndex: Int): Int {
        if (target.isEmpty() || source.size - startIndex < target.size) return -1
        val first = target[0]
        val max = source.size - target.size
        var i = startIndex.coerceAtLeast(0)
        while (i <= max) {
            if (source[i] != first) {
                while (++i <= max && source[i] != first) { /* fast scan */ }
            }
            if (i <= max) {
                var j = 1
                while (j < target.size && source[i + j] == target[j]) j++
                if (j == target.size) return i
            }
            i++
        }
        return -1
    }
}
