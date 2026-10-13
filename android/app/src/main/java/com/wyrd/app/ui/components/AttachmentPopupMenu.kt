package com.wyrd.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.WyrdIcons

/**
 * Brutalist pop-up menu featuring three big tactile buttons:
 * 1. Photo (opens default gallery)
 * 2. Files (opens default file manager)
 * 3. Camera (opens default camera)
 */
@Composable
fun AttachmentPopupMenu(
    onPhotoClick: () -> Unit,
    onFilesClick: () -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSurfaceElevated)
            .border(0.8.dp, DarkBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AttachmentActionButton(
                icon = WyrdIcons.ImageOutlined,
                label = "Photo",
                onClick = onPhotoClick
            )

            AttachmentActionButton(
                icon = WyrdIcons.FolderOutlined,
                label = "Files",
                onClick = onFilesClick
            )

            AttachmentActionButton(
                icon = WyrdIcons.PhotoCameraOutlined,
                label = "Camera",
                onClick = onCameraClick
            )
        }
    }
}

@Composable
private fun AttachmentActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
                .border(0.8.dp, DarkBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextWhite,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = TextWhite
            )
        )
    }
}

