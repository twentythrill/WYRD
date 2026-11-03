package com.wyrd.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.wyrd.app.WyrdApp
import com.wyrd.app.data.local.ChatEntity
import com.wyrd.app.data.local.MessageEntity
import com.wyrd.app.data.local.ProjectEntity
import com.wyrd.app.data.local.ProjectFileEntity
import com.wyrd.app.data.network.ChatMessagePayload
import com.wyrd.app.data.preferences.AppTheme
import com.wyrd.app.data.preferences.PreferencesManager
import android.util.Log
import com.wyrd.app.data.preferences.ReasoningEffort
import com.wyrd.app.data.preferences.SupportedModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.wyrd.app.data.tts.TtsPlaybackState
import com.wyrd.app.ui.components.formatMarkdownForReading
import com.wyrd.app.util.AttachmentHelper

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WyrdApp
    private val database = app.database
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()
    private val projectDao = database.projectDao()
    private val projectFileDao = database.projectFileDao()
    private val preferencesManager = app.preferencesManager
    private val secureKeyStorage = app.secureKeyStorage
    private val mimoService = app.mimoService
    private val exaSearchService = app.exaSearchService
    private val tavilySearchService = app.tavilySearchService
    private val openAiSttService = app.openAiSttService
    private val nativeTtsManager = app.nativeTtsManager
    private val audioRecorderHelper = app.audioRecorderHelper
    val hapticManager = app.hapticManager
    val supabaseSyncManager = app.supabaseSyncManager

    private val _supabaseEmail = MutableStateFlow<String?>(supabaseSyncManager.getUserEmail().ifBlank { null })
    val supabaseEmail: StateFlow<String?> = _supabaseEmail.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Chat session state
    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId: StateFlow<Long?> = _activeChatId.asStateFlow()

    // Projects state
    val projectList: StateFlow<List<ProjectEntity>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProjectId = MutableStateFlow<Long?>(null)
    val activeProjectId: StateFlow<Long?> = _activeProjectId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProject: StateFlow<ProjectEntity?> = kotlinx.coroutines.flow.combine(_activeChatId, _activeProjectId) { chatId, activeProjId ->
        Pair(chatId, activeProjId)
    }.flatMapLatest { (chatId, activeProjId) ->
        val effectiveProjId = if (chatId != null) {
            chatDao.getChatById(chatId)?.projectId
        } else {
            activeProjId
        }
        if (effectiveProjId == null) flowOf<ProjectEntity?>(null) else flow { emit(projectDao.getProjectById(effectiveProjId)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatList: StateFlow<List<ChatEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            chatDao.getAllChats()
        } else {
            chatDao.searchChats(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> = _activeChatId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            messageDao.getMessagesForChat(id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Generation state
    private var generationJob: Job? = null
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _streamingReasoning = MutableStateFlow("")
    val streamingReasoning: StateFlow<String> = _streamingReasoning.asStateFlow()

    private val _streamingStatus = MutableStateFlow<String?>(null)
    val streamingStatus: StateFlow<String?> = _streamingStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Editing message state
    private val _editingMessage = MutableStateFlow<MessageEntity?>(null)
    val editingMessage: StateFlow<MessageEntity?> = _editingMessage.asStateFlow()

    // Preferences & Settings
    val username: StateFlow<String> = preferencesManager.usernameFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedUsername())

    val systemPrompt: StateFlow<String> = preferencesManager.systemPromptFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedSystemPrompt())

    val activeModel: StateFlow<SupportedModel> = preferencesManager.activeModelFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedActiveModel())

    val isThinkingEnabled: StateFlow<Boolean> = preferencesManager.isThinkingEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedThinkingEnabled())

    val reasoningEffort: StateFlow<ReasoningEffort> = preferencesManager.reasoningEffortFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedReasoningEffort())

    val isXiaomiMimoEnabled: StateFlow<Boolean> = preferencesManager.isXiaomiMimoEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedXiaomiMimoEnabled())

    val isOpenAiSttEnabled: StateFlow<Boolean> = preferencesManager.isOpenAiSttEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedOpenAiSttEnabled())

    val isExaSearchEnabled: StateFlow<Boolean> = preferencesManager.isExaSearchEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedExaSearchEnabled())

    val isTavilySearchEnabled: StateFlow<Boolean> = preferencesManager.isTavilySearchEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedTavilySearchEnabled())

    val customBaseUrl: StateFlow<String> = preferencesManager.customBaseUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedCustomBaseUrl())

    val appTheme: StateFlow<AppTheme> = preferencesManager.appThemeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedAppTheme())

    // STT Recording state
    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()
    private var transcriptionJob: Job? = null
    private var autoReadNextAssistantMessage: Boolean = false
    @Volatile
    private var isTtsStreamingRequested: Boolean = false
    @Volatile
    private var currentStreamTtsCallback: (() -> Unit)? = null

    // TTS state
    val ttsPlaybackState: StateFlow<TtsPlaybackState> = nativeTtsManager.playbackState
    val isSpeaking: StateFlow<Boolean> = nativeTtsManager.playbackState
        .map { it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val currentPlayingMessageId: StateFlow<Long?> = nativeTtsManager.playbackState
        .map { it.messageId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ttsEngine: StateFlow<String> = preferencesManager.ttsEngineFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedTtsEngine())

    val ttsVoice: StateFlow<String> = preferencesManager.ttsVoiceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesManager.getCachedTtsVoice())

    val availableTtsEngines: StateFlow<List<com.wyrd.app.data.tts.TtsEngineInfo>> = nativeTtsManager.availableEngines
    val availableTtsVoices: StateFlow<List<com.wyrd.app.data.tts.TtsVoiceInfo>> = nativeTtsManager.availableVoices

    init {
        com.wyrd.app.service.WyrdBackgroundService.stopCallback = {
            stopGeneration()
        }
        viewModelScope.launch {
            preferencesManager.ttsEngineFlow.collect { engine ->
                nativeTtsManager.setEngine(engine)
            }
        }
        viewModelScope.launch {
            preferencesManager.ttsVoiceFlow.collect { voice ->
                nativeTtsManager.setPreferredVoice(voice)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(25000L)
                if (supabaseSyncManager.isUserSignedIn() && !_isGenerating.value && !_isSyncing.value) {
                    try {
                        _isSyncing.value = true
                        supabaseSyncManager.syncAll()
                    } catch (_: Exception) {
                    } finally {
                        _isSyncing.value = false
                    }
                }
            }
        }
    }

    fun refreshSync() {
        if (supabaseSyncManager.isUserSignedIn() && !_isGenerating.value && !_isSyncing.value) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    _isSyncing.value = true
                    supabaseSyncManager.syncAll()
                } catch (_: Exception) {
                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }

    // Attachment state (supports multiple attachments)
    private val _currentAttachments = MutableStateFlow<List<com.wyrd.app.util.AttachedItem>>(emptyList())
    val currentAttachments: StateFlow<List<com.wyrd.app.util.AttachedItem>> = _currentAttachments.asStateFlow()

    fun addAttachments(items: List<com.wyrd.app.util.AttachedItem>) {
        if (items.isEmpty()) return
        _currentAttachments.value = _currentAttachments.value + items
        hapticManager.tick()
    }

    fun removeAttachment(item: com.wyrd.app.util.AttachedItem) {
        hapticManager.click()
        _currentAttachments.value = _currentAttachments.value.filter { it.uriString != item.uriString }
    }

    fun clearAttachments() {
        hapticManager.click()
        _currentAttachments.value = emptyList()
    }

    // Input state
    val inputPrompt = MutableStateFlow(TextFieldValue(""))

    fun updateInputPrompt(value: TextFieldValue) {
        inputPrompt.value = value
    }

    fun startEditingMessage(message: MessageEntity) {
        hapticManager.click()
        autoReadNextAssistantMessage = false
        nativeTtsManager.stop()
        _editingMessage.value = message
        val restoredAttachments = message.getAttachmentList().map { info ->
            val isImg = info.type == "image"
            com.wyrd.app.util.AttachedItem(
                uriString = info.uri,
                name = info.name,
                mimeType = if (isImg) "image/jpeg" else "application/octet-stream",
                sizeBytes = info.size,
                isImage = isImg
            )
        }
        _currentAttachments.value = restoredAttachments
        inputPrompt.value = TextFieldValue(
            text = message.content,
            selection = TextRange(message.content.length)
        )
    }

    fun cancelEditingMessage() {
        hapticManager.click()
        _editingMessage.value = null
        _currentAttachments.value = emptyList()
        inputPrompt.value = TextFieldValue("")
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun stopGeneration() {
        if (!_isGenerating.value && generationJob == null) return
        hapticManager.click()
        autoReadNextAssistantMessage = false
        isTtsStreamingRequested = false
        currentStreamTtsCallback = null
        nativeTtsManager.stop()
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        _streamingContent.value = ""
        _streamingReasoning.value = ""
        _streamingStatus.value = null
        com.wyrd.app.service.WyrdBackgroundService.stop(app)
    }

    fun startNewChat() {
        hapticManager.click()
        generationJob?.cancel()
        generationJob = null
        autoReadNextAssistantMessage = false
        _editingMessage.value = null
        _activeProjectId.value = null
        _activeChatId.value = null
        _currentAttachments.value = emptyList()
        inputPrompt.value = TextFieldValue("")
        _streamingContent.value = ""
        _streamingReasoning.value = ""
        _streamingStatus.value = null
        _isGenerating.value = false
        nativeTtsManager.stop()
    }

    fun startNewChatInProject(projectId: Long) {
        hapticManager.click()
        generationJob?.cancel()
        generationJob = null
        autoReadNextAssistantMessage = false
        _editingMessage.value = null
        _activeProjectId.value = projectId
        _activeChatId.value = null
        _currentAttachments.value = emptyList()
        inputPrompt.value = TextFieldValue("")
        _streamingContent.value = ""
        _streamingReasoning.value = ""
        _streamingStatus.value = null
        _isGenerating.value = false
        nativeTtsManager.stop()
    }

    fun selectChat(chatId: Long) {
        android.util.Log.i("MainViewModel", "selectChat called with chatId=$chatId")
        hapticManager.tick()
        generationJob?.cancel()
        generationJob = null
        autoReadNextAssistantMessage = false
        _editingMessage.value = null
        viewModelScope.launch {
            val chat = chatDao.getChatById(chatId)
            _activeProjectId.value = chat?.projectId
            _activeChatId.value = chatId
        }
        _currentAttachments.value = emptyList()
        inputPrompt.value = TextFieldValue("")
        _streamingContent.value = ""
        _streamingReasoning.value = ""
        _streamingStatus.value = null
        _isGenerating.value = false
        nativeTtsManager.stop()
    }

    fun createProject(
        name: String,
        description: String = "",
        systemPrompt: String = "",
        color: String = "",
        stagedUris: List<Uri> = emptyList(),
        onCreated: (Long) -> Unit = {}
    ) {
        hapticManager.click()
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ProjectEntity(
                name = name.trim(),
                description = description.trim(),
                systemPrompt = systemPrompt.trim(),
                color = color.trim()
            )
            val newId = projectDao.insertProject(entity)
            val createdProj = projectDao.getProjectById(newId)

            for (uri in stagedUris) {
                try {
                    val item = AttachmentHelper.processUri(app, uri)
                    if (item != null) {
                        var storagePath = item.uriString
                        if (supabaseSyncManager.isUserSignedIn()) {
                            val publicUrl = supabaseSyncManager.uploadAttachmentToStorage(item.uriString, item.name)
                            if (publicUrl != null) {
                                storagePath = publicUrl
                            }
                        }
                        val fileEntity = ProjectFileEntity(
                            projectId = newId,
                            projectSyncId = createdProj?.syncId ?: "",
                            name = item.name,
                            size = item.sizeBytes,
                            mimeType = item.mimeType,
                            storagePath = storagePath,
                            textContent = item.textContent
                        )
                        projectFileDao.insertFile(fileEntity)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Failed to add staged project file $uri", e)
                }
            }

            _activeProjectId.value = newId
            _activeChatId.value = null
            if (supabaseSyncManager.isUserSignedIn()) {
                try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                onCreated(newId)
            }
        }
    }

    fun updateProject(project: ProjectEntity) {
        hapticManager.click()
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            if (supabaseSyncManager.isUserSignedIn()) {
                try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
            }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        deleteProject(project.id)
    }

    fun deleteProject(projectId: Long) {
        hapticManager.click()
        viewModelScope.launch(Dispatchers.IO) {
            val proj = projectDao.getProjectById(projectId)
            if (proj != null) {
                projectDao.deleteProjectById(projectId)
                projectFileDao.deleteFilesByProjectId(projectId)
                chatDao.detachChatsFromProject(projectId)
                if (_activeProjectId.value == projectId) {
                    _activeProjectId.value = null
                    _activeChatId.value = null
                }
                if (supabaseSyncManager.isUserSignedIn() && proj.syncId.isNotBlank()) {
                    try { supabaseSyncManager.deleteProjectRemotely(proj.syncId) } catch (_: Exception) {}
                    try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
                }
            }
        }
    }

    fun getProjectFiles(projectId: Long): Flow<List<ProjectFileEntity>> {
        return projectFileDao.getFilesByProject(projectId)
    }

    fun addProjectFile(projectId: Long, uri: Uri) {
        hapticManager.click()
        viewModelScope.launch(Dispatchers.IO) {
            val proj = projectDao.getProjectById(projectId) ?: return@launch
            val item = AttachmentHelper.processUri(app, uri) ?: return@launch
            var storagePath = item.uriString
            if (supabaseSyncManager.isUserSignedIn()) {
                val publicUrl = supabaseSyncManager.uploadAttachmentToStorage(item.uriString, item.name)
                if (publicUrl != null) {
                    storagePath = publicUrl
                }
            }

            val fileEntity = ProjectFileEntity(
                projectId = projectId,
                projectSyncId = proj.syncId,
                name = item.name,
                size = item.sizeBytes,
                mimeType = item.mimeType,
                storagePath = storagePath,
                textContent = item.textContent
            )
            projectFileDao.insertFile(fileEntity)
            if (supabaseSyncManager.isUserSignedIn()) {
                try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
            }
        }
    }

    fun deleteProjectFile(file: ProjectFileEntity) {
        hapticManager.click()
        viewModelScope.launch(Dispatchers.IO) {
            projectFileDao.deleteFileById(file.id)
            if (supabaseSyncManager.isUserSignedIn() && file.syncId.isNotBlank()) {
                try { supabaseSyncManager.deleteProjectFileRemotely(file.syncId, file.storagePath) } catch (_: Exception) {}
                try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
            }
        }
    }

    fun togglePinChat(chat: ChatEntity) {
        hapticManager.click()
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.updatePinned(chat.id, !chat.isPinned)
            if (supabaseSyncManager.isUserSignedIn()) {
                try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
            }
        }
    }

    fun deleteChat(chat: ChatEntity) {
        hapticManager.click()
        viewModelScope.launch(Dispatchers.IO) {
            val msgs = messageDao.getMessagesListForChat(chat.id)
            val otherChatUris = messageDao.getAllMessages()
                .asSequence()
                .filter { it.chatId != chat.id }
                .flatMap { it.getAttachmentList().asSequence() }
                .map { it.uri }
                .filter { it.isNotBlank() }
                .toSet()
            com.wyrd.app.util.AttachmentHelper.cleanupAttachments(app, msgs, excludeUris = otherChatUris)
            chatDao.deleteChatById(chat.id)
            messageDao.deleteMessagesForChat(chat.id)
            if (supabaseSyncManager.isUserSignedIn() && chat.syncId.isNotBlank()) {
                try { supabaseSyncManager.deleteChat(chat.syncId) } catch (_: Exception) {}
            }
            if (_activeChatId.value == chat.id) {
                startNewChat()
            }
        }
    }

    fun deleteAllUnpinnedChats(onComplete: (deletedCount: Int) -> Unit = {}) {
        hapticManager.click()
        viewModelScope.launch(Dispatchers.IO) {
            val deletedCount = try {
                val allChats = chatDao.getAllChatsList()
                val unpinnedChats = allChats.filter { !it.isPinned }
                val pinnedChatIds = allChats.filter { it.isPinned }.map { it.id }.toSet()
                val pinnedChatUris = messageDao.getAllMessages()
                    .asSequence()
                    .filter { it.chatId in pinnedChatIds }
                    .flatMap { it.getAttachmentList().asSequence() }
                    .map { it.uri }
                    .filter { it.isNotBlank() }
                    .toSet()
                for (c in unpinnedChats) {
                    val msgs = messageDao.getMessagesListForChat(c.id)
                    com.wyrd.app.util.AttachmentHelper.cleanupAttachments(app, msgs, excludeUris = pinnedChatUris)
                }
                val count = chatDao.deleteAllUnpinnedChats()
                messageDao.deleteOrphanedMessages()
                if (supabaseSyncManager.isUserSignedIn()) {
                    try { supabaseSyncManager.deleteUnpinnedChats() } catch (_: Exception) {}
                }
                count
            } catch (e: Throwable) {
                android.util.Log.e("MainViewModel", "Failed to delete unpinned chats", e)
                0
            }

            withContext(Dispatchers.Main) {
                val currentActiveId = _activeChatId.value
                if (currentActiveId != null) {
                    val currentChat = chatDao.getChatById(currentActiveId)
                    if (currentChat == null) {
                        startNewChat()
                    }
                }
                hapticManager.heavyClick()
                onComplete(deletedCount)
            }
        }
    }

    fun renameChat(chat: ChatEntity, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotBlank()) {
            hapticManager.click()
            viewModelScope.launch(Dispatchers.IO) {
                chatDao.updateTitle(chat.id, clean)
                if (supabaseSyncManager.isUserSignedIn()) {
                    try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
                }
            }
        }
    }

    fun setActiveModel(model: SupportedModel) {
        hapticManager.click()
        viewModelScope.launch {
            preferencesManager.setActiveModel(model)
            val key = if (model.isOpenAi) {
                secureKeyStorage.getOpenAiApiKey()
            } else {
                secureKeyStorage.getXiaomiApiKey()
            }
            mimoService.prewarmConnection(key, model, customBaseUrl.value)
        }
    }

    fun setThinkingEnabled(enabled: Boolean) {
        hapticManager.click()
        viewModelScope.launch {
            preferencesManager.setThinkingEnabled(enabled)
        }
    }

    fun setReasoningEffort(effort: ReasoningEffort) {
        hapticManager.click()
        viewModelScope.launch {
            preferencesManager.setReasoningEffort(effort)
        }
    }

    fun sendMessage(autoRead: Boolean = false) {
        autoReadNextAssistantMessage = autoRead
        val prompt = inputPrompt.value.text.trim()
        val attachments = _currentAttachments.value
        if ((prompt.isBlank() && attachments.isEmpty()) || _isGenerating.value) {
            autoReadNextAssistantMessage = false
            return
        }

        val activeM = activeModel.value
        val apiKey = if (activeM.isOpenAi) {
            secureKeyStorage.getOpenAiApiKey()
        } else {
            secureKeyStorage.getXiaomiApiKey()
        }
        if (apiKey.isBlank()) {
            _errorMessage.value = if (activeM.isOpenAi) {
                "OpenAI API key is required for GPT 6.1 Sol. Please configure it in Settings."
            } else {
                "Xiaomi MiMo API key is required. Please add it in Settings."
            }
            autoReadNextAssistantMessage = false
            return
        }

        hapticManager.click()
        nativeTtsManager.stop()
        inputPrompt.value = TextFieldValue("")
        _currentAttachments.value = emptyList()
        val editingMsg = _editingMessage.value
        _editingMessage.value = null
        _isGenerating.value = true
        _streamingContent.value = ""
        _streamingReasoning.value = ""
        _streamingStatus.value = "Sending..."
        _errorMessage.value = null

        generationJob = app.applicationScope.launch(Dispatchers.IO) {
            var currentChatId = _activeChatId.value
            val isFirstMessageInChat = (currentChatId == null) || messageDao.getMessagesListForChat(currentChatId).isEmpty()

            val displayPrompt = when {
                prompt.isNotBlank() -> prompt
                attachments.any { it.isImage } && attachments.all { it.isImage } -> {
                    if (attachments.size == 1) "What is in this image?" else "What is in these images?"
                }
                attachments.isNotEmpty() -> "Analyze the attached file(s)."
                else -> ""
            }

            if (currentChatId == null) {
                val titleSource = if (prompt.isNotBlank()) prompt else (attachments.firstOrNull()?.name ?: "New Chat")
                val initialWords = titleSource.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }.take(4).joinToString(" ")
                val initialTitle = if (initialWords.isNotBlank()) initialWords else "New Chat"
                val projId = _activeProjectId.value
                val proj = if (projId != null) projectDao.getProjectById(projId) else null
                val newChat = ChatEntity(
                    title = initialTitle,
                    projectId = proj?.id,
                    projectSyncId = proj?.syncId
                )
                currentChatId = chatDao.insertChat(newChat)
                _activeChatId.value = currentChatId
            } else if (editingMsg != null) {
                val chat = chatDao.getChatById(currentChatId)
                val chatSyncId = chat?.syncId
                val allChatMessages = messageDao.getMessagesListForChat(currentChatId)
                val toDelete = allChatMessages.filter {
                    it.timestamp > editingMsg.timestamp || (it.timestamp == editingMsg.timestamp && it.id >= editingMsg.id)
                }
                val toDeleteIds = toDelete.map { it.id }.toSet()
                val keptUris = attachments.map { it.uriString }.toSet() +
                    messageDao.getAllMessages()
                        .asSequence()
                        .filter { it.id !in toDeleteIds }
                        .flatMap { it.getAttachmentList().asSequence() }
                        .map { it.uri }
                        .filter { it.isNotBlank() }
                        .toSet()
                val syncIds = toDelete.mapNotNull { it.syncId.takeIf { s -> s.isNotBlank() } }
                if (!chatSyncId.isNullOrBlank() && syncIds.isNotEmpty()) {
                    supabaseSyncManager.deleteMessagesRemotely(
                        chatSyncId = chatSyncId,
                        messageSyncIds = syncIds,
                        fromTimestamp = editingMsg.timestamp,
                        inclusive = true,
                        excludeStoragePaths = keptUris
                    )
                }
                com.wyrd.app.util.AttachmentHelper.cleanupAttachments(app, toDelete, excludeUris = keptUris)
                messageDao.deleteMessagesFrom(currentChatId, editingMsg.timestamp, editingMsg.id)
            }

            val attachmentsJson = if (attachments.isNotEmpty()) {
                com.wyrd.app.util.AttachmentHelper.serializeAttachments(attachments)
            } else null

            // Save user message to Room
            val userMsg = MessageEntity(
                chatId = currentChatId,
                role = "user",
                content = displayPrompt,
                attachmentUri = attachments.firstOrNull()?.uriString,
                attachmentType = attachments.firstOrNull()?.let { if (it.isImage) "image" else "file" },
                attachmentName = attachments.firstOrNull()?.name,
                attachmentsJson = attachmentsJson
            )
            messageDao.insertMessage(userMsg)
            chatDao.updatePinned(currentChatId, chatDao.getChatById(currentChatId)?.isPinned ?: false, System.currentTimeMillis())

            executeStream(
                chatId = currentChatId,
                apiKey = apiKey,
                isFirstMessageInChat = isFirstMessageInChat,
                firstUserPromptForTitle = prompt.ifBlank { attachments.firstOrNull()?.name }
            )
        }
    }

    fun redoMessage(fromUserMessage: MessageEntity) {
        if (_isGenerating.value) return

        val activeM = activeModel.value
        val apiKey = if (activeM.isOpenAi) {
            secureKeyStorage.getOpenAiApiKey()
        } else {
            secureKeyStorage.getXiaomiApiKey()
        }
        if (apiKey.isBlank()) {
            _errorMessage.value = if (activeM.isOpenAi) {
                "OpenAI API key is required for GPT 6.1 Sol. Please configure it in Settings."
            } else {
                "Xiaomi MiMo API key is required. Please add it in Settings."
            }
            return
        }

        hapticManager.click()
        autoReadNextAssistantMessage = false
        _editingMessage.value = null
        nativeTtsManager.stop()
        _isGenerating.value = true
        _streamingContent.value = ""
        _streamingReasoning.value = ""
        _streamingStatus.value = "Sending..."
        _errorMessage.value = null

        generationJob = app.applicationScope.launch(Dispatchers.IO) {
            val chatId = fromUserMessage.chatId
            _activeChatId.value = chatId
            val chat = chatDao.getChatById(chatId)
            val chatSyncId = chat?.syncId
            // Delete all messages after this user message
            val allChatMessages = messageDao.getMessagesListForChat(chatId)
            val toDelete = allChatMessages.filter {
                it.timestamp > fromUserMessage.timestamp || (it.timestamp == fromUserMessage.timestamp && it.id > fromUserMessage.id)
            }
            val toDeleteIds = toDelete.map { it.id }.toSet()
            val keptUris = messageDao.getAllMessages()
                .asSequence()
                .filter { it.id !in toDeleteIds }
                .flatMap { it.getAttachmentList().asSequence() }
                .map { it.uri }
                .filter { it.isNotBlank() }
                .toSet()
            val syncIds = toDelete.mapNotNull { it.syncId.takeIf { s -> s.isNotBlank() } }
            if (!chatSyncId.isNullOrBlank() && syncIds.isNotEmpty()) {
                supabaseSyncManager.deleteMessagesRemotely(
                    chatSyncId = chatSyncId,
                    messageSyncIds = syncIds,
                    fromTimestamp = fromUserMessage.timestamp,
                    inclusive = false,
                    excludeStoragePaths = keptUris
                )
            }
            com.wyrd.app.util.AttachmentHelper.cleanupAttachments(app, toDelete, excludeUris = keptUris)
            messageDao.deleteMessagesAfter(chatId, fromUserMessage.timestamp, fromUserMessage.id)
            chatDao.updatePinned(chatId, chatDao.getChatById(chatId)?.isPinned ?: false, System.currentTimeMillis())

            executeStream(
                chatId = chatId,
                apiKey = apiKey,
                isFirstMessageInChat = false,
                firstUserPromptForTitle = null
            )
        }
    }

    fun branchChat(fromUserMessage: MessageEntity) {
        if (_isGenerating.value) return
        hapticManager.click()
        autoReadNextAssistantMessage = false
        _editingMessage.value = null
        nativeTtsManager.stop()

        viewModelScope.launch(Dispatchers.IO) {
            val originalChat = chatDao.getChatById(fromUserMessage.chatId)
            val originalTitle = originalChat?.title ?: "Chat"
            val branchTitle = if (originalTitle.endsWith("(Branch)")) {
                originalTitle
            } else {
                "$originalTitle (Branch)"
            }

            val allMessages = messageDao.getMessagesListForChat(fromUserMessage.chatId)
            val targetIndex = allMessages.indexOfFirst { it.id == fromUserMessage.id }
            if (targetIndex == -1) return@launch

            val branchMessages = mutableListOf<MessageEntity>()
            for (i in 0..targetIndex) {
                branchMessages.add(allMessages[i])
            }
            if (targetIndex + 1 < allMessages.size && allMessages[targetIndex + 1].role == "assistant") {
                branchMessages.add(allMessages[targetIndex + 1])
            }

            val newChat = ChatEntity(
                title = branchTitle,
                isPinned = false,
                projectId = originalChat?.projectId,
                projectSyncId = originalChat?.projectSyncId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newChatId = chatDao.insertChat(newChat)

            val baseTime = System.currentTimeMillis() - (branchMessages.size * 1000L)
            branchMessages.forEachIndexed { index, msg ->
                messageDao.insertMessage(
                    MessageEntity(
                        chatId = newChatId,
                        role = msg.role,
                        content = msg.content,
                        reasoning = msg.reasoning,
                        modelName = msg.modelName,
                        reasoningEnabled = msg.reasoningEnabled,
                        timestamp = baseTime + (index * 1000L),
                        attachmentUri = msg.attachmentUri,
                        attachmentType = msg.attachmentType,
                        attachmentName = msg.attachmentName,
                        attachmentsJson = msg.attachmentsJson
                    )
                )
            }

            withContext(Dispatchers.Main) {
                _activeChatId.value = newChatId
                inputPrompt.value = TextFieldValue("")
                _streamingContent.value = ""
                _streamingReasoning.value = ""
                _streamingStatus.value = null
                _isGenerating.value = false
                hapticManager.success()
            }
        }
    }

    private suspend fun executeStream(
        chatId: Long,
        apiKey: String,
        isFirstMessageInChat: Boolean,
        firstUserPromptForTitle: String?
    ) {
        _isGenerating.value = true
        _streamingContent.value = ""
        _streamingReasoning.value = ""
        _streamingStatus.value = "Sending..."
        _errorMessage.value = null
        com.wyrd.app.service.WyrdBackgroundService.start(app, "Sending...")

        // Build history payload
        // The network payload only retains recent context. Querying that window
        // directly avoids loading an entire long conversation on every send.
        val previousMessages = messageDao.getRecentMessagesForChat(chatId, MAX_CONTEXT_MESSAGES)
        val payloadList = previousMessages.map { msg ->
            val attachmentList = msg.getAttachmentList()
            val imageAttachments = attachmentList.filter { it.type == "image" }
            val fileAttachments = attachmentList.filter { it.type == "file" }

            val imagesBase64 = mutableListOf<String>()
            imageAttachments.mapNotNullTo(imagesBase64) {
                if (it.uri.startsWith("http://") || it.uri.startsWith("https://")) {
                    it.uri
                } else {
                    com.wyrd.app.util.AttachmentHelper.getBase64FromPath(it.uri)
                }
            }

            // Also check for PDF documents and render pages as images for multimodal vision!
            for (fileItem in fileAttachments) {
                if (fileItem.name.endsWith(".pdf", ignoreCase = true) || fileItem.uri.endsWith(".pdf", ignoreCase = true)) {
                    val pdfPages = com.wyrd.app.util.AttachmentHelper.getPdfPageImagesBase64(fileItem.uri)
                    imagesBase64.addAll(pdfPages)
                }
            }

            val fileContexts = fileAttachments.mapNotNull { fileItem ->
                val content = com.wyrd.app.util.AttachmentHelper.getTextContentFromPath(fileItem.uri)
                if (!content.isNullOrBlank()) {
                    "[Attached File: ${fileItem.name}]\n```\n$content\n```"
                } else null
            }

            val fullContent = if (fileContexts.isNotEmpty()) {
                fileContexts.joinToString("\n\n") + "\n\n${msg.content}"
            } else {
                msg.content
            }

            ChatMessagePayload(
                role = msg.role,
                content = fullContent,
                reasoning = msg.reasoning,
                imageBase64 = imagesBase64.firstOrNull(),
                imageMimeType = if (imagesBase64.isNotEmpty()) "image/jpeg" else null,
                imagesBase64 = imagesBase64
            )
        }

        // Guarantee custom system prompt & base url are read live from DataStore
        // If chat belongs to a project, strictly override with project instructions + knowledge base files
        val currentChat = chatDao.getChatById(chatId)
        val projId = if (currentChat != null) currentChat.projectId else _activeProjectId.value
        val project = if (projId != null) projectDao.getProjectById(projId) else null

        val effectiveSystemPrompt: String
        if (project != null) {
            val pFiles = projectFileDao.getFilesByProjectList(project.id)
            val textFiles = pFiles.filter { !it.textContent.isNullOrBlank() }
            val filesContext = if (textFiles.isNotEmpty()) {
                val blocks = textFiles.joinToString("\n\n") { file ->
                    "--- File: ${file.name} ---\n${file.textContent}"
                }
                "\n\n[Project Knowledge Base]:\nThe following source files are attached to this project for context:\n$blocks"
            } else ""
            effectiveSystemPrompt = "${project.systemPrompt}$filesContext"
        } else {
            effectiveSystemPrompt = preferencesManager.systemPromptFlow.first()
        }
        val liveCustomBaseUrl = preferencesManager.customBaseUrlFlow.first()

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var lastStreamUiUpdateMs = 0L

        fun publishStreamingText(force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (force || now - lastStreamUiUpdateMs >= STREAM_UI_UPDATE_INTERVAL_MS) {
                _streamingContent.value = contentBuilder.toString()
                _streamingReasoning.value = reasoningBuilder.toString()
                lastStreamUiUpdateMs = now
            }
        }

        var lastServiceStatus: String = "Sending..."
        fun updateBackgroundServiceStatus(newStatus: String) {
            if (lastServiceStatus != newStatus) {
                lastServiceStatus = newStatus
                com.wyrd.app.service.WyrdBackgroundService.updateStatus(app, newStatus)
            }
        }

        val liveExaApiKey = secureKeyStorage.getExaApiKey().ifBlank { null }
        val isExaEnabled = isExaSearchEnabled.value
        val liveTavilyApiKey = secureKeyStorage.getTavilyApiKey().ifBlank { null }
        val isTavilyEnabled = isTavilySearchEnabled.value && !liveTavilyApiKey.isNullOrBlank()

        isTtsStreamingRequested = autoReadNextAssistantMessage
        var isTtsStreamingActive = false
        var ttsSpokenPlainCursor = 0

        fun processStreamingTtsChunk() {
            if (!isTtsStreamingRequested) return
            if (!isTtsStreamingActive) {
                isTtsStreamingActive = true
                ttsSpokenPlainCursor = 0
                val userPromptText = payloadList.lastOrNull { it.role == "user" }?.content
                    ?: firstUserPromptForTitle ?: ""
                val promptLocale = nativeTtsManager.detectLanguage(userPromptText)
                nativeTtsManager.startStreaming(
                    com.wyrd.app.data.tts.NativeTtsManager.STREAMING_MESSAGE_ID,
                    languageHint = promptLocale
                )
            }

            while (true) {
                val currentMarkdown = contentBuilder.toString()
                val plainText = formatMarkdownForReading(currentMarkdown).plainText
                val safeCursor = ttsSpokenPlainCursor.coerceIn(0, plainText.length)
                if (plainText.length <= safeCursor) break

                val unvoiced = plainText.substring(safeCursor)
                val cutLength = findStreamingCutPoint(unvoiced, isFirstChunk = (safeCursor == 0))
                if (cutLength > 0) {
                    val chunkText = unvoiced.substring(0, cutLength).trim()
                    val globalOffset = safeCursor
                    ttsSpokenPlainCursor = safeCursor + cutLength
                    if (chunkText.isNotBlank()) {
                        nativeTtsManager.enqueueStreamingChunk(chunkText, globalOffset, fullTextSoFar = plainText)
                    }
                } else {
                    break
                }
            }
        }

        currentStreamTtsCallback = {
            processStreamingTtsChunk()
        }

        try {
            mimoService.streamChatCompletion(
                apiKey = apiKey,
                model = activeModel.value,
                isThinkingEnabled = isThinkingEnabled.value,
                systemPrompt = effectiveSystemPrompt,
                messages = payloadList,
                exaSearchService = exaSearchService,
                exaApiKey = liveExaApiKey,
                isExaEnabled = isExaEnabled,
                tavilySearchService = if (isTavilyEnabled) tavilySearchService else null,
                tavilyApiKey = liveTavilyApiKey,
                customBaseUrl = liveCustomBaseUrl
            ).collect { event ->
                when (event) {
                    is com.wyrd.app.data.network.StreamEvent.Content -> {
                        _streamingStatus.value = null
                        contentBuilder.append(event.text)
                        publishStreamingText()
                        hapticManager.streamTokenTick()
                        updateBackgroundServiceStatus("Generating response...")
                        if (isTtsStreamingRequested) {
                            processStreamingTtsChunk()
                        }
                    }
                    is com.wyrd.app.data.network.StreamEvent.Reasoning -> {
                        _streamingStatus.value = null
                        reasoningBuilder.append(event.text)
                        publishStreamingText()
                        hapticManager.streamTokenTick()
                        updateBackgroundServiceStatus("Thinking...")
                    }
                    is com.wyrd.app.data.network.StreamEvent.Status -> {
                        publishStreamingText(force = true)
                        _streamingStatus.value = event.status
                        updateBackgroundServiceStatus(event.status)
                    }
                    is com.wyrd.app.data.network.StreamEvent.ClearContent -> {
                        contentBuilder.clear()
                        _streamingContent.value = ""
                        if (isTtsStreamingActive) {
                            nativeTtsManager.stop()
                            isTtsStreamingActive = false
                            ttsSpokenPlainCursor = 0
                        }
                        if (reasoningBuilder.isNotEmpty() && !reasoningBuilder.endsWith("\n\n")) {
                            reasoningBuilder.append("\n\n")
                        }
                        publishStreamingText(force = true)
                    }
                }
            }

            // Streaming finished cleanly, save assistant message
            publishStreamingText(force = true)
            val rawContent = contentBuilder.toString().trim()
            val rawReasoning = if (isThinkingEnabled.value) reasoningBuilder.toString().trim().ifBlank { null } else null
            val finalContent = sanitizeStreamedContent(rawContent)
            val finalReasoning = rawReasoning?.let { sanitizeReasoning(it) }

            if (finalContent.isNotBlank() || finalReasoning != null) {
                // Preserve reasoning in reasoning column; never wipe or promote to content!
                val effectiveContent = if (finalContent.isNotBlank()) finalContent else "..."
                val effectiveReasoning = finalReasoning

                var targetChatId = chatId
                if (chatDao.getChatById(targetChatId) == null) {
                    val title = firstUserPromptForTitle?.take(30) ?: "Chat"
                    targetChatId = chatDao.insertChat(ChatEntity(title = title))
                    _activeChatId.value = targetChatId
                }

                val assistantMsg = MessageEntity(
                    chatId = targetChatId,
                    role = "assistant",
                    content = effectiveContent,
                    reasoning = effectiveReasoning,
                    modelName = activeModel.value.id,
                    reasoningEnabled = isThinkingEnabled.value
                )
                val assistantMsgId = try {
                    messageDao.insertMessage(assistantMsg)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to insert assistant message into chat $targetChatId", e)
                    val fallbackId = chatDao.insertChat(ChatEntity(title = "Chat"))
                    _activeChatId.value = fallbackId
                    messageDao.insertMessage(assistantMsg.copy(chatId = fallbackId))
                }

                if (supabaseSyncManager.isUserSignedIn()) {
                    app.applicationScope.launch(Dispatchers.IO) {
                        try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
                    }
                }

                _streamingContent.value = ""
                _streamingReasoning.value = ""
                _streamingStatus.value = null
                _isGenerating.value = false

                // Brief pause (~120ms) after stream ticks cease, then a firm single snap
                app.applicationScope.launch(Dispatchers.Main) {
                    delay(120L)
                    hapticManager.completionSnap()
                }

                if (isTtsStreamingRequested) {
                    isTtsStreamingRequested = false
                    autoReadNextAssistantMessage = false
                    val readingContent = formatMarkdownForReading(effectiveContent)
                    val finalPlain = readingContent.plainText
                    val isPaused = nativeTtsManager.playbackState.value.isPaused
                    if (isTtsStreamingActive) {
                        val safeCursor = ttsSpokenPlainCursor.coerceIn(0, finalPlain.length)
                        if (finalPlain.length > safeCursor && !isPaused) {
                            val remaining = finalPlain.substring(safeCursor).trim()
                            if (remaining.isNotBlank()) {
                                nativeTtsManager.enqueueStreamingChunk(remaining, safeCursor, fullTextSoFar = finalPlain)
                            }
                        }
                        nativeTtsManager.finishStreaming(assistantMsgId, finalPlain)
                    } else if (finalPlain.isNotBlank() && effectiveContent != "..." && !isPaused) {
                        withContext(Dispatchers.Main) {
                            nativeTtsManager.speak(assistantMsgId, finalPlain, 0)
                        }
                    }
                }
                currentStreamTtsCallback = null

                // Generate clean title in background AFTER stream completes, with zero competition
                if (isFirstMessageInChat && firstUserPromptForTitle != null && effectiveContent.isNotBlank() && effectiveContent != "...") {
                    val currentModel = activeModel.value
                    val finalTargetChatId = targetChatId
                    app.applicationScope.launch(Dispatchers.IO) {
                        try {
                            val summaryTitle = mimoService.generateTitle(
                                apiKey = apiKey,
                                model = currentModel,
                                firstUserPrompt = firstUserPromptForTitle,
                                assistantReply = effectiveContent,
                                customBaseUrl = liveCustomBaseUrl
                            )
                            val cleanTitle = mimoService.sanitizeChatTitle(summaryTitle)
                            if (cleanTitle.isNotBlank() && cleanTitle != "New Chat") {
                                chatDao.updateTitle(finalTargetChatId, cleanTitle, System.currentTimeMillis())
                                if (supabaseSyncManager.isUserSignedIn()) {
                                    try { supabaseSyncManager.syncAll() } catch (_: Exception) {}
                                }
                            }
                        } catch (e: Exception) {
                            // Keep initial 3-4 word title
                        }
                    }
                }
            } else {
                val providerName = if (activeModel.value.isOpenAi) "OpenAI" else "Xiaomi MiMo"
                throw IllegalStateException("Empty response received from $providerName server")
            }
        } catch (e: CancellationException) {
            Log.d("MainViewModel", "LLM generation cancelled by user")
            autoReadNextAssistantMessage = false
            isTtsStreamingRequested = false
            currentStreamTtsCallback = null
            withContext(NonCancellable) {
                val rawContent = contentBuilder.toString().trim()
                val rawReasoning = if (isThinkingEnabled.value) reasoningBuilder.toString().trim().ifBlank { null } else null
                val partialContent = sanitizeStreamedContent(rawContent)
                val partialReasoning = rawReasoning?.let { sanitizeReasoning(it) }

                if (partialContent.isNotBlank() || partialReasoning != null) {
                    val effectiveContent = if (partialContent.isNotBlank()) partialContent else "..."
                    val effectiveReasoning = partialReasoning

                    var targetChatId = chatId
                    if (chatDao.getChatById(targetChatId) == null) {
                        targetChatId = chatDao.insertChat(ChatEntity(title = "Chat"))
                        _activeChatId.value = targetChatId
                    }

                    val assistantMsg = MessageEntity(
                        chatId = targetChatId,
                        role = "assistant",
                        content = effectiveContent,
                        reasoning = effectiveReasoning,
                        modelName = activeModel.value.id,
                        reasoningEnabled = isThinkingEnabled.value
                    )
                    try { messageDao.insertMessage(assistantMsg) } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to insert partial message", e)
                    }
                }
            }
        } catch (e: Exception) {
            autoReadNextAssistantMessage = false
            isTtsStreamingRequested = false
            currentStreamTtsCallback = null
            if (isTtsStreamingActive) {
                nativeTtsManager.stop()
            }
            val providerName = if (activeModel.value.isOpenAi) "OpenAI" else "Xiaomi MiMo"
            val errorMsg = e.message ?: "Failed to receive response from $providerName"
            Log.e("MainViewModel", "LLM completion failure: $errorMsg", e)
            _errorMessage.value = errorMsg

            val rawContent = contentBuilder.toString().trim()
            val rawReasoning = if (isThinkingEnabled.value) reasoningBuilder.toString().trim().ifBlank { null } else null
            val partialContent = sanitizeStreamedContent(rawContent)
            val partialReasoning = rawReasoning?.let { sanitizeReasoning(it) }

            val displayError = if (partialContent.isNotBlank()) {
                "$partialContent\n\n**Error:** $errorMsg"
            } else {
                "**Error:** $errorMsg"
            }

            var targetChatId = chatId
            if (chatDao.getChatById(targetChatId) == null) {
                targetChatId = chatDao.insertChat(ChatEntity(title = "Chat"))
                _activeChatId.value = targetChatId
            }

            val assistantMsg = MessageEntity(
                chatId = targetChatId,
                role = "assistant",
                content = displayError,
                reasoning = partialReasoning,
                modelName = activeModel.value.id,
                reasoningEnabled = isThinkingEnabled.value
            )
            try { messageDao.insertMessage(assistantMsg) } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to insert error message", e)
            }
        } finally {
            withContext(NonCancellable) {
                isTtsStreamingRequested = false
                currentStreamTtsCallback = null
                _streamingContent.value = ""
                _streamingReasoning.value = ""
                _streamingStatus.value = null
                _isGenerating.value = false
                generationJob = null
                com.wyrd.app.service.WyrdBackgroundService.stop(app)
            }
        }
    }

    fun copyToClipboard(text: String) {
        hapticManager.click()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WYRD", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(app, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun playTts(messageId: Long, text: String, startOffset: Int = 0) {
        hapticManager.click()
        nativeTtsManager.speak(messageId, text, startOffset)
    }

    fun startStreamingTts() {
        hapticManager.click()
        isTtsStreamingRequested = true
        currentStreamTtsCallback?.invoke()
    }

    fun pauseTts() {
        hapticManager.click()
        nativeTtsManager.pause()
    }

    fun resumeTts() {
        hapticManager.click()
        nativeTtsManager.resume()
    }

    fun stopTts() {
        hapticManager.click()
        nativeTtsManager.stop()
    }

    fun seekTts(messageId: Long, text: String, charOffset: Int) {
        hapticManager.tick()
        nativeTtsManager.seekTo(messageId, text, charOffset)
    }

    fun toggleSpeech(messageId: Long, text: String) {
        hapticManager.click()
        val state = nativeTtsManager.playbackState.value
        if (state.messageId == messageId) {
            if (state.isPlaying) {
                nativeTtsManager.pause()
            } else if (state.isPaused) {
                nativeTtsManager.resume()
            } else {
                nativeTtsManager.speak(messageId, text)
            }
        } else {
            nativeTtsManager.stop()
            nativeTtsManager.speak(messageId, text)
        }
    }

    fun setTtsEngine(engine: String) {
        viewModelScope.launch {
            preferencesManager.setTtsEngine(engine)
            nativeTtsManager.setEngine(engine)
        }
    }

    fun setTtsVoice(voice: String) {
        viewModelScope.launch {
            preferencesManager.setTtsVoice(voice)
            nativeTtsManager.setPreferredVoice(voice)
        }
    }

    fun testTts(sampleText: String = "WYRD text to speech is working smoothly.") {
        hapticManager.click()
        val state = nativeTtsManager.playbackState.value
        if (state.isPlaying && state.messageId == -1L) {
            nativeTtsManager.stop()
        } else {
            nativeTtsManager.stop()
            nativeTtsManager.speak(messageId = -1L, text = sampleText)
        }
    }

    // STT Recording
    fun toggleRecording(onRecordPermissionNeeded: () -> Unit) {
        hapticManager.heavyClick()
        if (_isRecordingAudio.value) {
            stopRecordingAndTranscribe(autoSend = false)
        } else {
            startRecording(onRecordPermissionNeeded)
        }
    }

    fun stopRecording(autoSend: Boolean) {
        hapticManager.heavyClick()
        if (_isRecordingAudio.value) {
            stopRecordingAndTranscribe(autoSend = autoSend)
        }
    }

    private fun startRecording(onRecordPermissionNeeded: () -> Unit) {
        val openAiKey = secureKeyStorage.getOpenAiApiKey()
        if (openAiKey.isBlank()) {
            _errorMessage.value = "OpenAI API key is missing for STT. Please configure it in Settings."
            return
        }

        nativeTtsManager.stop()
        val started = audioRecorderHelper.startRecording(viewModelScope)
        if (started) {
            _isRecordingAudio.value = true
        } else {
            onRecordPermissionNeeded()
        }
    }

    fun cancelRecording() {
        hapticManager.click()
        autoReadNextAssistantMessage = false
        _isRecordingAudio.value = false
        _isTranscribing.value = false
        transcriptionJob?.cancel()
        transcriptionJob = null
        audioRecorderHelper.cancelRecording()
    }

    private fun stopRecordingAndTranscribe(autoSend: Boolean = false) {
        _isRecordingAudio.value = false
        autoReadNextAssistantMessage = autoSend
        val wavBytes = audioRecorderHelper.stopRecording() ?: run {
            autoReadNextAssistantMessage = false
            return
        }

        _isTranscribing.value = true
        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch(Dispatchers.IO) {
            val key = secureKeyStorage.getOpenAiApiKey()
            val result = openAiSttService.transcribeAudio(key, wavBytes)
            result.onSuccess { transcript ->
                if (transcript.isNotBlank()) {
                    hapticManager.success()
                    val existing = inputPrompt.value.text
                    val newText = if (existing.isBlank()) transcript.trim() else "${existing.trim()} ${transcript.trim()}"
                    withContext(Dispatchers.Main) {
                        inputPrompt.value = androidx.compose.ui.text.input.TextFieldValue(
                            text = newText,
                            selection = androidx.compose.ui.text.TextRange(newText.length)
                        )
                        if (autoSend) {
                            sendMessage(autoRead = true)
                        }
                    }
                } else {
                    autoReadNextAssistantMessage = false
                }
            }.onFailure { err ->
                autoReadNextAssistantMessage = false
                if (err !is CancellationException) {
                    _errorMessage.value = err.message ?: "Transcription failed"
                }
            }
            _isTranscribing.value = false
            transcriptionJob = null
        }
    }

    // Settings actions
    fun setUsername(newUsername: String) {
        viewModelScope.launch {
            preferencesManager.setUsername(newUsername)
        }
    }

    fun setSystemPrompt(newPrompt: String) {
        viewModelScope.launch {
            preferencesManager.setSystemPrompt(newPrompt)
        }
    }

    fun setXiaomiMimoEnabled(enabled: Boolean, onNeedsKey: () -> Unit) {
        val key = secureKeyStorage.getXiaomiApiKey()
        if (enabled && key.isBlank()) {
            onNeedsKey()
            return
        }
        viewModelScope.launch {
            preferencesManager.setXiaomiMimoEnabled(enabled)
        }
    }

    fun setOpenAiSttEnabled(enabled: Boolean, onNeedsKey: () -> Unit) {
        val key = secureKeyStorage.getOpenAiApiKey()
        if (enabled && key.isBlank()) {
            onNeedsKey()
            return
        }
        viewModelScope.launch {
            preferencesManager.setOpenAiSttEnabled(enabled)
        }
    }

    fun getXiaomiApiKey(): String = secureKeyStorage.getXiaomiApiKey()

    fun setXiaomiApiKey(key: String) {
        secureKeyStorage.setXiaomiApiKey(key)
        if (key.isNotBlank()) {
            viewModelScope.launch {
                preferencesManager.setXiaomiMimoEnabled(true)
            }
        }
    }

    fun setCustomBaseUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.setCustomBaseUrl(url)
        }
    }

    fun getOpenAiApiKey(): String = secureKeyStorage.getOpenAiApiKey()

    fun setOpenAiApiKey(key: String) {
        secureKeyStorage.setOpenAiApiKey(key)
        if (key.isNotBlank()) {
            viewModelScope.launch {
                preferencesManager.setOpenAiSttEnabled(true)
            }
        }
    }

    fun setExaSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setExaSearchEnabled(enabled)
        }
    }

    fun getExaApiKey(): String = secureKeyStorage.getExaApiKey()

    fun setExaApiKey(key: String) {
        secureKeyStorage.setExaApiKey(key)
    }

    fun setTavilySearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setTavilySearchEnabled(enabled)
        }
    }

    fun getTavilyApiKey(): String = secureKeyStorage.getTavilyApiKey()

    fun setTavilyApiKey(key: String) {
        secureKeyStorage.setTavilyApiKey(key)
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            preferencesManager.setAppTheme(theme)
        }
    }

    fun signInToSupabase(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = supabaseSyncManager.signIn(email, pass)
            _isSyncing.value = false
            if (result.isSuccess) {
                _supabaseEmail.value = result.getOrNull()
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun signUpToSupabase(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = supabaseSyncManager.signUp(email, pass)
            _isSyncing.value = false
            if (result.isSuccess) {
                _supabaseEmail.value = supabaseSyncManager.getUserEmail().ifBlank { null }
                onResult(true, result.getOrNull())
            } else {
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun signOutFromSupabase() {
        supabaseSyncManager.signOut()
        _supabaseEmail.value = null
    }

    fun syncWithCloud(onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = supabaseSyncManager.syncAll()
            _isSyncing.value = false
            if (!supabaseSyncManager.isUserSignedIn()) {
                _supabaseEmail.value = null
            }
            if (res.isSuccess) {
                onComplete(true, "Synchronized successfully")
            } else {
                onComplete(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun resetAllAppData(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.database.clearAllTables()
                secureKeyStorage.clearAll()
                preferencesManager.clearAll()
                app.cacheDir.deleteRecursively()
            } catch (e: Throwable) {
                android.util.Log.e("MainViewModel", "Failed to reset app data", e)
            }
            withContext(Dispatchers.Main) {
                _activeChatId.value = null
                _streamingContent.value = ""
                _streamingReasoning.value = ""
                _streamingStatus.value = null
                _isGenerating.value = false
                nativeTtsManager.stop()
                hapticManager.success()
                onComplete()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        com.wyrd.app.service.WyrdBackgroundService.stopCallback = null
    }

    private fun findStreamingCutPoint(unvoiced: String, isFirstChunk: Boolean): Int {
        if (unvoiced.isEmpty()) return 0

        // Priority 1: Sentence boundaries (. , ! , ? , .\n, !\n, ?\n, or \n\n)
        val sentenceMatch = SENTENCE_BOUNDARY_REGEX.find(unvoiced)
        if (sentenceMatch != null) {
            return sentenceMatch.range.last + 1
        }

        // Priority 2: For first chunk, allow earlier clause break (: , ; , , , or \n) if at least 8 chars
        if (isFirstChunk) {
            val clauseMatch = CLAUSE_BOUNDARY_REGEX.find(unvoiced)
            if (clauseMatch != null && clauseMatch.range.first >= 8) {
                return clauseMatch.range.last + 1
            }

            // If no punctuation yet, but we've accumulated at least 35 chars, break at nearest word boundary
            if (unvoiced.length >= 35) {
                val spaceIndex = unvoiced.lastIndexOf(' ', 45.coerceAtMost(unvoiced.length - 1))
                if (spaceIndex >= 15) {
                    return spaceIndex + 1
                }
            }
        } else {
            // Subsequent chunks: if sentence is very long (> 120 chars) without terminal punctuation, break at clause or word
            if (unvoiced.length >= 120) {
                val clauseMatch = CLAUSE_BOUNDARY_REGEX.find(unvoiced, startIndex = 50)
                if (clauseMatch != null) {
                    return clauseMatch.range.last + 1
                }
                val spaceIndex = unvoiced.lastIndexOf(' ', 140.coerceAtMost(unvoiced.length - 1))
                if (spaceIndex >= 70) {
                    return spaceIndex + 1
                }
            }
        }

        return 0
    }

    private fun sanitizeStreamedContent(text: String): String {
        var cleaned = text
        cleaned = cleaned.replace(THINK_BLOCK_REGEX, "")
        cleaned = cleaned.replace(THOUGHT_BLOCK_REGEX, "")
        cleaned = cleaned.replace(THINKING_BLOCK_REGEX, "")
        cleaned = cleaned.replace(TOOL_CALL_BLOCK_REGEX, "")
        cleaned = cleaned.replace(FUNCTION_CALL_BLOCK_REGEX, "")
        cleaned = cleaned.replace(RESIDUAL_TAGS_REGEX, "")
        return cleaned.trim()
    }

    private fun sanitizeReasoning(text: String): String {
        return text.replace(REASONING_TAGS_REGEX, "").trim()
    }

    private companion object {
        const val MAX_CONTEXT_MESSAGES = 30
        const val STREAM_UI_UPDATE_INTERVAL_MS = 48L
        val WHITESPACE_REGEX = Regex("\\s+")
        val SENTENCE_BOUNDARY_REGEX = Regex("""[.!?](\s|\n)|(\n\n)""")
        val CLAUSE_BOUNDARY_REGEX = Regex("""([:;,\n])\s*""")
        val THINK_BLOCK_REGEX = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)
        val THOUGHT_BLOCK_REGEX = Regex("""<thought>[\s\S]*?</thought>""", RegexOption.IGNORE_CASE)
        val THINKING_BLOCK_REGEX = Regex("""<thinking>[\s\S]*?</thinking>""", RegexOption.IGNORE_CASE)
        val TOOL_CALL_BLOCK_REGEX = Regex("""<tool_call>[\s\S]*?</tool_call>""", RegexOption.IGNORE_CASE)
        val FUNCTION_CALL_BLOCK_REGEX = Regex("""<function_call>[\s\S]*?</function_call>""", RegexOption.IGNORE_CASE)
        val RESIDUAL_TAGS_REGEX = Regex("""</?(?:think|thought|thinking|tool_call|function_call|function|parameter)[^>]*>""", RegexOption.IGNORE_CASE)
        val REASONING_TAGS_REGEX = Regex("""</?(?:think|thought|thinking)[^>]*>""", RegexOption.IGNORE_CASE)
    }
}
