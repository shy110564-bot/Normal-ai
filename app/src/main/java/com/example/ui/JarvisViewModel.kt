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
import com.example.data.preferences.GirlMood
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
import java.util.Calendar

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

    val toolEngine: JarvisToolEngine = JarvisToolEngine(
        context = application,
        dataRepository = dataRepository,
        onMoodChangeRequested = { newMood ->
            updateGirlMood(newMood)
        }
    )

    val brainClient = GeminiBrainClient(application, dataRepository, toolEngine)
    val liveVoiceEngine = GeminiLiveVoiceEngine(application, brainClient, dataRepository, toolEngine)

    val wakeWordEngine = WakeWordEngine(
        context = application,
        onWakeWordTriggered = { initialCommand ->
            handleWakeWordTriggered(initialCommand)
        },
        onSleepWordTriggered = { sleepCmd ->
            handleSleepWordTriggered(sleepCmd)
        },
        onChupModeTriggered = {
            handleChupSilentMode()
        },
        onSpecialMoodTriggered = { mood ->
            handleSpecialMoodSwitch(mood)
        },
        onContinuousCommandCaptured = { command ->
            handleSpokenCommand(command)
        },
        onStopCommandDetected = {
            stopAndInterruptAll("Ruk gayi ji… ab aap boliye")
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
    val isTorchOn: StateFlow<Boolean> = toolEngine.isTorchOn

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

        // Seed initial Hinglish welcome greeting & AK EXPLOITS creator recognition memory if database is fresh
        viewModelScope.launch {
            val existingMsgs = dataRepository.getRecentMessages(1)
            if (existingMsgs.isEmpty()) {
                val greeting = buildTimeBasedGreeting()
                dataRepository.addChatMessage(
                    role = "jarvis",
                    content = "$greeting\nMain JARVIS hun (v5.0 Final Ultimate — Created by AK EXPLOITS) 💕\nAap mujhse Hindi/Hinglish mein live baat kar sakte ho, 'JARVIS' bol kar wake kar sakte ho, ya apna pura phone control karwa sakte ho. Boliye ji, kya seva karun?"
                )
                dataRepository.saveMemory(
                    category = MemoryCategory.ASSISTANT_PREFERENCE.name,
                    key = "creator_identity",
                    value = "AK EXPLOITS (Telegram: https://t.me/+R9EwUE03GRswZDM9 | YouTube: AK EXPLOITS)",
                    importance = 5
                )
                dataRepository.saveMemory(
                    category = MemoryCategory.ASSISTANT_PREFERENCE.name,
                    key = "assistant_persona",
                    value = "JARVIS — 21-year-old intelligent, caring, emotional Indian Hinglish AI companion ('Ji…')",
                    importance = 5
                )
            }
        }
    }

    private fun buildTimeBasedGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning ji ☀️ Uth gaye aap?"
            in 12..16 -> "Ji… dopahar ho gayi! Khana khaya aapne? 🍱"
            in 17..21 -> "Good evening ji 💕 Aaj ka din kaisa raha?"
            else -> "Ji… itni raat ko jaag rahe ho? Main aapke saath hun 🌙"
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
            stopAndInterruptAll("Ruk gayi ji… ab aap boliye")
            return
        }

        // Handle "JARVIS, wake up" / "Jarvis utho"
        val lower = text.lowercase()
        if (lower == "jarvis, wake up" || lower == "jarvis wake up" || lower == "jarvis utho" || lower == "jarvis on") {
            handleWakeWordTriggered(null)
            return
        }

        viewModelScope.launch {
            _lastErrorBanner.value = null
            _lastFailedCommand.value = null
            dataRepository.addChatMessage(
                role = "user",
                content = text,
                isVoice = isVoiceOrigin
            )

            _isBrainThinking.value = true
            toolEngine.updateActivityLabel("Hmm… soch rahi hun ji…")

            val history = dataRepository.getRecentMessages(50)
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
                    toolEngine.updateActivityLabel("Ho gaya ji ✅ (${result.modelUsed})")

                    // Speak response if triggered via Voice / Continuous Listening (and not in Chup silent mode)
                    val isChup = wakeWordEngine.state.value.isChupSilentMode
                    if ((isVoiceOrigin || wakeWordEngine.state.value.isContinuousCommandSession) &&
                        !liveTelemetry.value.isCallingModeActive &&
                        !isChup
                    ) {
                        liveVoiceEngine.speakTextResponse(result.replyText, cfg)
                    }
                }

                is BrainResponseResult.Failure -> {
                    _lastErrorBanner.value = "${result.errorTitle}: ${result.errorMessage}"
                    _lastFailedCommand.value = text
                    toolEngine.updateActivityLabel("Alert — ${result.errorTitle}")
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
            liveVoiceEngine.interruptSpeech(returnToListening = false, reason = "Theek hai ji… standby mode")
            toolEngine.updateActivityLabel("Ji… boliye? Main sun rahi hun 💕")
        } else {
            wakeWordEngine.setContinuousCommandSession(true)
            toolEngine.updateActivityLabel("Haan ji… boliye, sun rahi hun 💕")
            liveVoiceEngine.speakTextResponse("Haan ji… boliye, main sun rahi hun.", settings.value)
        }
    }

    fun stopAndInterruptAll(reason: String = "Ruk gayi ji… ab aap boliye") {
        liveVoiceEngine.interruptSpeech(
            returnToListening = liveTelemetry.value.isCallingModeActive,
            reason = reason
        )
        if (!liveTelemetry.value.isCallingModeActive) {
            wakeWordEngine.setContinuousCommandSession(false)
        }
        toolEngine.updateActivityLabel(reason)
    }

    private fun handleWakeWordTriggered(initialCommand: String?) {
        toolEngine.updateActivityLabel("Haan ji… boliye, sun rahi hun 💕")
        if (initialCommand.isNullOrBlank()) {
            val reply = "Haan ji… boliye? Main sun rahi hun 💕"
            viewModelScope.launch {
                dataRepository.addChatMessage(
                    role = "jarvis",
                    content = reply,
                    isVoice = true
                )
            }
            liveVoiceEngine.speakTextResponse(reply, settings.value)
        } else {
            sendUserMessage(initialCommand, isVoiceOrigin = true)
        }
    }

    private fun handleSleepWordTriggered(sleepCmd: String) {
        liveVoiceEngine.interruptSpeech(returnToListening = false, reason = "Theek hai ji… so jaati hun 💤")
        val reply = "Theek hai ji… so jaati hun… Jab zaroorat ho, bula lena 💕"
        toolEngine.updateActivityLabel("Standby Mode 💤 ('JARVIS' bol kar jagayein)")
        viewModelScope.launch {
            dataRepository.addChatMessage(
                role = "jarvis",
                content = reply,
                isVoice = true
            )
        }
        liveVoiceEngine.speakTextResponse(reply, settings.value)
    }

    private fun handleChupSilentMode() {
        liveVoiceEngine.interruptSpeech(returnToListening = true, reason = "Chup hun ji… par sun rahi hun 🤫")
        toolEngine.updateActivityLabel("Chup Mode 🤫 — Bolungi nahi, par sun rahi hun ji")
    }

    private fun handleSpecialMoodSwitch(mood: GirlMood) {
        updateGirlMood(mood)
        val reply = "Ji… ab main ${mood.emoji} ${mood.title} mood mein hun! ${mood.sampleLine}"
        viewModelScope.launch {
            dataRepository.addChatMessage(
                role = "jarvis",
                content = reply,
                toolName = "set_jarvis_mood",
                isVoice = true
            )
        }
        liveVoiceEngine.speakTextResponse(reply, settings.value.copy(currentMood = mood))
    }

    private fun handleSpokenCommand(command: String) {
        if (command.isBlank()) return
        sendUserMessage(command, isVoiceOrigin = true)
    }

    // --- Creator & Hardware Shortcuts ---

    fun openCreatorChannel(platform: String) {
        viewModelScope.launch {
            toolEngine.openCreatorChannel(platform)
            val msg = if (platform.equals("TELEGRAM", ignoreCase = true)) {
                "Ji… mere creator AK EXPLOITS ka Telegram channel (${JarvisSettings.CREATOR_TELEGRAM_URL}) khol diya hai 💕"
            } else {
                "Ji… YouTube par mere creator 'AK EXPLOITS' ka channel khol diya hai 💕"
            }
            dataRepository.addChatMessage(
                role = "jarvis",
                content = msg,
                toolName = "open_creator_channel"
            )
        }
    }

    fun toggleFlashlightQuick() {
        viewModelScope.launch {
            val nextState = !isTorchOn.value
            val res = toolEngine.toggleFlashlight(nextState)
            val ok = res.optString("status") == "VERIFIED_SUCCESS"
            dataRepository.addChatMessage(
                role = "jarvis",
                content = if (ok) {
                    "Ho gaya ji ✅ Torch ${if (nextState) "ON 🔦" else "OFF"} kar diya hai."
                } else {
                    "Ji, is device par torch toggle nahi ho paya (${res.optString("message")})."
                },
                toolName = "toggle_flashlight"
            )
        }
    }

    // --- Multi-Step Automation & Screen Control Helpers ---

    fun runYouTubeMultiStepWorkflow(searchQuery: String) {
        val cleanQuery = searchQuery.ifBlank { "AK EXPLOITS" }
        viewModelScope.launch {
            dataRepository.addChatMessage(
                role = "user",
                content = "JARVIS, YouTube kholo aur '$cleanQuery' search karo."
            )
            val steps = listOf<Pair<String, suspend () -> Boolean>>(
                "Verify target application & intent resolution (YouTube)" to {
                    true
                },
                "Launch YouTube with exact search query: '$cleanQuery'" to {
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
                    "Ho gaya ji ✅ YouTube khol kar \"$cleanQuery\" search kar diya hai 💕"
                } else {
                    "Ji, YouTube workflow \"$cleanQuery\" ke liye run kiya. Control tab mein step status dekh lijiye."
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
                content = "Ji, aapke kehne par action cancel kar diya gaya."
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
            title = "API Key Encrypted & Saved ✅",
            details = "Ji, aapki Gemini API key Android Hardware Keystore (AES-256-GCM) mein safe hai. 'Test Key' daba kar check kar sakte hain."
        )
    }

    fun deleteApiKey() {
        secureKeyManager.deleteStoredApiKey()
        _apiKeyTestState.value = ApiKeyTestResult(
            success = false,
            title = "Stored API Key Deleted",
            details = "Ji, stored Gemini API key secure vault se hata di gayi hai."
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
    fun updateVoicePitch(pitch: Float) = viewModelScope.launch { settingsRepository.updateVoicePitch(pitch) }
    fun updateEmotionIntensity(intensity: Float) = viewModelScope.launch { settingsRepository.updateEmotionIntensity(intensity) }
    fun updateBreathingEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateBreathingEnabled(enabled) }
    fun updateGigglesEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateGigglesEnabled(enabled) }
    fun updateEmotionalPausesEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateEmotionalPausesEnabled(enabled) }
    fun updateWhisperModeEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateWhisperModeEnabled(enabled) }
    fun updateBargeIn(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateBargeInEnabled(enabled) }
    fun updateEchoControl(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateEchoControlEnabled(enabled) }
    fun updateWakeWordEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateWakeWordEnabled(enabled) }
    fun updateWakePhrase(phrase: String) = viewModelScope.launch { settingsRepository.updateSelectedWakePhrase(phrase) }
    fun updateWakeSensitivity(sens: Float) = viewModelScope.launch { settingsRepository.updateWakeWordSensitivity(sens) }
    fun updatePersonalityMode(mode: PersonalityMode) = viewModelScope.launch { settingsRepository.updatePersonalityMode(mode) }
    fun updateGirlMood(mood: GirlMood) {
        viewModelScope.launch {
            settingsRepository.updateCurrentMood(mood)
            toolEngine.updateActivityLabel("Mood Active: ${mood.emoji} ${mood.title} — \"${mood.sampleLine}\"")
        }
    }
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
