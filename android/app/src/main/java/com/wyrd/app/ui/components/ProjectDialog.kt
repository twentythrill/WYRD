package com.wyrd.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wyrd.app.data.local.ProjectEntity
import com.wyrd.app.data.local.ProjectFileEntity
import com.wyrd.app.ui.MainViewModel
import com.wyrd.app.ui.theme.DarkBorder
import com.wyrd.app.ui.theme.DarkCard
import com.wyrd.app.ui.theme.DarkSurface
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.SpaceGrotesk
import com.wyrd.app.ui.theme.TextGray
import com.wyrd.app.ui.theme.TextMuted
import com.wyrd.app.ui.theme.TextWhite
import com.wyrd.app.ui.theme.White
import com.wyrd.app.ui.theme.OnPrimaryColor
import com.wyrd.app.ui.theme.WyrdIcons
import com.wyrd.app.ui.theme.DitherMono
import com.wyrd.app.util.AttachmentHelper

val PROJECT_COLOR_PRESETS = listOf(
    "" to "Default",       // Bone White
    "#F97316" to "Orange",  // Warm Orange
    "#F59E0B" to "Amber",   // Warm Gold
    "#10B981" to "Emerald", // Green
    "#06B6D4" to "Cyan",    // Teal
    "#3B82F6" to "Blue",    // Electric Blue
    "#8B5CF6" to "Purple",  // Violet
    "#EC4899" to "Pink",    // Rose
    "#EF4444" to "Red"      // Crimson
)

@Composable
fun ProjectDialog(
    project: ProjectEntity?,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, systemPrompt: String, color: String, stagedUris: List<Uri>) -> Unit,
    onDelete: (ProjectEntity) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember(project) { mutableStateOf(project?.name ?: "") }
    var description by remember(project) { mutableStateOf(project?.description ?: "") }
    var systemPrompt by remember(project) { mutableStateOf(project?.systemPrompt ?: "") }
    var color by remember(project) { mutableStateOf(project?.color ?: "") }
    var stagedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val projectFiles by remember(project?.id) {
        if (project != null) {
            viewModel.getProjectFiles(project.id)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (project != null) {
                viewModel.addProjectFile(project.id, uri)
            } else {
                stagedUris = stagedUris + uri
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (project != null) "Project Settings" else "New Project",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 18.sp
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable fields
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Project Name
                    Text(
                        text = "Project Name *",
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("e.g. Website Redesign", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = White,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = "Description (Optional)",
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Brief goal or description...", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = White,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Project Color Swatches
                    Text(
                        text = "Project Color",
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PROJECT_COLOR_PRESETS.forEach { (presetHex, label) ->
                            val isSelected = color.equals(presetHex, ignoreCase = true)
                            val swatchColor = if (presetHex.isEmpty()) TextWhite else {
                                try {
                                    Color(android.graphics.Color.parseColor(presetHex))
                                } catch (e: Exception) {
                                    TextWhite
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) White else DarkBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { color = presetHex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = label,
                                        tint = if (presetHex.isEmpty()) OnPrimaryColor else if (presetHex.equals("#F59E0B", true)) DitherMono.Black else DitherMono.Paper,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom Instructions / System Prompt
                    Text(
                        text = "Project System Instructions",
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        placeholder = {
                            Text(
                                "e.g. You are a Kotlin Android expert. Provide clean Jetpack Compose code with minimal boilerplate...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        },
                        minLines = 4,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = White,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Knowledge Base Files Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Knowledge Base Files",
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )

                        TextButton(
                            onClick = { filePickerLauncher.launch("*/*") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add File",
                                color = TextWhite,
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (project == null) {
                        if (stagedUris.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                                    .clickable { filePickerLauncher.launch("*/*") }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = WyrdIcons.UploadFile,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap to add source files (.md, .txt, .pdf, code)",
                                        color = TextGray,
                                        fontSize = 12.sp,
                                        fontFamily = SpaceGrotesk
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                stagedUris.forEach { uri ->
                                    val fileName = remember(uri) { AttachmentHelper.getFileName(context, uri) }
                                    val fileSize = remember(uri) { AttachmentHelper.getFileSize(context, uri) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkSurfaceElevated)
                                            .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = WyrdIcons.Description,
                                                contentDescription = null,
                                                tint = TextGray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = fileName,
                                                        color = TextWhite,
                                                        fontSize = 12.sp,
                                                        fontFamily = SpaceGrotesk,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(DarkCard)
                                                            .border(0.5.dp, DarkBorder, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = "Staged",
                                                            color = TextMuted,
                                                            fontSize = 9.sp,
                                                            fontFamily = SpaceGrotesk
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = AttachmentHelper.formatSize(fileSize),
                                                    color = TextMuted,
                                                    fontSize = 10.sp,
                                                    fontFamily = SpaceGrotesk
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { stagedUris = stagedUris.filter { it != uri } },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove file",
                                                tint = TextWhite.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (projectFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                                .clickable { filePickerLauncher.launch("*/*") }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = WyrdIcons.UploadFile,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap to add source files (.md, .txt, .pdf, code)",
                                    color = TextGray,
                                    fontSize = 12.sp,
                                    fontFamily = SpaceGrotesk
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            projectFiles.forEach { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurfaceElevated)
                                        .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = WyrdIcons.Description,
                                            contentDescription = null,
                                            tint = TextGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = file.name,
                                                color = TextWhite,
                                                fontSize = 12.sp,
                                                fontFamily = SpaceGrotesk,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = AttachmentHelper.formatSize(file.size),
                                                color = TextMuted,
                                                fontSize = 10.sp,
                                                fontFamily = SpaceGrotesk
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteProjectFile(file) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove file",
                                            tint = TextWhite.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Delete Project Button (if editing existing)
                    if (project != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextWhite
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TextWhite.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete Project",
                                color = TextWhite,
                                fontFamily = SpaceGrotesk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = TextGray,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name.trim(), description.trim(), systemPrompt.trim(), color.trim(), stagedUris)
                                onDismiss()
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = White,
                            contentColor = OnPrimaryColor,
                            disabledContainerColor = DarkBorder,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Save",
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && project != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Delete Project?",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${project.name}' and all its attached files? Chats inside this project will be kept.",
                    fontFamily = SpaceGrotesk,
                    color = TextGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(project)
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = TextWhite, fontWeight = FontWeight.Bold, fontFamily = SpaceGrotesk)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextGray, fontFamily = SpaceGrotesk)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}
