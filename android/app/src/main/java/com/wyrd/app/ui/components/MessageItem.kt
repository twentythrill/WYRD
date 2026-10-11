package com.wyrd.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.data.local.MessageEntity
import com.wyrd.app.data.tts.NativeTtsManager
import com.wyrd.app.data.tts.TtsPlaybackState
import com.wyrd.app.ui.theme.AccentRed
import com.wyrd.app.ui.theme.Black
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.input.pointer.PointerEventPass
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.LocalWyrdColors
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextGray
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wyrd.app.util.AttachmentHelper

private val imageOkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

fun openAttachmentUri(context: android.content.Context, uri: String, isImage: Boolean = true) {
    try {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            context.startActivity(intent)
        } else {
            val file = File(uri)
            if (file.exists()) {
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val type = if (isImage) "image/*" else (context.contentResolver.getType(contentUri) ?: "*/*")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, type)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        }
    } catch (e: Exception) {
        Log.w("MessageItem", "Could not open attachment: $uri", e)
    }
}

@Composable
fun AttachmentImageView(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        withContext(Dispatchers.IO) {
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                val safeHash = try {
                    val md = MessageDigest.getInstance("MD5")
                    md.digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
                } catch (_: Exception) {
                    uri.hashCode().toString()
                }
                val cacheDir = File(context.cacheDir, "image_cache").apply { mkdirs() }
                val cachedFile = File(cacheDir, safeHash)
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    val bm = BitmapFactory.decodeFile(cachedFile.absolutePath)
                    if (bm != null) {
                        value = bm.asImageBitmap()
                        return@withContext
                    }
                }
                try {
                    val req = Request.Builder().url(uri).build()
                    imageOkHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bytes = resp.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                try { cachedFile.writeBytes(bytes) } catch (_: Exception) {}
                                val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bm != null) {
                                    value = bm.asImageBitmap()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AttachmentImageView", "Failed to load image from URL: $uri", e)
                }
            } else {
                try {
                    val file = File(uri)
                    if (file.exists()) {
                        val bm = BitmapFactory.decodeFile(file.absolutePath)
                        if (bm != null) {
                            value = bm.asImageBitmap()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val bm = bitmapState.value
    if (bm != null) {
        Image(
            bitmap = bm,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(DarkSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalWyrdColors.current.textSecondary
            )
        }
    }
}

@Composable
fun UserMessageBubble(
    message: MessageEntity,
    onEdit: (MessageEntity) -> Unit = {},
    onBranch: (MessageEntity) -> Unit = {},
    onRedo: (MessageEntity) -> Unit = {},
    onCopy: (MessageEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticManager = remember {
        (context.applicationContext as? com.wyrd.app.WyrdApp)?.hapticManager
            ?: com.wyrd.app.util.HapticManager(context)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkCard)
                .pointerInput(message.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        val now = android.os.SystemClock.uptimeMillis()
                        val pos = down.position
                        val timeDiff = now - lastTapTime
                        val dist = kotlin.math.hypot(pos.x - lastTapPosition.x, pos.y - lastTapPosition.y)
                        if (timeDiff in 30..450 && dist < 120f) {
                            down.consume()
                            menuExpanded = true
                            hapticManager.click()
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            lastTapPosition = pos
                        }
                    }
                }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column {
                val attachments = remember(message.attachmentsJson, message.attachmentUri) {
                    message.getAttachmentList()
                }
                val imageAttachments = attachments.filter { it.type == "image" }
                val fileAttachments = attachments.filter { it.type != "image" }

                if (imageAttachments.isNotEmpty()) {
                    if (imageAttachments.size == 1) {
                        val img = imageAttachments[0]
                        AttachmentImageView(
                            uri = img.uri,
                            contentDescription = img.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(0.5.dp, DarkBorder, RoundedCornerShape(14.dp))
                                .clickable { openAttachmentUri(context, img.uri, isImage = true) }
                        )
                    } else if (imageAttachments.size == 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (img in imageAttachments) {
                                AttachmentImageView(
                                    uri = img.uri,
                                    contentDescription = img.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(0.5.dp, DarkBorder, RoundedCornerShape(12.dp))
                                        .clickable { openAttachmentUri(context, img.uri, isImage = true) }
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (img in imageAttachments) {
                                AttachmentImageView(
                                    uri = img.uri,
                                    contentDescription = img.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(0.5.dp, DarkBorder, RoundedCornerShape(12.dp))
                                        .clickable { openAttachmentUri(context, img.uri, isImage = true) }
                                )
                            }
                        }
                    }
                }

                if (imageAttachments.isNotEmpty() && fileAttachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (fileAttachments.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (fileItem in fileAttachments) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(0.8.dp, DarkBorder, RoundedCornerShape(12.dp))
                                    .clickable { openAttachmentUri(context, fileItem.uri, isImage = false) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkCard),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = TextWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileItem.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = SpaceGrotesk,
                                            color = TextWhite,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeText = if (fileItem.size > 0L) {
                                        AttachmentHelper.formatSize(fileItem.size)
                                    } else if (!fileItem.uri.startsWith("http")) {
                                        AttachmentHelper.getFormattedSizeFromPath(fileItem.uri)
                                    } else {
                                        ""
                                    }
                                    if (sizeText.isNotBlank()) {
                                        Text(
                                            text = sizeText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = SpaceGrotesk,
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (attachments.isNotEmpty() && message.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (message.content.isNotBlank()) {
                    SelectionContainer(
                        modifier = Modifier.suppressBringIntoView()
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = TextWhite,
                                fontFamily = SpaceGrotesk,
                                fontSize = 17.sp,
                                lineHeight = 23.sp
                            )
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier
                    .background(DarkCard)
                    .border(0.8.dp, DarkBorder, RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Copy",
                                color = TextWhite,
                                fontFamily = SpaceGrotesk,
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onCopy(message)
                    }
                )

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Edit",
                                color = TextWhite,
                                fontFamily = SpaceGrotesk,
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onEdit(message)
                    }
                )

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Branch into a new chat",
                                color = TextWhite,
                                fontFamily = SpaceGrotesk,
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onBranch(message)
                    }
                )

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Redo",
                                color = TextWhite,
                                fontFamily = SpaceGrotesk,
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onRedo(message)
                    }
                )
            }
        }
    }
}

@Composable
fun UserMessageBubble(
    content: String,
    modifier: Modifier = Modifier
) {
    UserMessageBubble(
        message = MessageEntity(chatId = 0, role = "user", content = content),
        modifier = modifier
    )
}

@Composable
fun ReasoningBlock(
    reasoning: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticManager = remember {
        (context.applicationContext as? com.wyrd.app.WyrdApp)?.hapticManager
            ?: com.wyrd.app.util.HapticManager(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                expanded = !expanded
                hapticManager.tick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = if (isStreaming) White else TextGray,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = SpaceGrotesk,
                        color = if (isStreaming) White else TextGray,
                        fontSize = 13.sp
                    )
                )
                if (isStreaming) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(White.copy(alpha = alpha))
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextGray,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(bottom = 8.dp))
                SelectionContainer(
                    modifier = Modifier.suppressBringIntoView()
                ) {
                    Text(
                        text = reasoning + (if (isStreaming) " ▌" else ""),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = SpaceGrotesk,
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }
}

data class FormattedReadingContent(
    val plainText: String,
    val baseAnnotatedString: AnnotatedString
)

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
        c.isNotEmpty() && c.matches(Regex("^:?-+:?$"))
    }
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
    val builder = AnnotatedString.Builder()
    val plainBuilder = StringBuilder()

    val lines = markdown.split("\n")
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

        // Tables: header line containing '|' and next line is table separator
        if (trimmed.contains("|") && lineIndex + 1 < lines.size && isTableSeparator(lines[lineIndex + 1])) {
            val headerCells = parseTableCells(line)
            val allRows = mutableListOf<List<String>>()
            allRows.add(headerCells)
            lineIndex += 2 // skip header and separator lines

            while (lineIndex < lines.size) {
                val nextLine = lines[lineIndex].trim()
                if (nextLine.isEmpty() || !nextLine.contains("|") || nextLine.startsWith("```") || nextLine.startsWith("#") || nextLine.startsWith(">")) {
                    break
                }
                val rowCells = parseTableCells(nextLine)
                allRows.add(rowCells)
                lineIndex++
            }

            val numCols = headerCells.size
            if (numCols > 0) {
                val colWidths = IntArray(numCols) { col ->
                    allRows.maxOfOrNull { row -> (row.getOrNull(col) ?: "").length } ?: 3
                }
                for (c in 0 until numCols) {
                    if (colWidths[c] < 3) colWidths[c] = 3
                }

                val sb = java.lang.StringBuilder()
                // Header
                sb.append("| ")
                headerCells.forEachIndexed { idx, cell ->
                    sb.append(cell.padEnd(colWidths[idx]))
                    if (idx < numCols - 1) sb.append(" | ") else sb.append(" |")
                }
                sb.append("\n")

                // Separator
                sb.append("|")
                for (idx in 0 until numCols) {
                    sb.append("-".repeat(colWidths[idx] + 2))
                    sb.append("|")
                }
                sb.append("\n")

                // Data rows
                for (r in 1 until allRows.size) {
                    val row = allRows[r]
                    sb.append("| ")
                    for (idx in 0 until numCols) {
                        val cell = row.getOrNull(idx) ?: ""
                        sb.append(cell.padEnd(colWidths[idx]))
                        if (idx < numCols - 1) sb.append(" | ") else sb.append(" |")
                    }
                    sb.append("\n")
                }

                val tableText = sb.toString().trimEnd('\n')
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBg,
                        color = textColor,
                        fontSize = 13.sp
                    )
                )
                builder.append(tableText)
                builder.pop()
                plainBuilder.append(tableText)

                if (lineIndex < lines.size) {
                    builder.append("\n\n")
                    plainBuilder.append("\n\n")
                }
            }
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
                builder.append("\n")
                plainBuilder.append("\n")
            }
            lineIndex++
            continue
        }

        // Ordered List
        val numMatch = Regex("^(\\d+)\\.\\s+(.*)$").matchEntire(trimmed)
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

        // Normal character
        builder.append(text[cursor])
        plainBuilder.append(text[cursor])
        cursor++
    }
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
        // Monochromatic inverted text highlight pill (preserves underlying font metrics so text never shifts)
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

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.suppressBringIntoView(): Modifier = this.bringIntoViewResponder(
    object : BringIntoViewResponder {
        override fun calculateRectForParent(localRect: Rect): Rect = localRect
        override suspend fun bringChildIntoView(localRect: () -> Rect?) {
            // No-op: prevent LazyColumn from snapping or scrolling when selecting text
        }
    }
)

