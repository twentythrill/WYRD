package com.wyrd.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.R
import com.wyrd.app.ui.theme.Black
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextGray
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White

@Composable
fun BigSttOverlay(
    visible: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    onSendRecording: () -> Unit,
    onStopToEdit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(250)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(250)),
        modifier = modifier.fillMaxSize()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "bigPulse")
        val ringScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ringScale"
        )
        val ringAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ringAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black.copy(alpha = 0.88f))
                .pointerInput(Unit) {
                    val swipeThresholdPx = 60.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        var totalDx = 0f
                        var totalDy = 0f
                        var isSwipe = false

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalDx += dx
                            totalDy += dy

                            if (!isSwipe) {
                                if (kotlin.math.abs(totalDy) > 30f && kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx)) {
                                    break
                                }
                                if (totalDx > 20f && totalDx > kotlin.math.abs(totalDy)) {
                                    isSwipe = true
                                }
                            }

                            if (isSwipe && totalDx >= swipeThresholdPx) {
                                change.consume()
                                onCancel()
                                break
                            }
                        }
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Deliberately no-op: taps outside the three buttons cause nothing to happen
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(32.dp)
            ) {
                // Outer pulsing ring and main glowing recording button (Tap -> transcribe & auto-send)
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .scale(ringScale)
                                .clip(CircleShape)
                                .background(White.copy(alpha = ringAlpha))
                        )
                    }

                    // Main Big White Glowing Button
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(White)
                            .clickable(enabled = isRecording && !isTranscribing) { onSendRecording() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTranscribing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp),
                                color = Black,
                                strokeWidth = 3.5.dp
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_soundwave),
                                contentDescription = "Tap to transcribe and send",
                                colorFilter = ColorFilter.tint(Black),
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = if (isTranscribing) "Transcribing audio..." else "Listening...",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGrotesk,
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isTranscribing) "Converting speech to text via OpenAI" else "Tap center to send · Stop to edit",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = SpaceGrotesk,
                        color = TextGray,
                        fontSize = 14.sp
                    )
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Bottom actions: X Button (Cancel & Discard) and Stop Button (Stop & Insert to text field)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Cancel / Discard (X) button
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .border(0.8.dp, DarkBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel and discard",
                            tint = TextWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 2. Stop & Paste to text field (Stop) button
                    IconButton(
                        onClick = onStopToEdit,
                        enabled = isRecording && !isTranscribing,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .border(0.8.dp, DarkBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop and insert text into prompt",
                            tint = TextWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
