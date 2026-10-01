package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.data.preferences.GirlMood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class WakeWordState(
    val isEnabled: Boolean = false,
    val isListeningForWakeWord: Boolean = false,
    val isContinuousCommandSession: Boolean = false,
    val isChupSilentMode: Boolean = false,
    val selectedPhrase: String = "JARVIS",
    val sensitivity: Float = 0.75f,
    val lastDetectedTranscript: String = "",
    val statusLabel: String = "Ji… 'JARVIS' bolo 💕"
)

/**
 * Separate, modular Always-On Wake-Word & Hinglish Speech Detection Engine (v5.0).
 *
 * Supports:
 * - Wake Words: "JARVIS", "Hey JARVIS", "Oye JARVIS", "Sun JARVIS", "Jarvis utho",
 *   "Jarvis on", "Jarvis active", "Jarvis aa ja", "Jarvis suno", "Jarvis idhar aao", "Jarvis bolo"
 * - Sleep Words: "Jarvis off", "Jarvis sleep", "Jarvis so jao", "Jarvis band"
 * - Chup Mode: "Jarvis chup" (stops speaking immediately, but keeps listening silently!)
 * - Special Mood Commands: "Jarvis romantic mode", "Jarvis angry mode", "Jarvis study mode",
 *   "Jarvis fun mode", "Jarvis mom mode", "Jarvis professional mode"
 */
