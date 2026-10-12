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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.wyrd.app.ui.theme.AccentGreen
import com.wyrd.app.ui.theme.Black
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.delay
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
import androidx.compose.ui.text.style.TextAlign
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
    textColor: Color,
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
            textColor = textColor,
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

                // Append readable text version to overallAnnotatedBuilder for fallback
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

    // If blocks is empty (e.g. empty string), provide 1 empty text block
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

@Composable
fun CodeBlockCard(
    codeData: MarkdownCodeData,
    activeHighlightRange: Pair<Int, Int>? = null,
    onSeek: ((charOffset: Int) -> Unit)? = null,
    onActiveWordPosition: ((line: Int, wordTopInItem: Float, wordBottomInItem: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val wyrdColors = LocalWyrdColors.current
    val currentBorder = wyrdColors.border
    val currentSurface = wyrdColors.surface
    val currentSurfaceElevated = wyrdColors.surfaceElevated
    val currentTextPrimary = wyrdColors.textPrimary
    val currentTextSecondary = wyrdColors.textSecondary
    val currentBackground = wyrdColors.background

    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticManager = remember {
        (context.applicationContext as? com.wyrd.app.WyrdApp)?.hapticManager
            ?: com.wyrd.app.util.HapticManager(context)
    }
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val displayLang = codeData.language.ifBlank { "code" }

    val localHighlightRange = remember(activeHighlightRange, codeData.startOffset, codeData.endOffset) {
        if (activeHighlightRange != null) {
            val (hs, he) = activeHighlightRange
            val overlapStart = maxOf(hs, codeData.startOffset)
            val overlapEnd = minOf(he, codeData.endOffset)
            if (overlapStart < overlapEnd) {
                Pair(overlapStart - codeData.startOffset, overlapEnd - codeData.startOffset)
            } else null
        } else null
    }

    val codeAnnotated = remember(codeData.rawCode, localHighlightRange, currentTextPrimary, currentBackground) {
        val raw = codeData.rawCode
        if (localHighlightRange != null) {
            val (start, end) = localHighlightRange
            val safeStart = start.coerceIn(0, raw.length)
            val safeEnd = end.coerceIn(safeStart, raw.length)
            buildAnnotatedString {
                append(raw)
                if (safeStart < safeEnd) {
                    addStyle(
                        SpanStyle(
                            background = currentTextPrimary,
                            color = currentBackground
                        ),
                        start = safeStart,
                        end = safeEnd
                    )
                }
            }
        } else {
            AnnotatedString(raw)
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var blockTopInParent by remember { mutableStateOf(0f) }

    LaunchedEffect(localHighlightRange, textLayoutResult) {
        if (localHighlightRange == null) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val (start, _) = localHighlightRange
        val safeOffset = start.coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
        val line = layout.getLineForOffset(safeOffset)
        val lineTop = layout.getLineTop(line) + blockTopInParent
        val lineBottom = layout.getLineBottom(line) + blockTopInParent
        onActiveWordPosition?.invoke(line, lineTop, lineBottom)
    }

    Box(
        modifier = modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(currentSurface)
            .border(1.dp, currentBorder, RoundedCornerShape(12.dp))
            .onGloballyPositioned { coordinates ->
                blockTopInParent = coordinates.positionInParent().y
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentSurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayLang,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = currentTextSecondary
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            clipboardManager.setText(AnnotatedString(codeData.rawCode))
                            copied = true
                            hapticManager.click()
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (copied) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Copied",
                            tint = AccentGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Copied",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentGreen
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy code",
                            tint = currentTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Copy",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = currentTextSecondary
                        )
                    }
                }
            }

            HorizontalDivider(
                color = currentBorder,
                thickness = 0.8.dp
            )

            // Code Content Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .then(
                        if (onSeek != null) {
                            Modifier.pointerInput(codeData.startOffset, codeData.rawCode) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                                    val up = withTimeoutOrNull(longPressTimeout) {
                                        waitForUpOrCancellation()
                                    }
                                    if (up != null && !up.isConsumed) {
                                        val layout = textLayoutResult ?: return@awaitEachGesture
                                        val localOffset = layout.getOffsetForPosition(up.position)
                                        val globalOffset = codeData.startOffset + localOffset
                                        onSeek(globalOffset)
                                    }
                                }
                            }
                        } else Modifier
                    )
            ) {
                SelectionContainer(
                    modifier = Modifier.suppressBringIntoView()
                ) {
                    Text(
                        text = codeAnnotated,
                        onTextLayout = { textLayoutResult = it },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 17.sp,
                            lineHeight = 23.sp,
                            color = currentTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownTableCard(
    table: MarkdownTableData,
    activeHighlightRange: Pair<Int, Int>? = null,
    onSeek: ((charOffset: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val wyrdColors = LocalWyrdColors.current
    val currentBorder = wyrdColors.border
    val currentSurfaceElevated = wyrdColors.surfaceElevated
    val currentTextPrimary = wyrdColors.textPrimary
    val currentBackground = wyrdColors.background

    val numCols = table.headers.size
    if (numCols == 0) return

    BoxWithConstraints(
        modifier = modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(currentSurfaceElevated)
            .border(1.dp, currentBorder, RoundedCornerShape(12.dp))
    ) {
        val availableWidth = maxWidth
        val colWidths = remember(table, availableWidth) {
            val availDp = availableWidth.value
            val numCols = table.headers.size

            val colMaxChars = table.headers.indices.map { c ->
                maxOf(
                    table.headers.getOrNull(c)?.plainText?.length ?: 0,
                    table.rows.maxOfOrNull { it.cells.getOrNull(c)?.plainText?.length ?: 0 } ?: 0
                )
            }

            if (numCols == 1) {
                listOf(availableWidth)
            } else if (numCols == 2) {
                val c0Chars = colMaxChars.getOrElse(0) { 0 }
                val c1Chars = colMaxChars.getOrElse(1) { 0 }

                // If both columns have long paragraphs (> 50 chars), make both comfortable and allow horizontal scroll
                if (c0Chars > 50 && c1Chars > 50) {
                    listOf(220.dp, 220.dp)
                } else {
                    // Col 0 is label/flag/key, Col 1 is evidence/description/value
                    val c0Width = when {
                        c0Chars <= 8 -> 80f
                        c0Chars <= 16 -> 100f
                        c0Chars <= 30 -> 120f
                        else -> 135f
                    }
                    val remaining = (availDp - c0Width).coerceAtLeast(160f)
                    listOf(c0Width.dp, remaining.dp)
                }
            } else {
                // 3 or more columns
                val baseWidths = colMaxChars.map { maxChars ->
                    when {
                        maxChars <= 8 -> 75f
                        maxChars <= 16 -> 105f
                        maxChars <= 30 -> 145f
                        maxChars <= 60 -> 190f
                        else -> 240f
                    }
                }
                val totalBase = baseWidths.sum()
                val scale = if (totalBase < availDp && totalBase > 0f) (availDp / totalBase) else 1f
                baseWidths.map { (it * scale).dp }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .background(if (wyrdColors.isDark) Color(0xFF1A1A1A) else Color(0xFFDFDED8))
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    table.headers.forEachIndexed { c, headerCell ->
                        val align = table.alignments.getOrNull(c) ?: TableColumnAlignment.LEFT
                        val width = colWidths.getOrElse(c) { 140.dp }

                        val cellHighlightRange = if (activeHighlightRange != null) {
                            val (hs, he) = activeHighlightRange
                            val relStart = hs - headerCell.startOffset
                            val relEnd = he - headerCell.startOffset
                            if (relEnd > 0 && relStart < headerCell.plainText.length) {
                                Pair(relStart.coerceAtLeast(0), relEnd.coerceAtMost(headerCell.plainText.length))
                            } else null
                        } else null

                        val headerAnnotated = remember(headerCell, cellHighlightRange, currentTextPrimary, currentBackground) {
                            if (cellHighlightRange != null) {
                                buildAnnotatedString {
                                    append(headerCell.annotatedText)
                                    addStyle(
                                        SpanStyle(background = currentTextPrimary, color = currentBackground),
                                        start = cellHighlightRange.first,
                                        end = cellHighlightRange.second
                                    )
                                }
                            } else {
                                headerCell.annotatedText
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(width)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .then(
                                    if (onSeek != null) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            onSeek(headerCell.startOffset)
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = when (align) {
                                TableColumnAlignment.RIGHT -> Alignment.CenterEnd
                                TableColumnAlignment.CENTER -> Alignment.Center
                                TableColumnAlignment.LEFT -> Alignment.CenterStart
                            }
                        ) {
                            Text(
                                text = headerAnnotated,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentTextPrimary,
                                    letterSpacing = 0.5.sp
                                ),
                                textAlign = when (align) {
                                    TableColumnAlignment.RIGHT -> TextAlign.End
                                    TableColumnAlignment.CENTER -> TextAlign.Center
                                    TableColumnAlignment.LEFT -> TextAlign.Start
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = currentBorder,
                    thickness = 1.dp
                )

                // Table Data Rows
                table.rows.forEachIndexed { rIdx, row ->
                    val isLastRow = rIdx == table.rows.size - 1
                    val rowBg = if (rIdx % 2 == 1) {
                        if (wyrdColors.isDark) Color(0xFF101010) else Color(0xFFE5E4DD)
                    } else {
                        Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        for (c in 0 until numCols) {
                            val cell = row.cells.getOrNull(c)
                            val align = table.alignments.getOrNull(c) ?: TableColumnAlignment.LEFT
                            val width = colWidths.getOrElse(c) { 140.dp }

                            if (cell != null) {
                                val cellHighlightRange = if (activeHighlightRange != null) {
                                    val (hs, he) = activeHighlightRange
                                    val relStart = hs - cell.startOffset
                                    val relEnd = he - cell.startOffset
                                    if (relEnd > 0 && relStart < cell.plainText.length) {
                                        Pair(relStart.coerceAtLeast(0), relEnd.coerceAtMost(cell.plainText.length))
                                    } else null
                                } else null

                                val cellAnnotated = remember(cell, cellHighlightRange, currentTextPrimary, currentBackground) {
                                    if (cellHighlightRange != null) {
                                        buildAnnotatedString {
                                            append(cell.annotatedText)
                                            addStyle(
                                                SpanStyle(background = currentTextPrimary, color = currentBackground),
                                                start = cellHighlightRange.first,
                                                end = cellHighlightRange.second
                                            )
                                        }
                                    } else {
                                        cell.annotatedText
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(width)
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                        .then(
                                            if (onSeek != null) {
                                                Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    onSeek(cell.startOffset)
                                                }
                                            } else Modifier
                                        ),
                                    contentAlignment = when (align) {
                                        TableColumnAlignment.RIGHT -> Alignment.TopEnd
                                        TableColumnAlignment.CENTER -> Alignment.TopCenter
                                        TableColumnAlignment.LEFT -> Alignment.TopStart
                                    }
                                ) {
                                    Text(
                                        text = cellAnnotated,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            color = currentTextPrimary
                                        ),
                                        textAlign = when (align) {
                                            TableColumnAlignment.RIGHT -> TextAlign.End
                                            TableColumnAlignment.CENTER -> TextAlign.Center
                                            TableColumnAlignment.LEFT -> TextAlign.Start
                                        }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(width)
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }

                    if (!isLastRow) {
                        HorizontalDivider(
                            color = currentBorder.copy(alpha = 0.5f),
                            thickness = 0.8.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextBlockItem(
    block: MessageContentBlock.Text,
    activeHighlightRange: Pair<Int, Int>?,
    textColor: Color,
    onSeek: (charOffset: Int) -> Unit,
    onActiveWordPosition: ((line: Int, wordTopInItem: Float, wordBottomInItem: Float) -> Unit)? = null
) {
    val wyrdColors = LocalWyrdColors.current
    val currentTextPrimary = wyrdColors.textPrimary
    val currentBackground = wyrdColors.background

    val blockStart = block.startOffset
    val blockEnd = blockStart + block.formatted.baseAnnotatedString.length

    val localHighlightRange = remember(activeHighlightRange, blockStart, blockEnd) {
        if (activeHighlightRange != null) {
            val (hs, he) = activeHighlightRange
            val overlapStart = maxOf(hs, blockStart)
            val overlapEnd = minOf(he, blockEnd)
            if (overlapStart < overlapEnd) {
                Pair(overlapStart - blockStart, overlapEnd - blockStart)
            } else null
        } else null
    }

    val blockAnnotatedText = remember(block.formatted, localHighlightRange, currentTextPrimary, currentBackground) {
        getHighlightedAnnotatedString(
            formatted = block.formatted,
            highlightRange = localHighlightRange,
            highlightBg = currentTextPrimary,
            highlightTextColor = currentBackground
        )
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var blockTopInParent by remember { mutableStateOf(0f) }

    LaunchedEffect(localHighlightRange, textLayoutResult) {
        if (localHighlightRange == null) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val (start, _) = localHighlightRange
        val safeOffset = start.coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
        val line = layout.getLineForOffset(safeOffset)
        val lineTop = layout.getLineTop(line) + blockTopInParent
        val lineBottom = layout.getLineBottom(line) + blockTopInParent
        onActiveWordPosition?.invoke(line, lineTop, lineBottom)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                blockTopInParent = coordinates.positionInParent().y
            }
            .pointerInput(blockStart, block.formatted.plainText) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val up = withTimeoutOrNull(longPressTimeout) {
                        waitForUpOrCancellation()
                    }
                    if (up != null && !up.isConsumed) {
                        val layout = textLayoutResult ?: return@awaitEachGesture
                        val localOffset = layout.getOffsetForPosition(up.position)
                        val globalOffset = blockStart + localOffset
                        onSeek(globalOffset)
                    }
                }
            }
    ) {
        Text(
            text = blockAnnotatedText,
            onTextLayout = { textLayoutResult = it },
            style = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                fontFamily = SpaceGrotesk,
                fontSize = 17.sp,
                lineHeight = 23.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
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

    val hasComplexBlocks = remember(formatted) {
        formatted.blocks.any { it !is MessageContentBlock.Text }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textTopInItem by remember { mutableStateOf(0f) }

    LaunchedEffect(activeHighlightRange, textLayoutResult, isCurrentMessage) {
        if (!isCurrentMessage || activeHighlightRange == null || hasComplexBlocks) return@LaunchedEffect
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
        if (!hasComplexBlocks) {
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
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        textTopInItem = coordinates.positionInParent().y
                    }
            ) {
                SelectionContainer(
                    modifier = Modifier.suppressBringIntoView()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        formatted.blocks.forEach { block ->
                            when (block) {
                                is MessageContentBlock.Text -> {
                                    TextBlockItem(
                                        block = block,
                                        activeHighlightRange = activeHighlightRange,
                                        textColor = currentTextPrimary,
                                        onSeek = { charOffset -> onSeek(formatted.plainText, charOffset) },
                                        onActiveWordPosition = if (isCurrentMessage) { line, top, bottom ->
                                            onActiveWordPosition?.invoke(line, top + textTopInItem, bottom + textTopInItem)
                                        } else null
                                    )
                                }
                                is MessageContentBlock.Table -> {
                                    MarkdownTableCard(
                                        table = block.table,
                                        activeHighlightRange = activeHighlightRange,
                                        onSeek = { charOffset -> onSeek(formatted.plainText, charOffset) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is MessageContentBlock.Code -> {
                                    CodeBlockCard(
                                        codeData = block.code,
                                        activeHighlightRange = activeHighlightRange,
                                        onSeek = { charOffset -> onSeek(formatted.plainText, charOffset) },
                                        onActiveWordPosition = if (isCurrentMessage) { line, top, bottom ->
                                            onActiveWordPosition?.invoke(line, top + textTopInItem, bottom + textTopInItem)
                                        } else null,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
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

    val hasComplexBlocks = remember(formatted) {
        formatted?.blocks?.any { it !is MessageContentBlock.Text } == true
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

        if (!hasComplexBlocks) {
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
        } else {
            if (formatted != null) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer(
                        modifier = Modifier.suppressBringIntoView()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            formatted.blocks.forEach { block ->
                                when (block) {
                                    is MessageContentBlock.Text -> {
                                        val blockStart = block.startOffset
                                        val blockEnd = blockStart + block.formatted.baseAnnotatedString.length
                                        val localHighlightRange = if (activeHighlightRange != null) {
                                            val (hs, he) = activeHighlightRange
                                            val overlapStart = maxOf(hs, blockStart)
                                            val overlapEnd = minOf(he, blockEnd)
                                            if (overlapStart < overlapEnd) Pair(overlapStart - blockStart, overlapEnd - blockStart) else null
                                        } else null

                                        val blockAnnotated = remember(block.formatted, localHighlightRange, currentTextPrimary, currentBackground) {
                                            getHighlightedAnnotatedString(
                                                formatted = block.formatted,
                                                highlightRange = localHighlightRange,
                                                highlightBg = currentTextPrimary,
                                                highlightTextColor = currentBackground
                                            )
                                        }

                                        androidx.compose.material3.Text(
                                            text = blockAnnotated,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = currentTextPrimary,
                                                fontFamily = SpaceGrotesk,
                                                fontSize = 17.sp,
                                                lineHeight = 23.sp
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    is MessageContentBlock.Table -> {
                                        MarkdownTableCard(
                                            table = block.table,
                                            activeHighlightRange = activeHighlightRange,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    is MessageContentBlock.Code -> {
                                        CodeBlockCard(
                                            codeData = block.code,
                                            activeHighlightRange = activeHighlightRange,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
