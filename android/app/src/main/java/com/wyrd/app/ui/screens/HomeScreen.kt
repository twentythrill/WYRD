package com.wyrd.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wyrd.app.data.tts.TtsPlaybackState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.core.content.ContextCompat
import com.wyrd.app.R
import com.wyrd.app.ui.MainViewModel
import com.wyrd.app.ui.components.AssistantMessageItem
import com.wyrd.app.ui.components.AttachmentPopupMenu
import com.wyrd.app.ui.components.BigSttOverlay
import com.wyrd.app.ui.components.ChatInputBubble
import com.wyrd.app.util.AttachedItem
import com.wyrd.app.util.AttachmentHelper
import com.wyrd.app.ui.components.DrawerContent
import com.wyrd.app.ui.components.SpringLoadedPullIndicator
import com.wyrd.app.ui.components.StreamingAssistantMessageItem
import com.wyrd.app.ui.components.formatMarkdownForReading
import com.wyrd.app.data.local.ProjectEntity
import com.wyrd.app.ui.components.ProjectDialog
import com.wyrd.app.ui.components.TopBar
import com.wyrd.app.ui.components.UserMessageBubble
import kotlin.math.roundToInt
import com.wyrd.app.ui.theme.AccentRed
import com.wyrd.app.ui.theme.Black
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurface
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextGray
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White
import com.wyrd.app.ui.theme.isAppThemeDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val activeChatId by viewModel.activeChatId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val streamingReasoning by viewModel.streamingReasoning.collectAsState()
    val streamingStatus by viewModel.streamingStatus.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val reasoningEffort by viewModel.reasoningEffort.collectAsState()
    val username by viewModel.username.collectAsState()
    val promptText by viewModel.inputPrompt.collectAsState()
    val isRecording by viewModel.isRecordingAudio.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val ttsPlaybackState by viewModel.ttsPlaybackState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()

    val chatList by viewModel.chatList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val projectList by viewModel.projectList.collectAsState()
    val activeProjectId by viewModel.activeProjectId.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    var showProjectDialog by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<ProjectEntity?>(null) }

    val listState = rememberLazyListState()
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledUp by remember { mutableStateOf(false) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var lastTtsScrollLine by remember { mutableStateOf(-1) }

    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val isImeOpen = imeBottom > 0
    var prevImeBottom by remember { mutableStateOf(0) }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            viewModel.refreshSync()
        }
    }

    val isScrolledUpFromBottom by remember {
        derivedStateOf {
            val targetIndex = messages.size + if (isGenerating) 1 else 0
            if (targetIndex == 0) return@derivedStateOf false
            if (!listState.canScrollForward) return@derivedStateOf false

            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf false

            val lastVisible = visibleItems.last()
            if (lastVisible.index < targetIndex) {
                true
            } else {
                val viewportBottom = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
                val itemBottom = lastVisible.offset + lastVisible.size
                (itemBottom - viewportBottom) > 20
            }
        }
    }
    val showScrollToBottom = isScrolledUpFromBottom && !isImeOpen

    // Reset userScrolledUp when generation starts or chat switches
    LaunchedEffect(isGenerating, activeChatId) {
        if (isGenerating || activeChatId != null) {
            userScrolledUp = false
        }
    }

    // Reset TTS tracking line when playback stops
    LaunchedEffect(ttsPlaybackState.isPlaying, ttsPlaybackState.messageId) {
        if (!ttsPlaybackState.isPlaying) {
            lastTtsScrollLine = -1
        }
    }

    // Monitor manual scroll gestures: if user scrolls or flings away from the bottom, pause auto-scroll.
    // Only user-initiated dragging sets userScrolledUp = true, ignoring programmatic layout jumps.
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.isScrollInProgress, listState.canScrollForward) }
            .collect { (inProgress, canScrollForward) ->
                if (inProgress) {
                    if (!isProgrammaticScroll && isListDragged) {
                        if (canScrollForward) {
                            userScrolledUp = true
                        } else {
                            userScrolledUp = false
                        }
                    }
                } else {
                    if (!canScrollForward) {
                        userScrolledUp = false
                    }
                }
            }
    }

    // Initial chat load: when navigating to a chat with existing history, position at the true bottom once.
    var lastLoadedChatId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(activeChatId, messages) {
        if (activeChatId == null) {
            lastLoadedChatId = null
        } else {
            // Verify that messages actually belong to activeChatId (avoid executing prematurely on stale messages)
            val belongsToCurrentChat = messages.isNotEmpty() && messages.first().chatId == activeChatId
            if (belongsToCurrentChat && activeChatId != lastLoadedChatId) {
                lastLoadedChatId = activeChatId
                userScrolledUp = false
                isProgrammaticScroll = true
                val targetIndex = messages.size + if (isGenerating) 1 else 0

                // Multi-stage positioning to guarantee 100% bottom alignment as text/markdown finishes measuring:
                listState.scrollToItem(targetIndex, 0)
                kotlinx.coroutines.delay(40)
                listState.scrollToItem(targetIndex, 0)
                kotlinx.coroutines.delay(120)
                listState.scrollToItem(targetIndex, 0)
                kotlinx.coroutines.delay(250)
                listState.scrollToItem(targetIndex, 0)
                isProgrammaticScroll = false
            }
        }
    }

    // Auto-scroll to bottom ONLY when user submits a new prompt so the user's message is in view.
    // Strictly do NOT autoscroll while the answer is streaming or completing, preserving reading flow.
    LaunchedEffect(messages.size) {
        val totalCount = messages.size
        if (totalCount > 0 && !userScrolledUp && !isListDragged && !ttsPlaybackState.isPlaying) {
            val lastMsg = messages.lastOrNull()
            if (lastMsg?.role == "user") {
                val targetIndex = totalCount + if (isGenerating) 1 else 0
                listState.animateScrollToItem(targetIndex, 0)
            }
        }
    }

    // Auto-scroll to bottom when keyboard opens in an existing chat so messages are visible above capsule
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && prevImeBottom == 0) {
            userScrolledUp = false
        }
        prevImeBottom = imeBottom

        if (imeBottom > 0 && messages.isNotEmpty() && !userScrolledUp) {
            val targetIndex = messages.size + if (isGenerating) 1 else 0
            listState.scrollToItem(targetIndex, 0)
        }
    }

    // Audio recording permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleRecording {}
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    val triggerRecordingAction = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.toggleRecording {}
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val density = LocalDensity.current
    val thresholdPx = with(density) { 95.dp.toPx() }
    val pullDistanceAnim = remember { Animatable(0f) }
    var isSpringEngaged by remember { mutableStateOf(false) }
    val contentShift = -(pullDistanceAnim.value * 0.16f).coerceAtMost(with(density) { 28.dp.toPx() })

    // Auto-focus text box on app open so system keyboard appears
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Collapse keyboard whenever the drawer is opening or opened
    LaunchedEffect(drawerState.isOpen, drawerState.targetValue) {
        if (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // Collapse keyboard whenever STT is recording or transcribing
    LaunchedEffect(isRecording, isTranscribing) {
        if (isRecording || isTranscribing) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // Collapse keyboard when LLM begins generating or spits out text answer
    LaunchedEffect(isGenerating, streamingContent.isNotEmpty(), streamingReasoning.isNotEmpty()) {
        if (isGenerating || streamingContent.isNotEmpty() || streamingReasoning.isNotEmpty()) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // Collapse keyboard only when user manually drags message history
    LaunchedEffect(isListDragged) {
        if (isListDragged) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // Auto-focus text box when entering edit mode
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val currentAttachments by viewModel.currentAttachments.collectAsState()
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val items = AttachmentHelper.processUris(context, uris)
                if (items.isNotEmpty()) {
                    viewModel.addAttachments(items)
                } else {
                    Toast.makeText(context, "Could not process selected image(s)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val items = AttachmentHelper.processUris(context, uris)
                if (items.isNotEmpty()) {
                    viewModel.addAttachments(items)
                } else {
                    Toast.makeText(context, "Could not process selected file(s)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            val uri = pendingCameraUri
            if (uri != null) {
                coroutineScope.launch {
                    val item = AttachmentHelper.processUri(context, uri)
                    if (item != null) {
                        viewModel.addAttachments(listOf(item))
                    } else {
                        Toast.makeText(context, "Could not process photo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var isAttachmentMenuOpen by remember { mutableStateOf(false) }

    // Native Back Button Handling:
    // 1. If attachment menu is open -> Back smoothly closes it
    BackHandler(enabled = isAttachmentMenuOpen) {
        viewModel.hapticManager.click()
        isAttachmentMenuOpen = false
    }

    // 2. If editing prompt -> Back cancels editing
    BackHandler(enabled = editingMessage != null) {
        viewModel.cancelEditingMessage()
    }

    // 3. If STT recording or transcribing overlay is active -> Back cancels STT
    BackHandler(enabled = isRecording || isTranscribing) {
        viewModel.hapticManager.click()
        viewModel.cancelRecording()
    }

    // 4. If navigation drawer is open -> Back smoothly closes the drawer
    BackHandler(enabled = drawerState.isOpen) {
        viewModel.hapticManager.click()
        coroutineScope.launch { drawerState.close() }
    }

    // Close attachment menu if drawer opens or active chat changes
    LaunchedEffect(drawerState.isOpen, activeChatId) {
        if (drawerState.isOpen) {
            isAttachmentMenuOpen = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = DarkSurface,
                    modifier = Modifier.width(310.dp)
                ) {
                    DrawerContent(
                        chatList = chatList,
                        projectList = projectList,
                        activeChatId = activeChatId,
                        activeProjectId = activeProjectId,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onNewChat = {
                            viewModel.startNewChat()
                            coroutineScope.launch { drawerState.close() }
                        },
                        onNewChatInProject = { projId ->
                            viewModel.startNewChatInProject(projId)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onCreateProject = {
                            projectToEdit = null
                            showProjectDialog = true
                            coroutineScope.launch { drawerState.close() }
                        },
                        onEditProject = { proj ->
                            projectToEdit = proj
                            showProjectDialog = true
                            coroutineScope.launch { drawerState.close() }
                        },
                        onChatSelected = { id ->
                            if (activeChatId == id) {
                                coroutineScope.launch {
                                    userScrolledUp = false
                                    val targetIndex = messages.size + if (isGenerating) 1 else 0
                                    listState.scrollToItem(targetIndex, 0)
                                }
                            } else {
                                lastLoadedChatId = null
                                viewModel.selectChat(id)
                            }
                            coroutineScope.launch { drawerState.close() }
                        },
                        onTogglePinChat = { viewModel.togglePinChat(it) },
                        onRenameChat = { chat, newTitle -> viewModel.renameChat(chat, newTitle) },
                        onDeleteChat = { viewModel.deleteChat(it) },
                        onNavigateSettings = {
                            viewModel.hapticManager.click()
                            viewModel.stopTts()
                            coroutineScope.launch { drawerState.close() }
                            onNavigateSettings()
                        }
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = Black,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = contentShift.roundToInt(), y = 0) }
                    .pointerInput(drawerState.isClosed, isRecording, isTranscribing) {
                        if (!drawerState.isClosed || isRecording || isTranscribing) return@pointerInput
                        val edgeSafeZonePx = 50.dp.toPx()

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Leave the left 50dp edge completely untouched so ModalNavigationDrawer
                            // retains 100% native gesture control with full physics, velocity flings, and springs!
                            if (down.position.x < edgeSafeZonePx) {
                                return@awaitEachGesture
                            }

                            var isHorizontalPull = false
                            var hasDecidedDirection = false
                            var rawPullDistance = 0f
                            var totalX = 0f
                            var totalY = 0f
                            var lastMilestone = 0
                            var hasEngaged = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalX += dx
                                totalY += dy

                                if (!hasDecidedDirection) {
                                    if (kotlin.math.abs(totalY) > 20f && kotlin.math.abs(totalY) > kotlin.math.abs(totalX)) {
                                        // Vertical scroll intent — abort so LazyColumn message scrolling is completely smooth
                                        break
                                    }
                                    if (totalX < -20f && kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) {
                                        // Clear leftward drag intent confirmed!
                                        hasDecidedDirection = true
                                        isHorizontalPull = true
                                        keyboardController?.hide()
                                        focusManager.clearFocus(force = true)
                                    } else if (totalX > 25f) {
                                        // Rightward movement — abort immediately so no interference with other gestures
                                        break
                                    }
                                }

                                if (isHorizontalPull) {
                                    change.consume()
                                    rawPullDistance = (rawPullDistance - dx).coerceAtLeast(0f)

                                    // Progressive non-linear spring resistance
                                    val tension = 1f - (rawPullDistance / (thresholdPx * 3.0f)).coerceIn(0f, 0.50f)
                                    val elasticDistance = rawPullDistance * tension

                                    coroutineScope.launch {
                                        pullDistanceAnim.snapTo(elasticDistance)
                                    }

                                    val progress = (elasticDistance / thresholdPx).coerceIn(0f, 1f)

                                    // Progressive mechanical resistance haptic ticks
                                    if (progress >= 0.70f && lastMilestone < 2) {
                                        lastMilestone = 2
                                        viewModel.hapticManager.tick()
                                    } else if (progress >= 0.35f && lastMilestone < 1) {
                                        lastMilestone = 1
                                        viewModel.hapticManager.tick()
                                    } else if (progress < 0.30f) {
                                        lastMilestone = 0
                                    }

                                    // 100% threshold engagement
                                    if (elasticDistance >= thresholdPx) {
                                        if (!hasEngaged) {
                                            hasEngaged = true
                                            isSpringEngaged = true
                                            viewModel.hapticManager.heavyClick()
                                        }
                                    } else {
                                        if (hasEngaged) {
                                            hasEngaged = false
                                            isSpringEngaged = false
                                        }
                                    }
                                }
                            }

                            // Gesture ended (finger released)
                            if (hasEngaged) {
                                viewModel.hapticManager.success()
                                coroutineScope.launch {
                                    pullDistanceAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    isSpringEngaged = false
                                }
                                triggerRecordingAction()
                            } else {
                                if (pullDistanceAnim.value > 15f) {
                                    viewModel.hapticManager.tick()
                                }
                                coroutineScope.launch {
                                    pullDistanceAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    isSpringEngaged = false
                                }
                            }
                        }
                    }
            ) { _ ->
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val topBarHeight = statusBarTop + 56.dp
                val topFadeHeight = 48.dp
                val topGradientHeight = topBarHeight + topFadeHeight
                val topGradientHeightPx = with(density) { topGradientHeight.toPx() }

                val listBottomPadding = when {
                    isImeOpen -> {
                        var base = 76.dp
                        if (currentAttachments.isNotEmpty()) base += 52.dp
                        if (editingMessage != null) base += 44.dp
                        base
                    }
                    else -> 140.dp
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                        }
                ) {
                    if (messages.isEmpty() && !isGenerating) {
                        // Centered greeting area matching homescreen_1.jpg and homescreen_2.jpg mockups (moves up smoothly when keyboard opens)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                                .padding(top = topBarHeight, bottom = 70.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val eyeLogoRes = if (isAppThemeDark) R.drawable.app_logo else R.drawable.app_icon_trans_black
                            val eyeColorFilter = if (isAppThemeDark) ColorFilter.tint(White) else null

                            Image(
                                painter = painterResource(id = eyeLogoRes),
                                contentDescription = "WYRD Eye",
                                modifier = Modifier.size(160.dp),
                                colorFilter = eyeColorFilter,
                                alpha = 0.30f
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            val greetingText = buildAnnotatedString {
                                append("Welcome Back, ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = White)) {
                                    append(username.ifBlank { "Human" })
                                }
                            }

                            Text(
                                text = greetingText,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    color = TextWhite,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "(◕‿◕)",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = TextMuted,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 22.sp
                                )
                            )

                            if (activeProject != null && activeChatId == null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DarkSurfaceElevated)
                                        .border(0.8.dp, DarkBorder, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(White)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Drafting in ",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = SpaceGrotesk,
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    )
                                    Text(
                                        text = activeProject!!.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = SpaceGrotesk,
                                            color = TextWhite,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        // Active conversation stream with contentPadding allowing smooth scrolling behind both top bar and bottom capsule
                        // LocalBringIntoViewSpec provides NoOpBringIntoViewSpec to completely prevent LazyColumn from snapping or
                        // jumping when text is selected (long-press) anywhere in a message.
                        CompositionLocalProvider(
                            LocalBringIntoViewSpec provides NoOpBringIntoViewSpec
                        ) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(top = topBarHeight + 12.dp, bottom = listBottomPadding),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding()
                            ) {
                                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                                    if (message.role == "user") {
                                        UserMessageBubble(
                                            message = message,
                                            onCopy = { viewModel.copyToClipboard(it.content) },
                                            onEdit = { viewModel.startEditingMessage(it) },
                                            onBranch = { viewModel.branchChat(it) },
                                            onRedo = {
                                                 userScrolledUp = false
                                                 viewModel.redoMessage(it)
                                            }
                                        )
                                    } else {
                                        AssistantMessageItem(
                                            message = message,
                                            ttsPlaybackState = ttsPlaybackState,
                                            onCopy = { viewModel.copyToClipboard(message.content) },
                                            onSeek = { text, offset -> viewModel.seekTts(message.id, text, offset) },
                                            onActiveWordPosition = { line, wordTopInItem, wordBottomInItem ->
                                                if (ttsPlaybackState.isPlaying && !isListDragged && !listState.isScrollInProgress) {
                                                    val layoutInfo = listState.layoutInfo
                                                    val viewportHeight = layoutInfo.viewportSize.height
                                                    if (viewportHeight > 0) {
                                                        val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
                                                        val listTopPaddingPx = with(density) { (topBarHeight + 12.dp).toPx() }
                                                        val topLimit = with(density) { (topBarHeight + 4.dp).toPx() }
                                                        val desiredViewportY = (viewportHeight * 0.32f).coerceAtLeast(topLimit + with(density) { 16.dp.toPx() })
                                                        val bottomLimit = (viewportHeight * 0.44f).coerceAtLeast(desiredViewportY + with(density) { 36.dp.toPx() })

                                                        val needsScroll = if (visibleItem == null) {
                                                            true
                                                        } else {
                                                            val wordTopInViewport = visibleItem.offset + wordTopInItem
                                                            val wordBottomInViewport = visibleItem.offset + wordBottomInItem
                                                            wordTopInViewport < topLimit || wordBottomInViewport > bottomLimit
                                                        }

                                                        if (needsScroll && line != lastTtsScrollLine) {
                                                            lastTtsScrollLine = line
                                                            val targetOffset = (wordTopInItem + listTopPaddingPx - desiredViewportY).toInt().coerceAtLeast(0)
                                                            coroutineScope.launch {
                                                                listState.animateScrollToItem(index, targetOffset)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                if (isGenerating) {
                                    item {
                                        StreamingAssistantMessageItem(
                                            streamingContent = streamingContent,
                                            streamingReasoning = streamingReasoning,
                                            streamingStatus = streamingStatus,
                                            ttsPlaybackState = ttsPlaybackState
                                        )
                                    }
                                }

                                item(key = "chat_bottom_anchor") {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Subtle fade-to-background gradient: messages smoothly dissolve behind floating input capsule and navbar
                    val bottomGradientHeight = if (isImeOpen) 90.dp else 230.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomGradientHeight)
                            .align(Alignment.BottomCenter)
                            .imePadding()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Black.copy(alpha = 0.12f),
                                        Black.copy(alpha = 0.38f),
                                        Black.copy(alpha = 0.70f),
                                        Black.copy(alpha = 0.90f),
                                        Black
                                    )
                                )
                            )
                    )

                    // Tap-to-dismiss barrier for attachment popup
                    if (isAttachmentMenuOpen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isAttachmentMenuOpen = false
                                }
                        )
                    }

                    // Bottom floating controls & input capsule
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Floating action buttons above input: Scroll-to-bottom arrow (centered) & Play/Pause TTS (right)
                        val hasAssistantMessage = messages.any { it.role == "assistant" }
                        val isPlaying = ttsPlaybackState.isPlaying
                        val isPaused = ttsPlaybackState.isPaused
                        val showFloatingTts = (hasAssistantMessage || isGenerating || isPlaying || isPaused) &&
                                (!isImeOpen || isPlaying || isPaused)

                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Centered Scroll-to-Bottom Arrow Button
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showScrollToBottom,
                                enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
                                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f),
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 14.dp)
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurfaceElevated)
                                        .border(
                                            width = 0.8.dp,
                                            color = DarkBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            viewModel.hapticManager.click()
                                            userScrolledUp = false
                                            coroutineScope.launch {
                                                val targetIndex = messages.size + if (isGenerating) 1 else 0
                                                if (targetIndex > 0) {
                                                    if (targetIndex - listState.firstVisibleItemIndex > 8) {
                                                        listState.scrollToItem(targetIndex - 5, 0)
                                                    }
                                                    listState.animateScrollToItem(targetIndex, 0)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Scroll to bottom",
                                        tint = TextWhite,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Floating Play/Pause TTS button: anchored above send button; gracefully hidden when keyboard is open unless playing
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showFloatingTts,
                                enter = fadeIn(tween(200)) + scaleIn(tween(200)),
                                exit = fadeOut(tween(150)) + scaleOut(tween(150)),
                                modifier = Modifier.align(Alignment.BottomEnd)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 20.dp, bottom = 6.dp)
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlaying) White else DarkSurfaceElevated)
                                        .border(
                                            width = 0.8.dp,
                                            color = if (isPlaying) White else DarkBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            viewModel.hapticManager.click()
                                            if (isPlaying) {
                                                viewModel.pauseTts()
                                            } else if (isPaused) {
                                                viewModel.resumeTts()
                                            } else {
                                                // Play latest assistant message
                                                val targetMsg = messages.lastOrNull { it.role == "assistant" }
                                                if (targetMsg != null) {
                                                    val readingContent = formatMarkdownForReading(targetMsg.content)
                                                    viewModel.playTts(targetMsg.id, readingContent.plainText, 0)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause TTS" else "Play TTS",
                                        tint = if (isPlaying) Black else TextWhite,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Sleek editing prompt banner
                        AnimatedVisibility(
                            visible = editingMessage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(0.8.dp, DarkBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = TextWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Editing prompt",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = SpaceGrotesk,
                                            color = TextWhite,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.cancelEditingMessage() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel edit",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Attachment popup menu (Photo, Files, Camera)
                        AnimatedVisibility(
                            visible = isAttachmentMenuOpen,
                            enter = fadeIn(tween(180)) + expandVertically(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(200)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(150)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            AttachmentPopupMenu(
                                onPhotoClick = {
                                    viewModel.hapticManager.click()
                                    isAttachmentMenuOpen = false
                                    photoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onFilesClick = {
                                    viewModel.hapticManager.click()
                                    isAttachmentMenuOpen = false
                                    fileLauncher.launch(arrayOf("*/*"))
                                },
                                onCameraClick = {
                                    viewModel.hapticManager.click()
                                    isAttachmentMenuOpen = false
                                    val cameraInfo = AttachmentHelper.createCameraTempUri(context)
                                    if (cameraInfo != null) {
                                        pendingCameraUri = cameraInfo.first
                                        cameraLauncher.launch(cameraInfo.first)
                                    } else {
                                        Toast.makeText(context, "Unable to access camera", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        // Attachments preview bar (above input bubble)
                        AnimatedVisibility(
                            visible = currentAttachments.isNotEmpty(),
                            enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            AttachmentsPreviewBar(
                                items = currentAttachments,
                                onRemove = { viewModel.removeAttachment(it) },
                                onClearAll = { viewModel.clearAttachments() }
                            )
                        }

                        ChatInputBubble(
                            prompt = promptText,
                            onPromptChange = {
                                if (isAttachmentMenuOpen) isAttachmentMenuOpen = false
                                viewModel.updateInputPrompt(it)
                            },
                            onSend = {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                                userScrolledUp = false
                                if (isAttachmentMenuOpen) isAttachmentMenuOpen = false
                                viewModel.sendMessage()
                            },
                            onStop = {
                                viewModel.stopGeneration()
                            },
                            onSwipeToRecord = {
                                if (isAttachmentMenuOpen) isAttachmentMenuOpen = false
                                triggerRecordingAction()
                            },
                            isGenerating = isGenerating,
                            focusRequester = focusRequester,
                            hasAttachment = currentAttachments.isNotEmpty(),
                            isAttachmentMenuOpen = isAttachmentMenuOpen,
                            onToggleAttachmentMenu = {
                                viewModel.hapticManager.click()
                                if (!isAttachmentMenuOpen) {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                }
                                isAttachmentMenuOpen = !isAttachmentMenuOpen
                            }
                        )
                    }

                    // Subtle fade-to-background top gradient: messages smoothly dissolve behind top bar and status bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topGradientHeight)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Black,
                                        Black.copy(alpha = 0.90f),
                                        Black.copy(alpha = 0.70f),
                                        Black.copy(alpha = 0.40f),
                                        Black.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Permanently floating TopBar with model name and hamburger menu
                    TopBar(
                        activeModel = activeModel,
                        reasoningEffort = reasoningEffort,
                        onModelSelected = { viewModel.setActiveModel(it) },
                        onReasoningEffortSelected = { viewModel.setReasoningEffort(it) },
                        onMenuClick = {
                            viewModel.hapticManager.click()
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            coroutineScope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        },
                        activeProjectName = activeProject?.name,
                        activeProjectColor = activeProject?.color,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // Error message banner
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 58.dp, start = 16.dp, end = 16.dp)
                    ) {
                        errorMessage?.let { error ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkCard)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = AccentRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextWhite,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 13.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearErrorMessage() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss error",
                                        tint = TextGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Spring loaded pull-to-record drawer indicator
        SpringLoadedPullIndicator(
            pullProgress = (pullDistanceAnim.value / thresholdPx).coerceIn(0f, 1f),
            isEngaged = isSpringEngaged
        )

        // Fullscreen Big STT Recording Overlay
        BigSttOverlay(
            visible = isRecording || isTranscribing,
            isRecording = isRecording,
            isTranscribing = isTranscribing,
            activeModel = activeModel,
            reasoningEffort = reasoningEffort,
            onModelSelected = { viewModel.setActiveModel(it) },
            onReasoningEffortSelected = { viewModel.setReasoningEffort(it) },
            onSendRecording = { viewModel.stopRecording(autoSend = true) },
            onStopToEdit = { viewModel.stopRecording(autoSend = false) },
            onCancel = { viewModel.cancelRecording() }
        )

        if (showProjectDialog) {
            ProjectDialog(
                project = projectToEdit,
                viewModel = viewModel,
                onDismiss = {
                    showProjectDialog = false
                    projectToEdit = null
                },
                onSave = { name, description, systemPrompt, color, stagedUris ->
                    val currentProject = projectToEdit
                    if (currentProject != null) {
                        viewModel.updateProject(
                            currentProject.copy(
                                name = name,
                                description = description,
                                systemPrompt = systemPrompt,
                                color = color
                            )
                        )
                    } else {
                        viewModel.createProject(name, description, systemPrompt, color, stagedUris)
                    }
                    showProjectDialog = false
                    projectToEdit = null
                },
                onDelete = { proj ->
                    viewModel.deleteProject(proj)
                    showProjectDialog = false
                    projectToEdit = null
                }
            )
        }
    }
}

@Composable
fun AttachmentPreviewCard(
    item: AttachedItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(item.uriString) {
        if (item.isImage) {
            try {
                BitmapFactory.decodeFile(item.uriString)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceElevated)
            .border(0.8.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkCard)
                    .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isImage) Icons.Outlined.Image else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = SpaceGrotesk,
                    color = TextWhite,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = AttachmentHelper.formatSize(item.sizeBytes),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = SpaceGrotesk,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove attachment",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AttachmentsPreviewBar(
    items: List<AttachedItem>,
    onRemove: (AttachedItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.size == 1) {
        AttachmentPreviewCard(
            item = items[0],
            onRemove = { onRemove(items[0]) },
            modifier = modifier
        )
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(items, key = { it.uriString }) { item ->
                    CompactAttachmentChip(
                        item = item,
                        onRemove = { onRemove(item) }
                    )
                }
            }

            if (items.size >= 2) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .border(0.8.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Clear (${items.size})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = SpaceGrotesk,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CompactAttachmentChip(
    item: AttachedItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(item.uriString) {
        if (item.isImage) {
            try {
                BitmapFactory.decodeFile(item.uriString)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(0.8.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isImage) Icons.Outlined.Image else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.widthIn(max = 120.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = SpaceGrotesk,
                    color = TextWhite,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = AttachmentHelper.formatSize(item.sizeBytes),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = SpaceGrotesk,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val NoOpBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