class WakeWordEngine(
    private val context: Context,
    private val onWakeWordTriggered: (initialCommand: String?) -> Unit,
    private val onSleepWordTriggered: (sleepCommand: String) -> Unit,
    private val onChupModeTriggered: () -> Unit,
    private val onSpecialMoodTriggered: (GirlMood) -> Unit,
    private val onContinuousCommandCaptured: (command: String) -> Unit,
    private val onStopCommandDetected: () -> Unit,
    private val onVoiceRmsChanged: (Float) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(WakeWordState())
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    @Volatile
    private var isPausedForCallingMode = false
    @Volatile
    private var isRestartScheduled = false

    fun configure(enabled: Boolean, phrase: String, sensitivity: Float) {
        val prevEnabled = _state.value.isEnabled
        _state.value = _state.value.copy(
            isEnabled = enabled,
            selectedPhrase = phrase,
            sensitivity = sensitivity,
            statusLabel = if (enabled) "Listening… '$phrase' bolo 💕" else "Wake Word Standby"
        )
        if (enabled && !isPausedForCallingMode) {
            startListeningLoop()
        } else if (!enabled && prevEnabled && !_state.value.isContinuousCommandSession) {
            stopListeningLoop()
        }
    }

    fun setContinuousCommandSession(active: Boolean) {
        _state.value = _state.value.copy(
            isContinuousCommandSession = active,
            isChupSilentMode = false,
            statusLabel = if (active) {
                "Ji… sun rahi hun, boliye 💕"
            } else if (_state.value.isEnabled) {
                "Listening… '${_state.value.selectedPhrase}' bolo"
            } else {
                "Ji… boliye?"
            }
        )
        if (active && !isPausedForCallingMode) {
            startListeningLoop()
        } else if (!active && !_state.value.isEnabled) {
            stopListeningLoop()
        }
    }

    fun pauseForCallingMode(paused: Boolean) {
        isPausedForCallingMode = paused
        if (paused) {
            stopListeningLoop()
        } else if (_state.value.isEnabled || _state.value.isContinuousCommandSession) {
            startListeningLoop()
        }
    }

    fun startListeningLoop() {
        mainHandler.post {
            if (isPausedForCallingMode) return@post
            if (!hasMicPermission()) {
                _state.value = _state.value.copy(
                    isListeningForWakeWord = false,
                    statusLabel = "Mic permission chahiye ji"
                )
                return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = _state.value.copy(
                    isListeningForWakeWord = false,
                    statusLabel = "Speech service unavailable"
                )
                return@post
            }

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                        setRecognitionListener(recognitionListener)
                    }
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
                _state.value = _state.value.copy(isListeningForWakeWord = true)
            } catch (e: Exception) {
                scheduleRestart(1200L)
            }
        }
    }

    fun stopListeningLoop() {
        mainHandler.post {
            isRestartScheduled = false
            mainHandler.removeCallbacksAndMessages(null)
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = null
            _state.value = _state.value.copy(isListeningForWakeWord = false)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(isListeningForWakeWord = true)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.03f, 1.0f)
            onVoiceRmsChanged(normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            onVoiceRmsChanged(0.04f)
        }

        override fun onError(error: Int) {
            onVoiceRmsChanged(0.03f)
            if (!isPausedForCallingMode && (_state.value.isEnabled || _state.value.isContinuousCommandSession)) {
                scheduleRestart(650L)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val topText = matches.firstOrNull()?.trim().orEmpty()
            if (topText.isNotEmpty()) {
                processRecognizedSpeech(topText)
            }
            if (!isPausedForCallingMode && (_state.value.isEnabled || _state.value.isContinuousCommandSession)) {
                scheduleRestart(300L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val partial = matches.firstOrNull()?.trim().orEmpty()
            if (partial.isNotEmpty()) {
                _state.value = _state.value.copy(lastDetectedTranscript = partial)
                val lower = partial.lowercase(Locale.US)
                if (lower == "stop" || lower.contains("jarvis stop") || lower.contains("jarvis chup")) {
                    onStopCommandDetected()
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun processRecognizedSpeech(utterance: String) {
        val clean = utterance.trim()
        val lower = clean.lowercase(Locale.US)
        _state.value = _state.value.copy(lastDetectedTranscript = clean)

        // 1. Check for "Jarvis chup" (Chup mode: stop speaking, but KEEP listening!)
        if (lower.contains("jarvis chup") || lower == "chup" || lower == "chup ho jao") {
            _state.value = _state.value.copy(
                isChupSilentMode = true,
                isContinuousCommandSession = true,
                statusLabel = "Chup hun ji… par sun rahi hun 🤫"
            )
            onChupModeTriggered()
            return
        }

        // 2. Check for Sleep Words ("Jarvis off", "Jarvis sleep", "Jarvis so jao", "Jarvis band")
        if (lower.contains("jarvis off") ||
            lower.contains("jarvis sleep") ||
            lower.contains("jarvis so jao") ||
            lower.contains("jarvis band")
        ) {
            _state.value = _state.value.copy(
                isContinuousCommandSession = false,
                isChupSilentMode = false,
                statusLabel = "Theek hai ji… so jaati hun 💤"
            )
            onSleepWordTriggered(clean)
            return
        }

        // 3. Check for Stop command
        if (lower == "stop" || lower == "jarvis stop" || lower == "jarvis, stop" || lower.endsWith("jarvis stop")) {
            _state.value = _state.value.copy(
                isContinuousCommandSession = false,
                statusLabel = "Ruk gayi ji"
            )
            onStopCommandDetected()
            return
        }

        // 4. Check for Special Voice Mode commands
        when {
            lower.contains("romantic mode") -> {
                onSpecialMoodTriggered(GirlMood.ROMANTIC)
                return
            }
            lower.contains("angry mode") || lower.contains("nakhre mode") -> {
                onSpecialMoodTriggered(GirlMood.ANGRY)
                return
            }
            lower.contains("study mode") -> {
                onSpecialMoodTriggered(GirlMood.STUDY)
                return
            }
            lower.contains("fun mode") || lower.contains("playful mode") -> {
                onSpecialMoodTriggered(GirlMood.PLAYFUL)
                return
            }
            lower.contains("mom mode") || lower.contains("caring mode") -> {
                onSpecialMoodTriggered(GirlMood.CARING_MOM)
                return
            }
            lower.contains("professional mode") -> {
                onSpecialMoodTriggered(GirlMood.PROFESSIONAL)
                return
            }
        }

        // 5. Check for Wake Words ("JARVIS", "Hey JARVIS", "Oye JARVIS", "Sun JARVIS",
        // "Jarvis utho", "Jarvis on", "Jarvis active", "Jarvis aa ja", "Jarvis suno", "Jarvis idhar aao", "Jarvis bolo")
        val hasWakeWord = matchesWakeWord(lower, _state.value.sensitivity)
        if (hasWakeWord) {
            playActivationTone()
            _state.value = _state.value.copy(
                isContinuousCommandSession = true,
                isChupSilentMode = false,
                statusLabel = "Ji… boliye, sun rahi hun 💕"
            )
            val strippedCommand = extractCommandAfterWakeWord(clean)
            onWakeWordTriggered(strippedCommand.takeIf { it.isNotBlank() })
            return
        }

        // 6. If already in an active continuous command session, forward the spoken command directly!
        if (_state.value.isContinuousCommandSession) {
            _state.value = _state.value.copy(isChupSilentMode = false)
            onContinuousCommandCaptured(clean)
        }
    }

    private fun matchesWakeWord(lowerText: String, sensitivity: Float): Boolean {
        if (lowerText.contains("jarvis") ||
            lowerText.contains("hey jarvis") ||
            lowerText.contains("oye jarvis") ||
            lowerText.contains("sun jarvis")
        ) return true

        if (sensitivity >= 0.60f) {
            if (lowerText.startsWith("jervis") ||
                lowerText.startsWith("javis") ||
                lowerText.startsWith("jarv")
            ) {
                return true
            }
        }
        return false
    }

    private fun extractCommandAfterWakeWord(raw: String): String {
        val regex = Regex(
            "(?i)^(hey\\s+|oye\\s+|sun\\s+|suno\\s+)?(jarvis|jervis|javis)[,!.]?(\\s+(utho|wake\\s+up|on|active|aa\\s+ja|suno|idhar\\s+aao|bolo)[,!.]?)?\\s*"
        )
        return raw.replaceFirst(regex, "").trim()
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isRestartScheduled) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (!isPausedForCallingMode && (_state.value.isEnabled || _state.value.isContinuousCommandSession)) {
                startListeningLoop()
            }
        }, delayMs)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun playActivationTone() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        }
    }
}
