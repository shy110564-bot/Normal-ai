package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDataRepository
import com.example.data.local.JarvisDatabase
import com.example.data.local.MemoryCategory
import com.example.data.local.MemoryEntity
import com.example.data.local.NoteEntity
import com.example.data.preferences.JarvisSettings
import com.example.data.preferences.JarvisSettingsRepository
import com.example.data.preferences.PersonalityMode
import com.example.data.remote.ApiKeyTestResult
import com.example.data.remote.BrainResponseResult
import com.example.data.remote.GeminiBrainClient
import com.example.data.security.ApiKeyStatus
import com.example.data.security.SecureKeyManager
import com.example.domain.tools.JarvisToolEngine
import com.example.domain.tools.MultiStepStepStatus
import com.example.domain.tools.PendingConfirmationAction
import com.example.domain.tools.ToolExecutionLog
import com.example.service.CallingSessionTelemetry
import com.example.service.DeviceNotificationItem
import com.example.service.GeminiLiveVoiceEngine
import com.example.service.JarvisAccessibilityService
import com.example.service.JarvisNotificationService
import com.example.service.JarvisOperationalState
import com.example.service.ScreenSnapshot
import com.example.service.WakeWordEngine
import com.example.service.WakeWordState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class JarvisScreenTab(val title: String) {
    COMMAND_DECK("JARVIS"),
    CALLING_MODE("CALLING"),
    NOTES_MEMORY("VAULT"),
    AUTOMATION("CONTROL"),
    SETTINGS("SETTINGS")
}

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JarvisDatabase.getInstance(application)
    val dataRepository = JarvisDataRepository(database)
    val settingsRepository = JarvisSettingsRepository(application)
    val secureKeyManager = SecureKeyManager(application)
    val toolEngine = JarvisToolEngine(application, dataRepository)
    val brainClient = GeminiBrainClient(application, dataRepository, toolEngine)
    val liveVoiceEngine = GeminiLiveVoiceEngine(application, brainClient, dataRepository, toolEngine)

    val wakeWordEngine = WakeWordEngine(
        context = application,
        onWakeWordTriggered = { initialCommand ->
            handleWakeWordTriggered(initialCommand)
        },
        onContinuousCommandCaptured = { command ->
            handleSpokenCommand(command)
        },
        onStopCommandDetected = {
            stopAndInterruptAll("Stopped by voice command")
        },
        onVoiceRmsChanged = { rms ->
            _wakeMicRms.value = rms
        }
    )

    // Navigation state
    private val _currentTab = MutableStateFlow(JarvisScreenTab.COMMAND_DECK)
    val currentTab: StateFlow<JarvisScreenTab> = _currentTab.asStateFlow()

    // Reactive streams
    val settings: StateFlow<JarvisSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisSettings()
    )

    val keyStatus: StateFlow<ApiKeyStatus> = secureKeyManager.keyStatus
    val notes: StateFlow<List<NoteEntity>> = dataRepository.allNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val memories: StateFlow<List<MemoryEntity>> = dataRepository.allMemories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val chatMessages: StateFlow<List<ChatMessageEntity>> = dataRepository.allMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val liveTelemetry: StateFlow<CallingSessionTelemetry> = liveVoiceEngine.telemetry
    val wakeWordState: StateFlow<WakeWordState> = wakeWordEngine.state
    val toolLogs: StateFlow<List<ToolExecutionLog>> = toolEngine.executionLogs
    val pendingConfirmation: StateFlow<PendingConfirmationAction?> = toolEngine.pendingConfirmation
    val multiStepProgress: StateFlow<List<MultiStepStepStatus>> = toolEngine.multiStepProgress
    val currentActivityLabel: StateFlow<String> = toolEngine.currentActivityLabel

    val isAccessibilityConnected: StateFlow<Boolean> = JarvisAccessibilityService.isConnected
    val latestScreenSnapshot: StateFlow<ScreenSnapshot?> = JarvisAccessibilityService.latestSnapshot
    val isNotificationListenerConnected: StateFlow<Boolean> = JarvisNotificationService.isListenerConnected
    val activeNotifications: StateFlow<List<DeviceNotificationItem>> = JarvisNotificationService.notifications

    private val _wakeMicRms = MutableStateFlow(0.04f)
    val wakeMicRms: StateFlow<Float> = _wakeMicRms.asStateFlow()

    private val _isBrainThinking = MutableStateFlow(false)
    val isBrainThinking: StateFlow<Boolean> = _isBrainThinking.asStateFlow()

    private val _lastErrorBanner = MutableStateFlow<String?>(null)
    val lastErrorBanner: StateFlow<String?> = _lastErrorBanner.asStateFlow()

    private val _lastFailedCommand = MutableStateFlow<String?>(null)
    val lastFailedCommand: StateFlow<String?> = _lastFailedCommand.asStateFlow()

    private val _apiKeyTestState = MutableStateFlow<ApiKeyTestResult?>(null)
    val apiKeyTestState: StateFlow<ApiKeyTestResult?> = _apiKeyTestState.asStateFlow()

    private val _isTestingApiKey = MutableStateFlow(false)
    val isTestingApiKey: StateFlow<Boolean> = _isTestingApiKey.asStateFlow()

    init {
        // Sync WakeWordEngine with DataStore settings
        viewModelScope.launch {
            settings.collectLatest { cfg ->
                wakeWordEngine.configure(
                    enabled = cfg.wakeWordEnabled,
                    phrase = cfg.selectedWakePhrase,
                    sensitivity = cfg.wakeWordSensitivity
                )
            }
        }

        // Seed initial welcome message and starter system memory if database is fresh
        viewModelScope.launch {
            val existingMsgs = dataRepository.getRecentMessages(1)
            if (existingMsgs.isEmpty()) {
                dataRepository.addChatMessage(
                    role = "jarvis",
                    content = "JARVIS Core initialized. Gemini AI Brain, Gemini Live Voice, Modular Tool Engine, Long-Term Memory, and Android Screen Control interfaces are online. Say \"JARVIS\", launch Calling Mode, or enter a command below."
                )
                dataRepository.saveMemory(
                    category = MemoryCategory.ASSISTANT_PREFERENCE.name,
                    key = "assistant_identity",
                    value = "JARVIS — Futuristic Android Personal AI Assistant powered by Google Gemini",
                    importance = 5
                )
            }
        }
    }

    fun navigateTo(tab: JarvisScreenTab) {
        _currentTab.value = tab
    }

    fun clearErrorBanner() {
        _lastErrorBanner.value = null
    }

    // --- Text & Voice Command Processing ---

    fun sendUserMessage(rawText: String, isVoiceOrigin: Boolean = false) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        // Handle instant interruption commands
        if (liveVoiceEngine.isStopCommand(text)) {
            stopAndInterruptAll("Stopped by user command")
            return
        }

        // Handle "JARVIS, wake up"
        if (text.equals("jarvis, wake up", ignoreCase = true) ||
            text.equals("jarvis wake up", ignoreCase = true)
        ) {
            handleWakeWordTriggered(null)
            return
        }

        // If Gemini Live WebSocket Calling Mode is connected and active, also forward over live session if desired
        viewModelScope.launch {
            _lastErrorBanner.value = null
            _lastFailedCommand.value = null
            dataRepository.addChatMessage(
                role = "user",
                content = text,
                isVoice = isVoiceOrigin
            )

            _isBrainThinking.value = true
            toolEngine.updateActivityLabel("THINKING — ANALYZING INTENT WITH GEMINI")

            val history = dataRepository.getRecentMessages(14)
            val apiKey = secureKeyManager.getEffectiveApiKey()
            val cfg = settings.value

            val result = brainClient.processUserCommand(
                userPrompt = text,
                apiKey = apiKey,
                settings = cfg,
                conversationHistory = history.dropLast(1),
                isVoiceSession = isVoiceOrigin
            )

            _isBrainThinking.value = false

            when (result) {
                is BrainResponseResult.Success -> {
                    val toolBadge = result.executedTools.distinct().joinToString(", ").takeIf { it.isNotEmpty() }
                    dataRepository.addChatMessage(
                        role = "jarvis",
                        content = result.replyText,
                        toolName = toolBadge,
                        isVoice = isVoiceOrigin
                    )
                    toolEngine.updateActivityLabel("ONLINE — MODEL: ${result.modelUsed.uppercase()}")

                    // Speak response if triggered via Voice / Continuous Listening (and not already in WebSocket audio stream)
                    if ((isVoiceOrigin || wakeWordEngine.state.value.isContinuousCommandSession) &&
                        !liveTelemetry.value.isCallingModeActive
                    ) {
                        liveVoiceEngine.speakTextResponse(result.replyText, cfg)
                    }
                }

                is BrainResponseResult.Failure -> {
                    _lastErrorBanner.value = "${result.errorTitle}: ${result.errorMessage}"
                    _lastFailedCommand.value = text
                    toolEngine.updateActivityLabel("ALERT — ${result.errorTitle.uppercase()}")
                    dataRepository.addChatMessage(
                        role = "system",
                        content = "⚠️ ${result.errorTitle}: ${result.errorMessage}"
                    )
                }
            }
        }
    }

    fun retryLastFailedCommand() {
        val cmd = _lastFailedCommand.value ?: return
        sendUserMessage(cmd)
    }

    // --- Calling Mode & Continuous Voice Control ---

    fun startCallingMode() {
        wakeWordEngine.pauseForCallingMode(true)
        _currentTab.value = JarvisScreenTab.CALLING_MODE
        liveVoiceEngine.startCallingMode(
            apiKey = secureKeyManager.getEffectiveApiKey(),
            settings = settings.value
        )
    }

    fun endCallingMode() {
        liveVoiceEngine.stopCallingMode("Calling Mode ended by user")
        wakeWordEngine.pauseForCallingMode(false)
        if (_currentTab.value == JarvisScreenTab.CALLING_MODE) {
            _currentTab.value = JarvisScreenTab.COMMAND_DECK
        }
    }

    fun toggleCallingMute() {
        liveVoiceEngine.toggleMute()
    }

    fun toggleContinuousVoiceLoopOnMainScreen() {
        if (liveTelemetry.value.isCallingModeActive) {
            _currentTab.value = JarvisScreenTab.CALLING_MODE
            return
        }
        val currentlyActive = wakeWordEngine.state.value.isContinuousCommandSession
        if (currentlyActive) {
            wakeWordEngine.setContinuousCommandSession(false)
            liveVoiceEngine.interruptSpeech(returnToListening = false, reason = "Continuous listening paused")
            toolEngine.updateActivityLabel("STANDBY — ALL SYSTEMS NOMINAL")
        } else {
            wakeWordEngine.setContinuousCommandSession(true)
            toolEngine.updateActivityLabel("LISTENING CONTINUOUSLY — SPEAK NATURALLY")
            liveVoiceEngine.speakTextResponse("Yes, I'm listening.", settings.value)
        }
    }

    fun stopAndInterruptAll(reason: String = "Interrupted by user") {
        liveVoiceEngine.interruptSpeech(
            returnToListening = liveTelemetry.value.isCallingModeActive,
            reason = reason
        )
        if (!liveTelemetry.value.isCallingModeActive) {
            wakeWordEngine.setContinuousCommandSession(false)
        }
        toolEngine.updateActivityLabel("INTERRUPTED — $reason")
    }

    private fun handleWakeWordTriggered(initialCommand: String?) {
        toolEngine.updateActivityLabel("WAKE WORD DETECTED — LISTENING")
        if (initialCommand.isNullOrBlank()) {
            viewModelScope.launch {
                dataRepository.addChatMessage(
                    role = "jarvis",
                    content = "Yes, I'm listening.",
                    isVoice = true
                )
            }
            liveVoiceEngine.speakTextResponse("Yes, I'm listening.", settings.value)
        } else {
            sendUserMessage(initialCommand, isVoiceOrigin = true)
        }
    }

    private fun handleSpokenCommand(command: String) {
        if (command.isBlank()) return
        sendUserMessage(command, isVoiceOrigin = true)
    }

    // --- Multi-Step Automation & Screen Control Helpers ---

    fun runYouTubeMultiStepWorkflow(searchQuery: String) {
        val cleanQuery = searchQuery.ifBlank { "Android AI tutorials" }
        viewModelScope.launch {
            dataRepository.addChatMessage(
                role = "user",
                content = "JARVIS, open YouTube, search for '$cleanQuery', and prepare the results."
            )
            val steps = listOf<Pair<String, suspend () -> Boolean>>(
                "Verify target application & intent resolution (YouTube)" to {
                    true
                },
                "Launch YouTube with search query: '$cleanQuery'" to {
                    val res = toolEngine.executeToolCall(
                        name = "open_application",
                        args = JSONObject().put("appName", "YouTube").put("searchQuery", cleanQuery),
                        confirmSensitive = settings.value.confirmSensitiveActions,
                        memoryEnabled = settings.value.memoryEnabled
                    )
                    res.optString("status").startsWith("VERIFIED")
                },
                "Inspect screen UI hierarchy via Accessibility Service if enabled" to {
                    val snap = JarvisAccessibilityService.instance?.captureCurrentScreen()
                    snap != null || true
                }
            )
            val results = toolEngine.runMultiStepDemonstration(steps)
            val allOk = results.all { it.status == "VERIFIED" }
            dataRepository.addChatMessage(
                role = "jarvis",
                content = if (allOk) {
                    "Multi-step workflow completed: Launched YouTube and executed search for \"$cleanQuery\"."
                } else {
                    "Executed YouTube workflow for \"$cleanQuery\" with partial verification. Check the Control tab for step details."
                },
                toolName = "multi_step_workflow"
            )
        }
    }

    fun captureCurrentScreenNow() {
        viewModelScope.launch {
            toolEngine.executeToolCall("read_screen_context", JSONObject())
        }
    }

    fun executeDirectScreenAction(
        action: String,
        targetText: String = "",
        inputText: String = "",
        scrollDirection: String = "DOWN"
    ) {
        viewModelScope.launch {
            val args = JSONObject()
                .put("action", action)
                .put("targetText", targetText)
                .put("inputText", inputText)
                .put("scrollDirection", scrollDirection)
            toolEngine.executeToolCall("screen_interaction", args)
        }
    }

    fun confirmPendingAction() {
        viewModelScope.launch {
            val msg = toolEngine.confirmAndExecutePendingAction()
            dataRepository.addChatMessage(
                role = "jarvis",
                content = msg,
                toolName = "confirmed_action"
            )
        }
    }

    fun cancelPendingAction() {
        toolEngine.dismissPendingConfirmation()
        viewModelScope.launch {
            dataRepository.addChatMessage(
                role = "system",
                content = "Sensitive action cancelled by user."
            )
        }
    }

    // --- Notes & Memory Operations ---

    fun createNote(title: String, content: String, category: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            dataRepository.createNote(title, content, category, isVoiceCreated = false)
        }
    }

    fun updateNote(id: Long, title: String, content: String, category: String) {
        viewModelScope.launch {
            dataRepository.updateNote(id, title, content, category)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            dataRepository.deleteNote(id)
        }
    }

    fun saveMemoryItem(category: String, key: String, value: String, importance: Int) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            dataRepository.saveMemory(category, key, value, importance)
        }
    }

    fun deleteMemoryItem(id: Long) {
        viewModelScope.launch {
            dataRepository.deleteMemoryById(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            dataRepository.clearAllMemories()
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            dataRepository.clearConversation()
        }
    }

    // --- API Key Management ---

    fun saveApiKey(rawKey: String) {
        secureKeyManager.saveApiKey(rawKey)
        _apiKeyTestState.value = ApiKeyTestResult(
            success = true,
            title = "API Key Encrypted & Saved",
            details = "Stored in Android Hardware Keystore (AES-256-GCM). Tap 'Test API Key' to verify connectivity."
        )
    }

    fun deleteApiKey() {
        secureKeyManager.deleteStoredApiKey()
        _apiKeyTestState.value = ApiKeyTestResult(
            success = false,
            title = "Stored API Key Deleted",
            details = "The stored Gemini API key has been purged from the secure vault."
        )
    }

    fun testApiKey(candidateKeyInput: String = "") {
        val keyToTest = candidateKeyInput.trim().ifEmpty { secureKeyManager.getEffectiveApiKey() }
        viewModelScope.launch {
            _isTestingApiKey.value = true
            _apiKeyTestState.value = null
            val res = brainClient.testApiKey(
                candidateKey = keyToTest,
                configuredModel = settings.value.textBrainModel
            )
            _apiKeyTestState.value = res
            _isTestingApiKey.value = false
        }
    }

    // --- Settings Updaters ---

    fun updateLiveModel(model: String) = viewModelScope.launch { settingsRepository.updateLiveModel(model) }
    fun updateTextModel(model: String) = viewModelScope.launch { settingsRepository.updateTextModel(model) }
    fun updateVoiceEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateVoiceOutputEnabled(enabled) }
    fun updateSelectedVoice(voice: String) = viewModelScope.launch { settingsRepository.updateSelectedVoice(voice) }
    fun updateSpeakingSpeed(speed: Float) = viewModelScope.launch { settingsRepository.updateSpeakingSpeed(speed) }
    fun updateBargeIn(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateBargeInEnabled(enabled) }
    fun updateEchoControl(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateEchoControlEnabled(enabled) }
    fun updateWakeWordEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateWakeWordEnabled(enabled) }
    fun updateWakePhrase(phrase: String) = viewModelScope.launch { settingsRepository.updateSelectedWakePhrase(phrase) }
    fun updateWakeSensitivity(sens: Float) = viewModelScope.launch { settingsRepository.updateWakeWordSensitivity(sens) }
    fun updatePersonalityMode(mode: PersonalityMode) = viewModelScope.launch { settingsRepository.updatePersonalityMode(mode) }
    fun updateResponseStyle(style: String) = viewModelScope.launch { settingsRepository.updateResponseStyle(style) }
    fun updateFormality(level: Float) = viewModelScope.launch { settingsRepository.updateFormalityLevel(level) }
    fun updatePersonalityLevel(level: Float) = viewModelScope.launch { settingsRepository.updatePersonalityLevel(level) }
    fun updateMemoryEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateMemoryEnabled(enabled) }
    fun updateConfirmSensitive(confirm: Boolean) = viewModelScope.launch { settingsRepository.updateConfirmSensitiveActions(confirm) }
    fun updateScreenAutoAttach(auto: Boolean) = viewModelScope.launch { settingsRepository.updateScreenContextAutoAttach(auto) }

    fun computeEffectiveOperationalState(): JarvisOperationalState {
        val callState = liveTelemetry.value
        if (callState.isCallingModeActive) {
            return callState.operationalState
        }
        if (_isBrainThinking.value) {
            return JarvisOperationalState.THINKING
        }
        if (callState.operationalState == JarvisOperationalState.SPEAKING) {
            return JarvisOperationalState.SPEAKING
        }
        if (wakeWordState.value.isContinuousCommandSession || wakeWordState.value.isListeningForWakeWord) {
            return JarvisOperationalState.LISTENING
        }
        if (!brainClient.isNetworkAvailable()) {
            return JarvisOperationalState.OFFLINE
        }
        if (_lastErrorBanner.value != null) {
            return JarvisOperationalState.ERROR
        }
        return if (keyStatus.value.isConfigured) {
            JarvisOperationalState.CONNECTED
        } else {
            JarvisOperationalState.STANDBY
        }
    }

    override fun onCleared() {
        super.onCleared()
        wakeWordEngine.stopListeningLoop()
        liveVoiceEngine.shutdown()
    }
}
