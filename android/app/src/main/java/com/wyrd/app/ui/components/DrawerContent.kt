package com.wyrd.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.wyrd.app.R
import com.wyrd.app.ui.theme.isAppThemeDark
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import com.wyrd.app.data.local.ProjectEntity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.data.local.ChatEntity
import com.wyrd.app.ui.theme.AccentRed
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurface
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextGray
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White

@Composable
fun DrawerContent(
    chatList: List<ChatEntity>,
    projectList: List<ProjectEntity> = emptyList(),
    activeChatId: Long?,
    activeProjectId: Long? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewChatInProject: (Long) -> Unit = {},
    onCreateProject: () -> Unit = {},
    onEditProject: (ProjectEntity) -> Unit = {},
    onChatSelected: (Long) -> Unit,
    onTogglePinChat: (ChatEntity) -> Unit,
    onRenameChat: (ChatEntity, String) -> Unit,
    onDeleteChat: (ChatEntity) -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticManager = remember { (context.applicationContext as? com.wyrd.app.WyrdApp)?.hapticManager }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var chatToRename by remember { mutableStateOf<ChatEntity?>(null) }
    var expandedProjectIds by remember { mutableStateOf(setOf<Long>()) }

    androidx.compose.runtime.LaunchedEffect(activeProjectId, activeChatId) {
        if (activeProjectId != null) {
            expandedProjectIds = expandedProjectIds + activeProjectId
        }
        val activeChat = chatList.firstOrNull { it.id == activeChatId }
        if (activeChat?.projectId != null) {
            expandedProjectIds = expandedProjectIds + activeChat.projectId
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(DarkSurface)
            .statusBarsPadding()
            .padding(vertical = 12.dp)
    ) {
        // App branding header: logo marker + "WYRD"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val eyeLogoRes = if (isAppThemeDark) R.drawable.app_logo else R.drawable.app_icon_trans_black
            val eyeColorFilter = if (isAppThemeDark) ColorFilter.tint(TextWhite) else null

            Image(
                painter = painterResource(id = eyeLogoRes),
                contentDescription = "WYRD Logo",
                modifier = Modifier.size(32.dp),
                colorFilter = eyeColorFilter
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "WYRD",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Top static actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            DrawerActionItem(
                icon = Icons.Default.Add,
                label = "New Chat",
                onClick = onNewChat
            )

            DrawerActionItem(
                icon = Icons.Default.Search,
                label = "Search Chats",
                onClick = {
                    hapticManager?.tick()
                    isSearchExpanded = !isSearchExpanded
                }
            )

            if (isSearchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "Filter conversations...",
                            color = TextMuted,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = TextGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = White,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }

            DrawerActionItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                onClick = onNavigateSettings
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
        Spacer(modifier = Modifier.height(10.dp))

        val genericChats = chatList.filter { it.projectId == null }

        // Scrollable past chats & projects
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            // --- PROJECTS SECTION ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROJECTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .width(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                            .clickable {
                                hapticManager?.tick()
                                onCreateProject()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Project",
                            tint = TextWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (projectList.isEmpty()) {
                item {
                    Text(
                        text = "No projects yet. Tap + to create.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextMuted,
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            } else {
                projectList.forEach { project ->
                    val isExpanded = expandedProjectIds.contains(project.id)
                    val projectChats = chatList.filter { it.projectId == project.id }
                    val isProjectSelected = (activeProjectId == project.id && activeChatId == null)

                    item(key = "proj_${project.id}") {
                        ProjectItemRow(
                            project = project,
                            isExpanded = isExpanded,
                            isSelected = isProjectSelected,
                            onToggleExpand = {
                                expandedProjectIds = if (isExpanded) {
                                    expandedProjectIds - project.id
                                } else {
                                    expandedProjectIds + project.id
                                }
                            },
                            onNewChat = { onNewChatInProject(project.id) },
                            onSettings = { onEditProject(project) }
                        )
                    }

                    if (isExpanded) {
                        items(projectChats, key = { "proj_${project.id}_${it.id}" }) { chat ->
                            ChatItemRow(
                                chat = chat,
                                isSelected = chat.id == activeChatId,
                                isNested = true,
                                onClick = { onChatSelected(chat.id) },
                                onTogglePin = { onTogglePinChat(chat) },
                                onRename = { chatToRename = chat },
                                onDelete = { onDeleteChat(chat) }
                            )
                        }
                    }
                }
            }

            // Divider
            item {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            // --- HISTORY SECTION (Generic Chats) ---
            item {
                Text(
                    text = "HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            if (genericChats.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "No conversation history yet" else "No matching conversations",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextMuted,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(genericChats, key = { it.id }) { chat ->
                    ChatItemRow(
                        chat = chat,
                        isSelected = chat.id == activeChatId,
                        isNested = false,
                        onClick = { onChatSelected(chat.id) },
                        onTogglePin = { onTogglePinChat(chat) },
                        onRename = { chatToRename = chat },
                        onDelete = { onDeleteChat(chat) }
                    )
                }
            }
        }
    }

    if (chatToRename != null) {
        RenameChatDialog(
            chat = chatToRename!!,
            onDismiss = { chatToRename = null },
            onConfirm = { newTitle ->
                onRenameChat(chatToRename!!, newTitle)
                chatToRename = null
            }
        )
    }
}

@Composable
private fun DrawerActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextWhite,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = TextWhite,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectItemRow(
    project: ProjectEntity,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpand: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticManager = remember { (context.applicationContext as? com.wyrd.app.WyrdApp)?.hapticManager }

    val projectColor = remember(project.color) {
        if (project.color.isNotBlank()) {
            try {
                Color(android.graphics.Color.parseColor(project.color))
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) DarkCard else Color.Transparent)
                .combinedClickable(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        hapticManager?.tick()
                        onToggleExpand()
                    },
                    onLongClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        hapticManager?.heavyClick()
                        menuExpanded = true
                    }
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        hapticManager?.tick()
                        onToggleExpand()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = projectColor ?: (if (isExpanded || isSelected) TextWhite else TextGray),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = project.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isExpanded || isSelected) TextWhite else TextGray,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .width(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                    .clickable {
                        hapticManager?.tick()
                        onNewChat()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Chat in Project",
                    tint = TextWhite,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            modifier = Modifier.background(DarkCard)
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Settings",
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp
                        )
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSettings()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatItemRow(
    chat: ChatEntity,
    isSelected: Boolean,
    isNested: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticManager = remember { (context.applicationContext as? com.wyrd.app.WyrdApp)?.hapticManager }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) DarkCard else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(
                    onClick = {
                        android.util.Log.i("DrawerContent", "ChatItemRow clicked: ${chat.id}, title=${chat.title}")
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onClick()
                    },
                    onLongClick = {
                        hapticManager?.heavyClick()
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        menuExpanded = true
                    }
                )
                .padding(
                    start = if (isNested) 28.dp else 12.dp,
                    end = 12.dp,
                    top = if (isNested) 8.dp else 10.dp,
                    bottom = if (isNested) 8.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chat.title.ifBlank { "Untitled conversation" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isSelected) TextWhite else TextGray,
                    fontFamily = SpaceGrotesk,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = if (isNested) 13.sp else 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (chat.isPinned) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = TextGray,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            modifier = Modifier.background(DarkCard)
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (chat.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (chat.isPinned) "Unpin chat" else "Pin chat",
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp
                        )
                    }
                },
                onClick = {
                    onTogglePin()
                    menuExpanded = false
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rename chat",
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp
                        )
                    }
                },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete chat",
                            color = AccentRed,
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp
                        )
                    }
                },
                onClick = {
                    onDelete()
                    menuExpanded = false
                }
            )
        }
    }
}

@Composable
private fun RenameChatDialog(
    chat: ChatEntity,
    onDismiss: () -> Unit,
    onConfirm: (newTitle: String) -> Unit
) {
    var titleText by remember { mutableStateOf(chat.title) }
    val focusRequester = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Rename Chat",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextWhite,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter a title for this conversation (max 4 words recommended):",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextGray,
                        fontFamily = SpaceGrotesk,
                        fontSize = 13.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    placeholder = { Text("Chat title...", color = TextMuted, fontFamily = SpaceGrotesk) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextWhite,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = titleText.trim()
                            if (trimmed.isNotBlank()) {
                                onConfirm(trimmed)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = titleText.trim()
                    if (trimmed.isNotBlank()) {
                        onConfirm(trimmed)
                    }
                }
            ) {
                Text(
                    text = "Save",
                    color = TextWhite,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = TextGray,
                    fontFamily = SpaceGrotesk
                )
            }
        }
    )
}
