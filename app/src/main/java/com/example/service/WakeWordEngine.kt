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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class WakeWordState(
    val isEnabled: Boolean = false,
    val isListeningForWakeWord: Boolean = false,
    val isContinuousCommandSession: Boolean = false,
    val selectedPhrase: String = "JARVIS",
    val sensitivity: Float = 0.72f,
    val lastDetectedTranscript: String = "",
    val statusLabel: String = "Wake Word Standby (OFF)"
)

/**
 * Separate, modular Wake-Word & Continuous Speech Detection Engine.
 * Operates independently from GeminiLiveVoiceEngine so the architecture remains expandable.
 *
 * Supports:
 * - Primary wake word: "JARVIS"
 * - Optional wake phrase: "Hey JARVIS"
 * - "JARVIS, wake up"
 * - "JARVIS, stop" / "Stop"
 * - Continuous conversation loop: WAKE -> LISTEN -> UNDERSTAND -> THINK -> SPEAK -> LISTEN AGAIN
 */
class WakeWordEngine(
    private val context: Context,
    private val onWakeWordTriggered: (initialCommand: String?) -> Unit,
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
            statusLabel = if (enabled) "Armed for '$phrase'" else "Wake Word OFF"
        )
        if (enabled && !isPausedForCallingMode) {
            startListeningLoop()
        } else if (!enabled && prevEnabled && !_state.value.isContinuousCommandSession) {
            stopListeningLoop()
        }
    }

    /**
     * Starts or stops the continuous hands-free voice loop (LISTEN -> THINK -> SPEAK -> LISTEN)
     * directly from the main screen without requiring repeated microphone button presses.
     */
    fun setContinuousCommandSession(active: Boolean) {
        _state.value = _state.value.copy(
            isContinuousCommandSession = active,
            statusLabel = if (active) {
                "Continuous Voice Loop Active — Speak naturally"
            } else if (_state.value.isEnabled) {
                "Armed for '${_state.value.selectedPhrase}'"
            } else {
                "Standby"
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
                    statusLabel = "Mic permission needed for Wake Word"
                )
                return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = _state.value.copy(
                    isListeningForWakeWord = false,
                    statusLabel = "Speech recognition service unavailable on device"
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
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
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
                scheduleRestart(700L)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val topText = matches.firstOrNull()?.trim().orEmpty()
            if (topText.isNotEmpty()) {
                processRecognizedSpeech(topText)
            }
            if (!isPausedForCallingMode && (_state.value.isEnabled || _state.value.isContinuousCommandSession)) {
                scheduleRestart(350L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val partial = matches.firstOrNull()?.trim().orEmpty()
            if (partial.isNotEmpty()) {
                _state.value = _state.value.copy(lastDetectedTranscript = partial)
                // Check for immediate "JARVIS, stop" or "Stop" interruption in partial results
                val lower = partial.lowercase(Locale.US)
                if (lower == "stop" || lower.contains("jarvis stop") || lower.contains("jarvis, stop")) {
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

        // 1. Check for Stop command
        if (lower == "stop" || lower == "jarvis stop" || lower == "jarvis, stop" || lower.endsWith("jarvis stop")) {
            _state.value = _state.value.copy(
                isContinuousCommandSession = false,
                statusLabel = "Stopped by voice command"
            )
            onStopCommandDetected()
            return
        }

        // 2. Check for Wake Word ("JARVIS", "Hey JARVIS", "JARVIS wake up", or phonetic close matches based on sensitivity)
        val hasWakeWord = matchesWakeWord(lower, _state.value.sensitivity)
        if (hasWakeWord) {
            playActivationTone()
            _state.value = _state.value.copy(
                isContinuousCommandSession = true,
                statusLabel = "Wake Word Detected — Listening continuously"
            )
            val strippedCommand = extractCommandAfterWakeWord(clean)
            onWakeWordTriggered(strippedCommand.takeIf { it.isNotBlank() })
            return
        }

        // 3. If already in an active continuous command session, forward the spoken command directly!
        if (_state.value.isContinuousCommandSession) {
            onContinuousCommandCaptured(clean)
        }
    }

    private fun matchesWakeWord(lowerText: String, sensitivity: Float): Boolean {
        if (lowerText.contains("jarvis") || lowerText.contains("hey jarvis")) return true
        if (sensitivity >= 0.65f) {
            // Handle common speech-to-text phonetic variations of "Jarvis"
            if (lowerText.startsWith("jervis") || lowerText.startsWith("javis") || lowerText.startsWith("harvest")) {
                return true
            }
        }
        return false
    }

    private fun extractCommandAfterWakeWord(raw: String): String {
        val regex = Regex("(?i)^(hey\\s+)?(jarvis|jervis|javis)[,!.]?(\\s+wake\\s+up[,!.]?)?\\s*")
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
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 160)
        }
    }
}
