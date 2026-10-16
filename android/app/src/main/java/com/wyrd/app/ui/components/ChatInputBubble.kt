package com.wyrd.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.ui.theme.Black
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextPlaceholder
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White

@Composable
fun ChatInputBubble(
    prompt: TextFieldValue,
    onPromptChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit = {},
    onSwipeToRecord: () -> Unit = {},
    isGenerating: Boolean,
    focusRequester: FocusRequester,
    hasAttachment: Boolean = false,
    isAttachmentMenuOpen: Boolean = false,
    onToggleAttachmentMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val canSend = (prompt.text.isNotBlank() || hasAttachment) && !isGenerating

    val plusRotation by animateFloatAsState(
        targetValue = if (isAttachmentMenuOpen) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "plusRotation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(34.dp))
                .background(DarkSurfaceElevated)
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plus icon on far left (animates to 'x' when menu is open)
            IconButton(
                onClick = onToggleAttachmentMenu,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isAttachmentMenuOpen) "Close Attachments" else "Add Attachment",
                    tint = if (isAttachmentMenuOpen) TextWhite else TextMuted,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = plusRotation }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Expandable prompt input (vertically centered in capsule, expands up to 180dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 180.dp)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (prompt.text.isEmpty()) {
                    Text(
                        text = "Ask away...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPlaceholder,
                            fontFamily = SpaceGrotesk,
                            fontSize = 16.sp
                        )
                    )
                }

                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            // Enter must insert a newline and nothing else: handle it here and
                            // fully consume it so it can never fall through to Compose's default
                            // keyboard action (clearFocus) or to the tap-to-dismiss surface,
                            // both of which collapse the soft keyboard.
                            val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                            if (!isEnter) return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent true
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val start = prompt.selection.min
                            val end = prompt.selection.max
                            onPromptChange(
                                TextFieldValue(
                                    text = prompt.text.substring(0, start) + "\n" + prompt.text.substring(end),
                                    selection = TextRange(start + 1),
                                    composition = null
                                )
                            )
                            true
                        },
                    textStyle = TextStyle(
                        color = TextWhite,
                        fontFamily = SpaceGrotesk,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(White),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default // Enter key inserts new line
                    ),
                    // No-op IME actions: the default behavior (clearFocus) would collapse the
                    // keyboard whenever Enter is delivered as an editor action.
                    keyboardActions = KeyboardActions(
                        onDone = {},
                        onGo = {},
                        onNext = {},
                        onPrevious = {},
                        onSearch = {},
                        onSend = {}
                    )
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val isButtonEnabled = isGenerating || canSend

            // Dedicated action button: Send arrow when idle/typing, Stop square when generating
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(White)
                    .clickable(enabled = isButtonEnabled) {
                        if (isGenerating) {
                            onStop()
                        } else {
                            onSend()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isGenerating,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                            .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                    },
                    label = "SendStopAnimation"
                ) { generating ->
                    if (generating) {
                        // Brutalist solid stop square matching WYRD design language
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Black)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Send Message",
                            tint = if (canSend) Black else Black.copy(alpha = 0.35f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
