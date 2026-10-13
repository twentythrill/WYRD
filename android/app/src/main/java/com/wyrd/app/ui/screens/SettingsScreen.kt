package com.wyrd.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import com.wyrd.app.ui.theme.WyrdIcons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.wyrd.app.data.preferences.AppTheme
import com.wyrd.app.data.tts.TtsEngineInfo
import com.wyrd.app.data.tts.TtsVoiceInfo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyrd.app.ui.MainViewModel
import com.wyrd.app.ui.theme.AccentGreen
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
import com.wyrd.app.ui.theme.OnPrimaryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storedUsername by viewModel.username.collectAsState()
    val storedSystemPrompt by viewModel.systemPrompt.collectAsState()

    var usernameText by rememberSaveable { mutableStateOf<String?>(null) }
    val currentUsername = usernameText ?: storedUsername

    var systemPromptText by rememberSaveable { mutableStateOf<String?>(null) }
    val currentSystemPrompt = systemPromptText ?: storedSystemPrompt
    val isXiaomiMimoEnabled by viewModel.isXiaomiMimoEnabled.collectAsState()
    val isOpenAiSttEnabled by viewModel.isOpenAiSttEnabled.collectAsState()
    val isExaSearchEnabled by viewModel.isExaSearchEnabled.collectAsState()
    val isTavilySearchEnabled by viewModel.isTavilySearchEnabled.collectAsState()
    val customBaseUrl by viewModel.customBaseUrl.collectAsState()

    val context = LocalContext.current

    var showXiaomiDialog by remember { mutableStateOf(false) }
    var showOpenAiDialog by remember { mutableStateOf(false) }
    var showExaDialog by remember { mutableStateOf(false) }
    var showTavilyDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showClearUnpinnedDialog by remember { mutableStateOf(false) }
    var showSupabaseAuthDialog by remember { mutableStateOf(false) }
    var supabaseAuthIsSignUp by remember { mutableStateOf(false) }
    var supabaseAuthEmail by remember { mutableStateOf("") }
    var supabaseAuthPassword by remember { mutableStateOf("") }
    var supabaseAuthPasswordVisible by remember { mutableStateOf(false) }
    var supabaseAuthError by remember { mutableStateOf<String?>(null) }
    var supabaseAuthLoading by remember { mutableStateOf(false) }

    var editingXiaomiKey by remember { mutableStateOf("") }
    var editingCustomBaseUrl by remember { mutableStateOf("") }
    var editingOpenAiKey by remember { mutableStateOf("") }
    var editingExaKey by remember { mutableStateOf("") }
    var editingTavilyKey by remember { mutableStateOf("") }

    // Native Back Button Handling:
    BackHandler {
        if (showSupabaseAuthDialog) {
            showSupabaseAuthDialog = false
        } else if (showXiaomiDialog) {
            showXiaomiDialog = false
        } else if (showOpenAiDialog) {
            showOpenAiDialog = false
        } else if (showExaDialog) {
            showExaDialog = false
        } else if (showTavilyDialog) {
            showTavilyDialog = false
        } else if (showResetConfirmDialog) {
            showResetConfirmDialog = false
        } else if (showClearUnpinnedDialog) {
            showClearUnpinnedDialog = false
        } else {
            viewModel.hapticManager.click()
            onBack()
        }
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.hapticManager.click()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // USERNAME SECTION
            SettingsSectionHeader(title = "USER PROFILE")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = currentUsername,
                onValueChange = { newText ->
                    usernameText = newText
                    viewModel.setUsername(newText)
                },
                label = { Text("Username", color = TextGray, fontFamily = SpaceGrotesk) },
                placeholder = { Text("Human", color = TextMuted, fontFamily = SpaceGrotesk) },
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

            Spacer(modifier = Modifier.height(28.dp))

            // APPEARANCE SECTION
            SettingsSectionHeader(title = "APPEARANCE")
            Spacer(modifier = Modifier.height(8.dp))
            val appTheme by viewModel.appTheme.collectAsState()
            ThemeDropdownRow(
                currentTheme = appTheme,
                onThemeSelected = { newTheme ->
                    viewModel.hapticManager.click()
                    viewModel.setAppTheme(newTheme)
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // SYSTEM PROMPT SECTION
            SettingsSectionHeader(title = "SYSTEM PROMPT")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = currentSystemPrompt,
                onValueChange = { newText ->
                    systemPromptText = newText
                    viewModel.setSystemPrompt(newText)
                },
                label = { Text("Global instructions", color = TextGray, fontFamily = SpaceGrotesk) },
                placeholder = { Text("Inject instructions into all completion calls...", color = TextMuted, fontFamily = SpaceGrotesk) },
                minLines = 4,
                maxLines = 14,
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
                    .heightIn(min = 120.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // CLOUD SYNC SECTION
            SettingsSectionHeader(title = "CLOUD SYNC")
            Spacer(modifier = Modifier.height(8.dp))
            val supabaseEmail by viewModel.supabaseEmail.collectAsState()
            val isSyncing by viewModel.isSyncing.collectAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .border(0.8.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        viewModel.hapticManager.tick()
                        if (supabaseEmail == null) {
                            showSupabaseAuthDialog = true
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WYRD Cloud (Supabase)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = if (supabaseEmail != null) "Connected as $supabaseEmail" else "Tap to link with web companion",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (supabaseEmail != null) AccentGreen else TextMuted,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp
                        )
                    )
                }

                if (supabaseEmail != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.hapticManager.click()
                                viewModel.syncWithCloud { success, msg ->
                                    Toast.makeText(context, if (success) "Synced with Cloud" else (msg ?: "Sync failed"), Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = TextWhite,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = WyrdIcons.Sync,
                                    contentDescription = "Sync Now",
                                    tint = TextWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.hapticManager.click()
                                viewModel.signOutFromSupabase()
                                Toast.makeText(context, "Cloud account disconnected", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = WyrdIcons.Logout,
                                contentDescription = "Disconnect",
                                tint = AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Connect",
                        tint = TextGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // LLM API SECTION
            SettingsSectionHeader(title = "LLM API")
            Spacer(modifier = Modifier.height(8.dp))
            val hasXiaomiKey = viewModel.getXiaomiApiKey().isNotBlank()
            val xiaomiSubtitle = when {
                customBaseUrl.isNotBlank() -> "Custom Host: $customBaseUrl"
                hasXiaomiKey -> "MiMo 2.6 Flash & Pro (Key configured)"
                else -> "Tap to configure Xiaomi API key & host"
            }
            ProviderConfigRow(
                title = "Xiaomi MiMo",
                subtitle = xiaomiSubtitle,
                isEnabled = isXiaomiMimoEnabled,
                hasKey = hasXiaomiKey,
                onTitleClick = {
                    viewModel.hapticManager.tick()
                    editingXiaomiKey = viewModel.getXiaomiApiKey()
                    editingCustomBaseUrl = customBaseUrl
                    showXiaomiDialog = true
                },
                onToggle = { enabled ->
                    viewModel.hapticManager.click()
                    viewModel.setXiaomiMimoEnabled(enabled) {
                        editingXiaomiKey = viewModel.getXiaomiApiKey()
                        editingCustomBaseUrl = customBaseUrl
                        showXiaomiDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // STT API SECTION
            SettingsSectionHeader(title = "STT API")
            Spacer(modifier = Modifier.height(8.dp))
            val hasOpenAiKey = viewModel.getOpenAiApiKey().isNotBlank()
            ProviderConfigRow(
                title = "OpenAI STT",
                subtitle = if (hasOpenAiKey) "gpt-4o-mini-transcribe (Key configured)" else "Tap to paste OpenAI API key",
                isEnabled = isOpenAiSttEnabled,
                hasKey = hasOpenAiKey,
                onTitleClick = {
                    viewModel.hapticManager.tick()
                    editingOpenAiKey = viewModel.getOpenAiApiKey()
                    showOpenAiDialog = true
                },
                onToggle = { enabled ->
                    viewModel.hapticManager.click()
                    viewModel.setOpenAiSttEnabled(enabled) {
                        editingOpenAiKey = viewModel.getOpenAiApiKey()
                        showOpenAiDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // SPEECH SYNTHESIS (TTS) SECTION
            SettingsSectionHeader(title = "SPEECH SYNTHESIS (TTS)")
            Spacer(modifier = Modifier.height(8.dp))
            val ttsEngine by viewModel.ttsEngine.collectAsState()
            val ttsVoice by viewModel.ttsVoice.collectAsState()
            val availableEngines by viewModel.availableTtsEngines.collectAsState()
            val availableVoices by viewModel.availableTtsVoices.collectAsState()
            val isSpeaking by viewModel.isSpeaking.collectAsState()
            val currentPlayingMsgId by viewModel.currentPlayingMessageId.collectAsState()
            val isTestPlaying = isSpeaking && currentPlayingMsgId == -1L

            TtsSettingsSection(
                currentEngine = ttsEngine,
                currentVoice = ttsVoice,
                availableEngines = availableEngines,
                availableVoices = availableVoices,
                isTestPlaying = isTestPlaying,
                onEngineSelected = { engine ->
                    viewModel.hapticManager.click()
                    viewModel.setTtsEngine(engine)
                },
                onVoiceSelected = { voice ->
                    viewModel.hapticManager.click()
                    viewModel.setTtsVoice(voice)
                },
                onTestClick = {
                    viewModel.testTts()
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // WEB SEARCH API SECTION
            SettingsSectionHeader(title = "WEB SEARCH APIS")
            Spacer(modifier = Modifier.height(8.dp))

            val hasTavilyKey = viewModel.getTavilyApiKey().isNotBlank()
            ProviderConfigRow(
                title = "Tavily Search (Live Web)",
                subtitle = if (hasTavilyKey) "Tavily AI (Personal key active)" else "Real-time web browsing (Tap to set key)",
                isEnabled = isTavilySearchEnabled,
                hasKey = hasTavilyKey,
                onTitleClick = {
                    viewModel.hapticManager.tick()
                    editingTavilyKey = viewModel.getTavilyApiKey()
                    showTavilyDialog = true
                },
                onToggle = { enabled ->
                    viewModel.hapticManager.click()
                    viewModel.setTavilySearchEnabled(enabled)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            val hasExaKey = viewModel.getExaApiKey().isNotBlank()
            ProviderConfigRow(
                title = "Exa AI Search",
                subtitle = if (hasExaKey) "Exa AI MCP (Personal key active)" else "Exa AI MCP (Free public tier / Tap to set key)",
                isEnabled = isExaSearchEnabled,
                hasKey = hasExaKey,
                onTitleClick = {
                    viewModel.hapticManager.tick()
                    editingExaKey = viewModel.getExaApiKey()
                    showExaDialog = true
                },
                onToggle = { enabled ->
                    viewModel.hapticManager.click()
                    viewModel.setExaSearchEnabled(enabled)
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // DATA MANAGEMENT SECTION
            SettingsSectionHeader(title = "DATA MANAGEMENT")
            Spacer(modifier = Modifier.height(8.dp))

            // Delete Unpinned Chats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .border(0.8.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        viewModel.hapticManager.click()
                        showClearUnpinnedDialog = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Delete Unpinned Chats",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Delete all conversations while keeping pinned chats safe",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextGray,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp
                        )
                    )
                }
                Icon(
                    imageVector = WyrdIcons.PushPin,
                    contentDescription = "Preserve Pinned",
                    tint = TextGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .border(0.8.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        viewModel.hapticManager.click()
                        showResetConfirmDialog = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset All App Data",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = AccentRed,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Permanently wipe all chats, API keys, and cache",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextGray,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "WYRD v${com.wyrd.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Supabase Cloud Auth Dialog
        if (showSupabaseAuthDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!supabaseAuthLoading) {
                        showSupabaseAuthDialog = false
                        supabaseAuthError = null
                    }
                },
                containerColor = DarkCard,
                titleContentColor = TextWhite,
                textContentColor = TextGray,
                title = {
                    Text(
                        text = "WYRD Cloud",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Mode Switcher Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!supabaseAuthIsSignUp) White else Color.Transparent)
                                    .clickable {
                                        viewModel.hapticManager.tick()
                                        supabaseAuthIsSignUp = false
                                        supabaseAuthError = null
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign In",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (!supabaseAuthIsSignUp) Black else TextGray,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = SpaceGrotesk
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (supabaseAuthIsSignUp) White else Color.Transparent)
                                    .clickable {
                                        viewModel.hapticManager.tick()
                                        supabaseAuthIsSignUp = true
                                        supabaseAuthError = null
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign Up",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (supabaseAuthIsSignUp) Black else TextGray,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = SpaceGrotesk
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (supabaseAuthIsSignUp)
                                "Create a cloud account to synchronize chats across web and mobile."
                            else
                                "Sign in with your email to access synchronized conversations.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )

                        if (supabaseAuthError != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, AccentRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = supabaseAuthError ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AccentRed,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = supabaseAuthEmail,
                            onValueChange = { supabaseAuthEmail = it },
                            label = { Text("Email", color = TextGray, fontFamily = SpaceGrotesk) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None
                            ),
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

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = supabaseAuthPassword,
                            onValueChange = { supabaseAuthPassword = it },
                            label = { Text("Password", color = TextGray, fontFamily = SpaceGrotesk) },
                            singleLine = true,
                            visualTransformation = if (supabaseAuthPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None
                            ),
                            trailingIcon = {
                                IconButton(onClick = { supabaseAuthPasswordVisible = !supabaseAuthPasswordVisible }) {
                                    Icon(
                                        imageVector = if (supabaseAuthPasswordVisible) WyrdIcons.Visibility else WyrdIcons.VisibilityOff,
                                        contentDescription = if (supabaseAuthPasswordVisible) "Hide password" else "Show password",
                                        tint = TextGray
                                    )
                                }
                            },
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
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val cleanEmail = supabaseAuthEmail.trim()
                            val enteredPassword = supabaseAuthPassword
                            if (cleanEmail.isBlank() || enteredPassword.isBlank()) {
                                supabaseAuthError = "Please enter both email and password"
                                return@TextButton
                            }
                            supabaseAuthLoading = true
                            supabaseAuthError = null
                            if (supabaseAuthIsSignUp) {
                                viewModel.signUpToSupabase(cleanEmail, enteredPassword) { success, msg ->
                                    supabaseAuthLoading = false
                                    if (success) {
                                        showSupabaseAuthDialog = false
                                        Toast.makeText(context, "Account ready! $msg", Toast.LENGTH_LONG).show()
                                    } else {
                                        supabaseAuthError = msg ?: "Sign up failed"
                                    }
                                }
                            } else {
                                viewModel.signInToSupabase(cleanEmail, enteredPassword) { success, msg ->
                                    supabaseAuthLoading = false
                                    if (success) {
                                        showSupabaseAuthDialog = false
                                        Toast.makeText(context, "Cloud sync connected!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        supabaseAuthError = msg ?: "Sign in failed"
                                    }
                                }
                            }
                        },
                        enabled = !supabaseAuthLoading
                    ) {
                        if (supabaseAuthLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = TextWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (supabaseAuthIsSignUp) "Create Account" else "Sign In",
                                color = White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSupabaseAuthDialog = false
                            supabaseAuthError = null
                        },
                        enabled = !supabaseAuthLoading
                    ) {
                        Text("Cancel", color = TextGray, fontFamily = SpaceGrotesk)
                    }
                }
            )
        }

        // Xiaomi API Key & Host Dialog
        if (showXiaomiDialog) {
            ApiKeyEntryDialog(
                title = "Xiaomi MiMo Configuration",
                initialKey = editingXiaomiKey,
                initialUrl = editingCustomBaseUrl,
                urlLabel = "Custom API Base URL (Optional)",
                onDismiss = { showXiaomiDialog = false },
                onSave = { key, url ->
                    viewModel.hapticManager.success()
                    viewModel.setXiaomiApiKey(key)
                    viewModel.setCustomBaseUrl(url)
                    showXiaomiDialog = false
                }
            )
        }

        // OpenAI API Key Dialog
        if (showOpenAiDialog) {
            ApiKeyEntryDialog(
                title = "OpenAI API Key",
                initialKey = editingOpenAiKey,
                onDismiss = { showOpenAiDialog = false },
                onSave = { key, _ ->
                    viewModel.hapticManager.success()
                    viewModel.setOpenAiApiKey(key)
                    showOpenAiDialog = false
                }
            )
        }

        // Tavily Search API Key Dialog
        if (showTavilyDialog) {
            ApiKeyEntryDialog(
                title = "Tavily Search API Key",
                initialKey = editingTavilyKey,
                description = "Get a free API key at app.tavily.com (1,000 free searches/month). Enables real-time live web browsing for current events and breaking news.",
                placeholder = "tvly-...",
                onDismiss = { showTavilyDialog = false },
                onSave = { key, _ ->
                    viewModel.hapticManager.success()
                    viewModel.setTavilyApiKey(key)
                    showTavilyDialog = false
                }
            )
        }

        // Exa Search API Key Dialog
        if (showExaDialog) {
            ApiKeyEntryDialog(
                title = "Exa AI Search Key",
                initialKey = editingExaKey,
                description = "Get a free API key at dashboard.exa.ai/api-keys (1,000 free searches) to avoid shared IP limits.",
                placeholder = "Paste Exa key or leave blank for public tier...",
                onDismiss = { showExaDialog = false },
                onSave = { key, _ ->
                    viewModel.hapticManager.success()
                    viewModel.setExaApiKey(key)
                    showExaDialog = false
                }
            )
        }

        // Reset All Data Confirmation Dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                containerColor = DarkCard,
                title = {
                    Text(
                        text = "Reset All App Data?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = AccentRed,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Text(
                        text = "This will permanently delete all chat history, clear all stored API keys, and wipe local caches. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextGray,
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetConfirmDialog = false
                            viewModel.resetAllAppData {
                                onBack()
                            }
                        }
                    ) {
                        Text(
                            text = "Wipe Everything",
                            color = AccentRed,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text(
                            text = "Cancel",
                            color = TextGray,
                            fontFamily = SpaceGrotesk
                        )
                    }
                }
            )
        }

        // Delete Unpinned Chats Confirmation Dialog
        if (showClearUnpinnedDialog) {
            AlertDialog(
                onDismissRequest = { showClearUnpinnedDialog = false },
                containerColor = DarkCard,
                title = {
                    Text(
                        text = "Delete Unpinned Chats?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Text(
                        text = "This will permanently delete all unpinned chat history. All pinned conversations will be safely preserved. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextGray,
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearUnpinnedDialog = false
                            viewModel.deleteAllUnpinnedChats { count ->
                                val message = if (count > 0) {
                                    "Deleted $count unpinned chat${if (count > 1) "s" else ""}"
                                } else {
                                    "No unpinned chats to delete"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(
                            text = "Delete",
                            color = AccentRed,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearUnpinnedDialog = false }) {
                        Text(
                            text = "Cancel",
                            color = TextGray,
                            fontFamily = SpaceGrotesk
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.3.sp
        )
    )
}

@Composable
private fun ProviderConfigRow(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    hasKey: Boolean,
    onTitleClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(0.8.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onTitleClick() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextWhite,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                )
                if (hasKey) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Key set",
                        tint = Color(0xFF32D74B),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextGray,
                    fontFamily = SpaceGrotesk,
                    fontSize = 13.sp
                )
            )
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Black,
                checkedTrackColor = White,
                uncheckedThumbColor = TextGray,
                uncheckedTrackColor = DarkCard,
                uncheckedBorderColor = DarkBorder
            )
        )
    }
}

@Composable
private fun ApiKeyEntryDialog(
    title: String,
    initialKey: String,
    initialUrl: String? = null,
    urlLabel: String? = null,
    description: String = "Encrypted in Android Keystore via EncryptedSharedPreferences.",
    placeholder: String = "Paste secret key here...",
    onDismiss: () -> Unit,
    onSave: (key: String, customUrl: String) -> Unit
) {
    var keyText by remember { mutableStateOf(initialKey) }
    var urlText by remember { mutableStateOf(initialUrl ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = WyrdIcons.Key,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
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
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextGray,
                        fontFamily = SpaceGrotesk,
                        fontSize = 13.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    placeholder = { Text(placeholder, color = TextMuted, fontFamily = SpaceGrotesk) },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = White,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (urlLabel != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text(urlLabel, color = TextGray, fontFamily = SpaceGrotesk, fontSize = 12.sp) },
                        placeholder = { Text("Auto (api / token-plan)", color = TextMuted, fontFamily = SpaceGrotesk, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = White,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyText.trim(), urlText.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = White,
                    contentColor = OnPrimaryColor,
                    disabledContainerColor = DarkBorder,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Save",
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

@Composable
private fun ThemeDropdownRow(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceElevated)
                .border(0.8.dp, DarkBorder, RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextWhite,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (currentTheme) {
                        AppTheme.SYSTEM -> "System (Follows device settings)"
                        AppTheme.LIGHT -> "Light (Manual light mode)"
                        AppTheme.DARK -> "Dark (Manual AMOLED black)"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextGray,
                        fontFamily = SpaceGrotesk,
                        fontSize = 13.sp
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentTheme.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextWhite,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Theme",
                    tint = TextGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(DarkCard)
                .width(220.dp)
        ) {
            AppTheme.entries.forEach { theme ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = theme.displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (theme == currentTheme) TextWhite else TextGray,
                                        fontWeight = if (theme == currentTheme) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = SpaceGrotesk
                                    )
                                )
                                Text(
                                    text = when (theme) {
                                        AppTheme.SYSTEM -> "Follows system"
                                        AppTheme.LIGHT -> "Always light"
                                        AppTheme.DARK -> "Always dark"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            if (theme == currentTheme) {
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
                        onThemeSelected(theme)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TtsSettingsSection(
    currentEngine: String,
    currentVoice: String,
    availableEngines: List<TtsEngineInfo>,
    availableVoices: List<TtsVoiceInfo>,
    isTestPlaying: Boolean,
    onEngineSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onTestClick: () -> Unit
) {
    var engineMenuExpanded by remember { mutableStateOf(false) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }

    val activeEngineLabel = when {
        currentEngine.isBlank() || currentEngine == "system" -> "System Default (Device settings)"
        else -> availableEngines.find { it.packageName == currentEngine }?.label ?: currentEngine
    }

    val activeVoiceLabel = when {
        currentVoice.isBlank() || currentVoice == "system" -> "System / Engine Default"
        else -> availableVoices.find { it.name == currentVoice }?.displayName ?: currentVoice
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(0.8.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Engine Selector Row
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { engineMenuExpanded = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TTS Engine Provider",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeEngineLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextGray,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Engine",
                    tint = TextGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = engineMenuExpanded,
                onDismissRequest = { engineMenuExpanded = false },
                modifier = Modifier
                    .background(DarkCard)
                    .width(280.dp)
            ) {
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "System Default",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (currentEngine.isBlank() || currentEngine == "system") TextWhite else TextGray,
                                        fontWeight = if (currentEngine.isBlank() || currentEngine == "system") FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = SpaceGrotesk
                                    )
                                )
                                Text(
                                    text = "Follows phone settings",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            if (currentEngine.isBlank() || currentEngine == "system") {
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
                        onEngineSelected("")
                        engineMenuExpanded = false
                    }
                )

                val enginesToShow = if (availableEngines.isNotEmpty()) {
                    availableEngines
                } else {
                    listOf(
                        TtsEngineInfo("com.samsung.SMT", "Samsung text-to-speech"),
                        TtsEngineInfo("com.google.android.tts", "Speech Recognition and Synthesis from Google")
                    )
                }

                enginesToShow.forEach { engine ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = engine.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (currentEngine == engine.packageName) TextWhite else TextGray,
                                            fontWeight = if (currentEngine == engine.packageName) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = SpaceGrotesk
                                        )
                                    )
                                    Text(
                                        text = engine.packageName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextMuted,
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                                if (currentEngine == engine.packageName) {
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
                            onEngineSelected(engine.packageName)
                            engineMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (currentEngine == "com.samsung.SMT") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Note: One UI marks Samsung TTS as private for system apps. Android routes speech through Google Speech Services; configure your preferred voice profile directly below.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(DarkBorder)
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Voice Selector Row
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { voiceMenuExpanded = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voice Profile",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextWhite,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeVoiceLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextGray,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Voice",
                    tint = TextGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = voiceMenuExpanded,
                onDismissRequest = { voiceMenuExpanded = false },
                modifier = Modifier
                    .background(DarkCard)
                    .width(280.dp)
            ) {
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "System / Engine Default",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (currentVoice.isBlank() || currentVoice == "system") TextWhite else TextGray,
                                        fontWeight = if (currentVoice.isBlank() || currentVoice == "system") FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = SpaceGrotesk
                                    )
                                )
                                Text(
                                    text = "Honors phone settings selection",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            if (currentVoice.isBlank() || currentVoice == "system") {
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
                        onVoiceSelected("")
                        voiceMenuExpanded = false
                    }
                )

                availableVoices.forEach { voice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = voice.displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (currentVoice == voice.name) TextWhite else TextGray,
                                        fontWeight = if (currentVoice == voice.name) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = SpaceGrotesk
                                    )
                                )
                                if (currentVoice == voice.name) {
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
                            onVoiceSelected(voice.name)
                            voiceMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(DarkBorder)
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Test Speech Preview Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Test Speech",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextWhite,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isTestPlaying) "Playing test utterance..." else "Tap to preview voice & engine",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isTestPlaying) TextWhite else TextGray,
                        fontFamily = SpaceGrotesk,
                        fontSize = 13.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isTestPlaying) AccentRed else White)
                    .clickable { onTestClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isTestPlaying) WyrdIcons.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isTestPlaying) "Stop" else "Test",
                        tint = if (isTestPlaying) White else Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTestPlaying) "Stop" else "Preview",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isTestPlaying) White else Black,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

