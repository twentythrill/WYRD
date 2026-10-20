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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import com.wyrd.app.data.local.MessageEntity
import com.wyrd.app.data.preferences.SupportedModel
import com.wyrd.app.data.tts.NativeTtsManager
import com.wyrd.app.data.tts.TtsPlaybackState
import com.wyrd.app.ui.theme.AccentGreen
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.LocalWyrdColors
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextGray
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White
import com.wyrd.app.ui.theme.WyrdIcons
import com.wyrd.app.util.AttachmentHelper
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
    var touchPoint by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

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
                            touchPoint = pos
                            menuExpanded = true
                            hapticManager.click()
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            lastTapPosition = pos
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
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
                                        imageVector = WyrdIcons.DescriptionOutlined,
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

            Box(
                modifier = Modifier
                    .offset { IntOffset(touchPoint.x.roundToInt(), touchPoint.y.roundToInt()) }
                    .size(1.dp)
            ) {
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
                                    imageVector = WyrdIcons.ContentCopy,
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
                                    imageVector = WyrdIcons.CallSplit,
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
                    imageVector = WyrdIcons.Lightbulb,
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
                            imageVector = WyrdIcons.ContentCopy,
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

    val tableTouchState = LocalTableTouchState.current

    BoxWithConstraints(
        modifier = modifier
            // Marks the whole table card as a horizontal-pan zone for the duration of any gesture
            // that starts inside it, so the global pull-to-record swipe stands down and lets the
            // table be panned without ever triggering STT.
            .pointerInput(tableTouchState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    tableTouchState.isTouchingTable = true
                    try {
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.id == down.id && it.pressed }) break
                        } while (true)
                    } finally {
                        tableTouchState.isTouchingTable = false
                    }
                }
            }
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

    val context = androidx.compose.ui.platform.LocalContext.current

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
                        val urlAnnotation = block.formatted.baseAnnotatedString
                            .getStringAnnotations(tag = "URL", start = localOffset, end = localOffset)
                            .firstOrNull()
                        if (urlAnnotation != null) {
                            openAttachmentUri(context, urlAnnotation.item, isImage = false)
                        } else {
                            val globalOffset = blockStart + localOffset
                            onSeek(globalOffset)
                        }
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
                                val urlAnnotation = formatted.baseAnnotatedString
                                    .getStringAnnotations(tag = "URL", start = charOffset, end = charOffset)
                                    .firstOrNull()
                                if (urlAnnotation != null) {
                                    openAttachmentUri(context, urlAnnotation.item, isImage = false)
                                } else {
                                    onSeek(formatted.plainText, charOffset)
                                }
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

        Spacer(modifier = Modifier.height(6.dp))

        // Model / reasoning provenance caption
        val metaParts = buildList {
            message.modelName?.let { id ->
                add(SupportedModel.entries.find { it.id == id }?.shortName ?: id)
            }
            message.reasoningEnabled?.let { enabled ->
                add(if (enabled) "Reasoning on" else "Reasoning off")
            }
        }
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = currentTextSecondary,
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.sp
                )
            )
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
                        imageVector = WyrdIcons.ContentCopy,
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
