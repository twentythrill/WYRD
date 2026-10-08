package com.wyrd.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.R
import com.wyrd.app.ui.theme.Black
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White
import kotlin.math.roundToInt

/**
 * Spring-loaded pull-to-record drawer indicator.
 * Appears dynamically from the right screen bezel as the user pulls left against spring tension.
 * Requires a strong pull to reach 100% engagement threshold before triggering STT.
 */
@Composable
fun SpringLoadedPullIndicator(
    pullProgress: Float,
    isEngaged: Boolean,
    modifier: Modifier = Modifier
) {
    if (pullProgress <= 0.01f) return

    val animatedProgress by animateFloatAsState(
        targetValue = pullProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pullProgress"
    )

    val scale by animateFloatAsState(
        targetValue = if (isEngaged) 1.18f else 0.75f + (animatedProgress * 0.35f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pullScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Elastic pill expanding outward from the right screen edge
        val drawerWidth = 62.dp + (38.dp * animatedProgress)
        val drawerHeight = 84.dp + (16.dp * animatedProgress)

        Box(
            modifier = Modifier
                .width(drawerWidth)
                .height(drawerHeight)
                .clip(RoundedCornerShape(topStart = 44.dp, bottomStart = 44.dp))
                .background(
                    if (isEngaged) White
                    else DarkSurfaceElevated
                )
                .border(
                    width = if (isEngaged) 2.dp else 1.dp,
                    color = if (isEngaged) White else White.copy(alpha = 0.15f + (animatedProgress * 0.5f)),
                    shape = RoundedCornerShape(topStart = 44.dp, bottomStart = 44.dp)
                )
                .padding(start = 10.dp, end = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Tension progress ring and soundwave icon (strictly icon without text)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                if (!isEngaged) {
                    // Circular tension fill indicator
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(50.dp),
                        color = White,
                        trackColor = White.copy(alpha = 0.12f),
                        strokeWidth = 3.dp
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_soundwave),
                    contentDescription = "STT Pull",
                    tint = if (isEngaged) Black else White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