@Composable
fun AssistantMessageItem(
    message: MessageEntity,
    ttsPlaybackState: TtsPlaybackState,
    onCopy: () -> Unit,
    onSeek: (plainText: String, charOffset: Int) -> Unit,
    onActiveWordPosition: ((line: Int, wordTopInItem: Float, wordBottomInItem: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isCurrentMessage = ttsPlaybackState.messageId == message.id
    val activeHighlightRange = if (isCurrentMessage) ttsPlaybackState.currentRange else null

    val wyrdColors = LocalWyrdColors.current
    val currentTextPrimary = wyrdColors.textPrimary
    val currentSurfaceElevated = wyrdColors.surfaceElevated
    val currentBorder = wyrdColors.border
    val currentTextSecondary = wyrdColors.textSecondary
    val currentBackground = wyrdColors.background

    val formatted = remember(message.content, wyrdColors) {
        formatMarkdownForReading(
            markdown = message.content,
            textColor = currentTextPrimary,
            headerColor = currentTextPrimary,
            inlineCodeBg = currentSurfaceElevated,
            inlineCodeColor = currentTextPrimary,
            boldColor = currentTextPrimary,
            italicColor = currentTextPrimary,
            quoteColor = currentTextSecondary,
            bulletColor = currentTextSecondary
        )
    }

    val annotatedText = remember(formatted, activeHighlightRange, currentTextPrimary, currentBackground) {
        getHighlightedAnnotatedString(
            formatted = formatted,
            highlightRange = activeHighlightRange,
            highlightBg = currentTextPrimary,
            highlightTextColor = currentBackground
        )
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textTopInItem by remember { mutableStateOf(0f) }

    LaunchedEffect(activeHighlightRange, textLayoutResult, isCurrentMessage) {
        if (!isCurrentMessage || activeHighlightRange == null) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val (start, _) = activeHighlightRange
        val safeOffset = start.coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
        val line = layout.getLineForOffset(safeOffset)
        val lineTop = layout.getLineTop(line) + textTopInItem
        val lineBottom = layout.getLineBottom(line) + textTopInItem
        onActiveWordPosition?.invoke(line, lineTop, lineBottom)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Collapsed-by-default reasoning block
        if (!message.reasoning.isNullOrBlank()) {
            ReasoningBlock(reasoning = message.reasoning)
        }

        // Synchronized ElevenReader style reading & Tap-to-seek
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    textTopInItem = coordinates.positionInParent().y
                }
                .pointerInput(message.id, formatted.plainText) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        val up = withTimeoutOrNull(longPressTimeout) {
                            waitForUpOrCancellation()
                        }
                        if (up != null && !up.isConsumed) {
                            val layout = textLayoutResult ?: return@awaitEachGesture
                            val charOffset = layout.getOffsetForPosition(up.position)
                            onSeek(formatted.plainText, charOffset)
                        }
                    }
                }
        ) {
            SelectionContainer(
                modifier = Modifier.suppressBringIntoView()
            ) {
                Text(
                    text = annotatedText,
                    onTextLayout = { textLayoutResult = it },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = currentTextPrimary,
                        fontFamily = SpaceGrotesk,
                        fontSize = 17.sp,
                        lineHeight = 23.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Enlarged Copy Button (48dp target, 22dp icon)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(currentSurfaceElevated)
                    .border(0.8.dp, currentBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy message",
                        tint = currentTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StreamingAssistantMessageItem(
    streamingContent: String,
    streamingReasoning: String = "",
    streamingStatus: String? = null,
    ttsPlaybackState: TtsPlaybackState? = null,
    modifier: Modifier = Modifier
) {
    val wyrdColors = LocalWyrdColors.current
    val currentTextPrimary = wyrdColors.textPrimary
    val currentSurfaceElevated = wyrdColors.surfaceElevated
    val currentTextSecondary = wyrdColors.textSecondary
    val currentBackground = wyrdColors.background

    val isSpeakingThis = ttsPlaybackState?.isPlaying == true && ttsPlaybackState.messageId == NativeTtsManager.STREAMING_MESSAGE_ID
    val activeHighlightRange = if (isSpeakingThis) ttsPlaybackState?.currentRange else null

    val formatted = remember(streamingContent, wyrdColors) {
        if (streamingContent.isNotEmpty()) {
            formatMarkdownForReading(
                markdown = streamingContent,
                textColor = currentTextPrimary,
                headerColor = currentTextPrimary,
                inlineCodeBg = currentSurfaceElevated,
                inlineCodeColor = currentTextPrimary,
                boldColor = currentTextPrimary,
                italicColor = currentTextPrimary,
                quoteColor = currentTextSecondary,
                bulletColor = currentTextSecondary
            )
        } else null
    }

    val annotatedText = remember(formatted, activeHighlightRange, currentTextPrimary, currentBackground) {
        if (formatted != null) {
            getHighlightedAnnotatedString(
                formatted = formatted,
                highlightRange = activeHighlightRange,
                highlightBg = currentTextPrimary,
                highlightTextColor = currentBackground
            )
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (streamingReasoning.isNotBlank()) {
            ReasoningBlock(
                reasoning = streamingReasoning,
                isStreaming = streamingContent.isEmpty()
            )
        }

        if (!streamingStatus.isNullOrBlank() && streamingContent.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = streamingStatus,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SpaceGrotesk,
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                )
            }
        }

        if (annotatedText != null) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer(
                    modifier = Modifier.suppressBringIntoView()
                ) {
                    androidx.compose.material3.Text(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = currentTextPrimary,
                            fontFamily = SpaceGrotesk,
                            fontSize = 17.sp,
                            lineHeight = 23.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
