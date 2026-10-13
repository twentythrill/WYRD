package com.wyrd.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.data.preferences.SupportedModel
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurface
import com.wyrd.app.ui.theme.TextGray
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.WyrdIcons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.wyrd.app.ui.theme.SpaceGrotesk

@Composable
fun TopBar(
    activeModel: SupportedModel,
    isThinkingEnabled: Boolean,
    onModelSelected: (SupportedModel) -> Unit,
    onToggleThinking: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    activeProjectName: String? = null,
    activeProjectColor: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open Drawer",
                tint = TextWhite,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        ModelAndEffortSelector(
            activeModel = activeModel,
            isThinkingEnabled = isThinkingEnabled,
            onModelSelected = onModelSelected,
            onToggleThinking = onToggleThinking
        )

        if (!activeProjectName.isNullOrBlank()) {
            val projectColor = remember(activeProjectColor) {
                if (!activeProjectColor.isNullOrBlank()) {
                    try {
                        Color(android.graphics.Color.parseColor(activeProjectColor))
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = WyrdIcons.Folder,
                    contentDescription = null,
                    tint = projectColor ?: TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = activeProjectName,
                    color = TextGray,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ModelAndEffortSelector(
    activeModel: SupportedModel,
    isThinkingEnabled: Boolean,
    onModelSelected: (SupportedModel) -> Unit,
    onToggleThinking: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    enabled: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticManager = remember { (context.applicationContext as? com.wyrd.app.WyrdApp)?.hapticManager }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) {
                    hapticManager?.tick()
                    dropdownExpanded = true
                }
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MiMo 2.6",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    fontSize = 17.sp
                )
            )
            Text(
                text = when (activeModel) {
                    SupportedModel.MIMO_2_6_PRO -> " Pro"
                    SupportedModel.MIMO_2_6_FLASH -> " Flash"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    color = TextWhite,
                    fontSize = 17.sp
                )
            )
            if (isThinkingEnabled) {
                Text(
                    text = " · Thinking",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal,
                        color = TextGray,
                        fontSize = 15.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select Model and Thinking",
                tint = TextGray,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false },
            modifier = Modifier
                .background(DarkCard)
                .width(230.dp)
        ) {
            Text(
                text = "MODEL",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )

            SupportedModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = model.shortName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (model == activeModel) TextWhite else TextGray,
                                    fontWeight = if (model == activeModel) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (model == activeModel) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = TextWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        onModelSelected(model)
                        dropdownExpanded = false
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = DarkSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        hapticManager?.tick()
                        onToggleThinking(!isThinkingEnabled)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thinking",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextWhite,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "Deep reasoning mode",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
                Switch(
                    checked = isThinkingEnabled,
                    onCheckedChange = {
                        hapticManager?.tick()
                        onToggleThinking(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF141414),
                        checkedTrackColor = Color(0xFFCECDC4),
                        uncheckedThumbColor = Color(0xFF8E8E93),
                        uncheckedTrackColor = Color(0xFF2C2C2E),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
        }
    }
}

