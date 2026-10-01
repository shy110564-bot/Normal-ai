package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.data.local.JarvisDataRepository
import com.example.data.preferences.JarvisSettings
import com.example.data.remote.GeminiBrainClient
import com.example.domain.tools.JarvisToolEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class JarvisOperationalState(val badgeText: String, val subtitle: String) {
    STANDBY("STANDBY", "Systems online — Ready for voice or command"),
    CONNECTING("CONNECTING", "Establishing real-time Gemini Live WebSocket..."),
    CONNECTED("CONNECTED", "Gemini Live neural link established"),
    LISTENING("LISTENING", "Microphone active — Listening continuously"),
    THINKING("THINKING", "Processing intent & reasoning with Gemini..."),
    SPEAKING("SPEAKING", "JARVIS speaking — Say 'JARVIS, stop' to interrupt"),
    INTERRUPTED("INTERRUPTED", "Speech interrupted — Listening to your new command"),
    RECONNECTING("RECONNECTING", "Restoring Gemini Live WebSocket session..."),
    OFFLINE("OFFLINE", "Network unavailable — Local tools active"),
    ERROR("ERROR", "System alert — Check connection or API key")
}

enum class GeminiLiveConnectionState(val label: String) {
    GEMINI_DISCONNECTED("GEMINI DISCONNECTED"),
    CONNECTING("CONNECTING"),
    GEMINI_CONNECTED("GEMINI CONNECTED"),
    RECONNECTING("RECONNECTING"),
    ERROR("ERROR")
}

data class CallingSessionTelemetry(
    val operationalState: JarvisOperationalState = JarvisOperationalState.STANDBY,
    val liveConnectionState: GeminiLiveConnectionState = GeminiLiveConnectionState.GEMINI_DISCONNECTED,
    val isCallingModeActive: Boolean = false,
    val isContinuousMicActive: Boolean = false,
    val isMicMuted: Boolean = false,
    val isSpeakerActive: Boolean = false,
    val activeLiveModel: String = JarvisSettings.DEFAULT_LIVE_MODEL,
    val audioAmplitude: Float = 0f,
    val spectrumBands: List<Float> = List(16) { 0.08f },
    val liveTranscript: String = "",
    val jarvisLiveResponse: String = "",
    val statusDetail: String = "Tap Calling Mode or Mic to begin continuous conversation",
    val errorMessage: String? = null
)

class GeminiLiveVoiceEngine(
    private val context: Context,
    private val brainClient: GeminiBrainClient,
    private val dataRepository: JarvisDataRepository,
    private val toolEngine: JarvisToolEngine
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _telemetry = MutableStateFlow(CallingSessionTelemetry())
    val telemetry: StateFlow<CallingSessionTelemetry> = _telemetry.asStateFlow()

    private var webSocket: WebSocket? = null
    private var audioCaptureJob: Job? = null
    private var audioPlaybackJob: Job? = null
    private var reconnectJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private val pcmPlaybackQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile
    private var isSetupComplete = false
    @Volatile
    private var isModelTurnFinished = true
    @Volatile
    private var triedFallbackLiveModel = false

    private var currentApiKey: String = ""
    private var currentSettings: JarvisSettings = JarvisSettings()
    private var reconnectAttempts = 0

    // Native Android TTS as expandable secondary/fallback voice output
    private var tts: TextToSpeech? = null
    @Volatile
    private var isTtsReady = false

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        updateTelemetry {
                            it.copy(
                                operationalState = JarvisOperationalState.SPEAKING,
                                isSpeakerActive = true,
                                statusDetail = "JARVIS speaking response..."
                            )
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        updateTelemetry {
                            val nextState = if (it.isCallingModeActive || it.isContinuousMicActive) {
                                JarvisOperationalState.LISTENING
                            } else {
                                JarvisOperationalState.STANDBY
                            }
                            it.copy(
                                operationalState = nextState,
                                isSpeakerActive = false,
                                audioAmplitude = 0.05f,
                                statusDetail = if (nextState == JarvisOperationalState.LISTENING) {
                                    "Listening continuously — Speak naturally"
                                } else {
                                    "Ready"
                                }
                            )
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        updateTelemetry { it.copy(isSpeakerActive = false) }
                    }
                })
            }
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts real-time Gemini Live Calling Mode (continuous LISTEN -> THINK -> SPEAK -> LISTEN).
     */
    fun startCallingMode(apiKey: String, settings: JarvisSettings) {
        if (!hasRecordAudioPermission()) {
            updateTelemetry {
                it.copy(
                    operationalState = JarvisOperationalState.ERROR,
                    errorMessage = "Microphone permission (RECORD_AUDIO) is required for Gemini Live voice conversation.",
                    statusDetail = "Grant Microphone permission to begin"
                )
            }
            return
        }
        if (!brainClient.isNetworkAvailable()) {
            updateTelemetry {
                it.copy(
                    operationalState = JarvisOperationalState.OFFLINE,
                    liveConnectionState = GeminiLiveConnectionState.ERROR,
                    errorMessage = "Network unavailable. Connect to the internet to start Gemini Live Calling Mode.",
                    statusDetail = "Offline — Gemini Live unavailable"
                )
            }
            return
        }
        if (apiKey.isBlank()) {
            updateTelemetry {
                it.copy(
                    operationalState = JarvisOperationalState.ERROR,
                    liveConnectionState = GeminiLiveConnectionState.ERROR,
                    errorMessage = "Gemini API Key not configured. Open Settings > AI Brain to save your API Key.",
                    statusDetail = "API Key required"
                )
            }
            return
        }

        currentApiKey = apiKey.trim()
        currentSettings = settings
        triedFallbackLiveModel = false
        reconnectAttempts = 0

        configureAudioRoutingForCall(enable = true, echoControl = settings.echoControlEnabled)
        playFeedbackChime(ToneGenerator.TONE_PROP_BEEP)

        updateTelemetry {
            it.copy(
                operationalState = JarvisOperationalState.CONNECTING,
                liveConnectionState = GeminiLiveConnectionState.CONNECTING,
                isCallingModeActive = true,
                isContinuousMicActive = true,
                isMicMuted = false,
                activeLiveModel = settings.liveVoiceModel,
                errorMessage = null,
                statusDetail = "Connecting to Gemini Live (${settings.liveVoiceModel})..."
            )
        }

        connectWebSocket(modelToUse = settings.liveVoiceModel)
    }

    /**
     * Toggles mute/unmute during an active Calling Mode session.
     */
    fun toggleMute() {
        val muted = !_telemetry.value.isMicMuted
        updateTelemetry {
            it.copy(
                isMicMuted = muted,
                statusDetail = if (muted) "MIC MUTED — Tap Unmute to resume speaking" else "MIC ACTIVE — Listening continuously"
            )
        }
    }

    /**
     * Interrupts JARVIS immediately ("JARVIS, stop" / Barge-in / Stop button)
     * and returns to listening if in Calling Mode or Standby otherwise.
     */
    fun interruptSpeech(returnToListening: Boolean = true, reason: String = "Interrupted by user") {
        pcmPlaybackQueue.clear()
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        }
        runCatching {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        }

        val activeCall = _telemetry.value.isCallingModeActive || _telemetry.value.isContinuousMicActive
        val targetState = if (returnToListening && activeCall) {
            JarvisOperationalState.LISTENING
        } else {
            JarvisOperationalState.STANDBY
        }

        updateTelemetry {
            it.copy(
                operationalState = JarvisOperationalState.INTERRUPTED,
                isSpeakerActive = false,
                audioAmplitude = 0.05f,
                statusDetail = reason
            )
        }

        engineScope.launch {
            delay(350)
            updateTelemetry {
                if (it.operationalState == JarvisOperationalState.INTERRUPTED) {
                    it.copy(
                        operationalState = targetState,
                        statusDetail = if (targetState == JarvisOperationalState.LISTENING) {
                            "Listening for your next command..."
                        } else {
                            "Standby"
                        }
                    )
                } else {
                    it
                }
            }
        }
    }

    /**
     * Ends Calling Mode and cleanly releases WebSocket, AudioRecord, and AudioTrack resources.
     */
    fun stopCallingMode(reason: String = "Calling Mode ended") {
        reconnectJob?.cancel()
        audioCaptureJob?.cancel()
        audioPlaybackJob?.cancel()
        pcmPlaybackQueue.clear()
        isSetupComplete = false

        runCatching { webSocket?.close(1000, "User ended session") }
        webSocket = null

        releaseAudioCapture()
        releaseAudioPlayback()
        runCatching { tts?.stop() }
        configureAudioRoutingForCall(enable = false, echoControl = false)
        playFeedbackChime(ToneGenerator.TONE_PROP_NACK)

        updateTelemetry {
            CallingSessionTelemetry(
                operationalState = JarvisOperationalState.STANDBY,
                liveConnectionState = GeminiLiveConnectionState.GEMINI_DISCONNECTED,
                isCallingModeActive = false,
                isContinuousMicActive = false,
                isMicMuted = false,
                isSpeakerActive = false,
                activeLiveModel = currentSettings.liveVoiceModel,
                statusDetail = reason
            )
        }
    }

    /**
     * Speaks a text response using Android TTS when not in native WebSocket PCM stream
     * (or when voice output is enabled for wake-word / text-assisted responses).
     */
    fun speakTextResponse(text: String, settings: JarvisSettings) {
        if (!settings.voiceOutputEnabled || text.isBlank()) return
        if (!isTtsReady) return
        val cleanText = text.replace(Regex("[*#`_~]"), "").trim()
        tts?.setSpeechRate(settings.speakingSpeed.coerceIn(0.5f, 2.0f))
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    /**
     * Sends a text/voice command directly over the active Gemini Live WebSocket if connected.
     */
    fun sendTextTurnOverLiveSession(text: String) {
        val ws = webSocket ?: return
        if (!isSetupComplete) return
        if (isStopCommand(text)) {
            interruptSpeech(returnToListening = true, reason = "Voice command: Stop")
            return
        }

        val clientContent = JSONObject().put(
            "clientContent",
            JSONObject()
                .put(
                    "turns",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", text)))
                    )
                )
                .put("turnComplete", true)
        )
        ws.send(clientContent.toString())
        updateTelemetry {
            it.copy(
                operationalState = JarvisOperationalState.THINKING,
                liveTranscript = text,
                statusDetail = "Sent to Gemini Live — Thinking..."
            )
        }
    }

    private fun connectWebSocket(modelToUse: String) {
        runCatching { webSocket?.cancel() }
        isSetupComplete = false

        val normalizedModel = if (modelToUse.startsWith("models/")) {
            modelToUse
        } else {
            "models/${modelToUse.trim()}"
        }

        val wsUrl =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$currentApiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = brainClient.okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    engineScope.launch {
                        sendInitialSetupFrame(webSocket, normalizedModel)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingWebSocketJson(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    handleIncomingWebSocketJson(webSocket, bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    handleSocketDisconnectOrModelFallback(code, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handleSocketDisconnectOrModelFallback(
                        response?.code ?: -1,
                        t.message ?: "WebSocket connection error"
                    )
                }
            }
        )
    }

    private suspend fun sendInitialSetupFrame(ws: WebSocket, fullModelName: String) {
        val systemInstruction = brainClient.buildSystemInstruction(currentSettings)
        val setupObj = JSONObject().apply {
            put("model", fullModelName)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put(
                                "prebuiltVoiceConfig",
                                JSONObject().put("voiceName", currentSettings.selectedVoiceName)
                            )
                        )
                    )
                }
            )
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
            put("tools", toolEngine.getGeminiToolsJsonArray())
        }

        val root = JSONObject().put("setup", setupObj)
        ws.send(root.toString())
    }

    private fun handleIncomingWebSocketJson(ws: WebSocket, rawJson: String) {
        try {
            val json = JSONObject(rawJson)

            // 1. Setup Complete
            if (json.has("setupComplete")) {
                isSetupComplete = true
                reconnectAttempts = 0
                updateTelemetry {
                    it.copy(
                        operationalState = JarvisOperationalState.LISTENING,
                        liveConnectionState = GeminiLiveConnectionState.GEMINI_CONNECTED,
                        errorMessage = null,
                        statusDetail = "Gemini Live Connected — Speak naturally (Continuous Listening)"
                    )
                }
                startContinuousAudioLoops()
                return
            }

            // 2. Tool Call from Gemini Live
            if (json.has("toolCall")) {
                val toolCallObj = json.optJSONObject("toolCall")
                val fnCalls = toolCallObj?.optJSONArray("functionCalls") ?: JSONArray()
                engineScope.launch {
                    updateTelemetry {
                        it.copy(
                            operationalState = JarvisOperationalState.THINKING,
                            statusDetail = "Executing requested tool action..."
                        )
                    }
                    val functionResponses = JSONArray()
                    for (i in 0 until fnCalls.length()) {
                        val call = fnCalls.optJSONObject(i) ?: continue
                        val callId = call.optString("id", "")
                        val fnName = call.optString("name", "")
                        val fnArgs = call.optJSONObject("args")

                        val resultObj = toolEngine.executeToolCall(
                            name = fnName,
                            args = fnArgs,
                            confirmSensitive = currentSettings.confirmSensitiveActions,
                            memoryEnabled = currentSettings.memoryEnabled,
                            isVoiceOrigin = true
                        )

                        val respItem = JSONObject().apply {
                            if (callId.isNotEmpty()) put("id", callId)
                            put("name", fnName)
                            put("response", JSONObject().put("result", resultObj))
                        }
                        functionResponses.put(respItem)
                    }

                    val toolRespPayload = JSONObject().put(
                        "toolResponse",
                        JSONObject().put("functionResponses", functionResponses)
                    )
                    ws.send(toolRespPayload.toString())
                }
                return
            }

            // 3. Server Content (Audio chunks, Text transcript, Interruption, Turn Complete)
            if (json.has("serverContent")) {
                val serverContent = json.optJSONObject("serverContent") ?: return

                if (serverContent.optBoolean("interrupted", false)) {
                    interruptSpeech(returnToListening = true, reason = "Barge-in detected — Listening")
                    return
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null) {
                    isModelTurnFinished = false
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue

                        // Extract text if present
                        val textPart = part.optString("text", "")
                        if (textPart.isNotBlank()) {
                            updateTelemetry {
                                val updatedResp = (it.jarvisLiveResponse + " " + textPart).trim().takeLast(600)
                                it.copy(jarvisLiveResponse = updatedResp)
                            }
                        }

                        // Extract native PCM audio chunk
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val mimeType = inlineData.optString("mimeType", "")
                            val base64Audio = inlineData.optString("data", "")
                            if (base64Audio.isNotEmpty() && mimeType.startsWith("audio/pcm")) {
                                val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                if (pcmBytes.isNotEmpty()) {
                                    pcmPlaybackQueue.offer(pcmBytes)
                                    updateTelemetry {
                                        it.copy(
                                            operationalState = JarvisOperationalState.SPEAKING,
                                            isSpeakerActive = true,
                                            statusDetail = "JARVIS speaking (Gemini Live native voice)"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    isModelTurnFinished = true
                    val respText = _telemetry.value.jarvisLiveResponse.trim()
                    if (respText.isNotEmpty()) {
                        engineScope.launch {
                            dataRepository.addChatMessage(
                                role = "jarvis",
                                content = respText,
                                isVoice = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore malformed frame
        }
    }

    private fun handleSocketDisconnectOrModelFallback(code: Int, reason: String) {
        if (!_telemetry.value.isCallingModeActive) return

        // If user-configured model (e.g. "gemini-3.8-live") is not yet active on the endpoint,
        // automatically fall back to "gemini-2.5-flash-native-audio-preview-12-2025" so Live Voice works seamlessly
        if (!isSetupComplete && !triedFallbackLiveModel &&
            _telemetry.value.activeLiveModel != JarvisSettings.FALLBACK_LIVE_MODEL
        ) {
            triedFallbackLiveModel = true
            updateTelemetry {
                it.copy(
                    operationalState = JarvisOperationalState.CONNECTING,
                    liveConnectionState = GeminiLiveConnectionState.RECONNECTING,
                    activeLiveModel = JarvisSettings.FALLBACK_LIVE_MODEL,
                    statusDetail = "Switching to live native audio model (${JarvisSettings.FALLBACK_LIVE_MODEL})..."
                )
            }
            connectWebSocket(modelToUse = JarvisSettings.FALLBACK_LIVE_MODEL)
            return
        }

        if (reconnectAttempts < 3 && brainClient.isNetworkAvailable()) {
            reconnectAttempts++
            reconnectJob?.cancel()
            reconnectJob = engineScope.launch {
                updateTelemetry {
                    it.copy(
                        operationalState = JarvisOperationalState.RECONNECTING,
                        liveConnectionState = GeminiLiveConnectionState.RECONNECTING,
                        statusDetail = "Reconnecting Gemini Live session (attempt $reconnectAttempts/3)..."
                    )
                }
                delay(1500L * reconnectAttempts)
                if (_telemetry.value.isCallingModeActive) {
                    connectWebSocket(_telemetry.value.activeLiveModel)
                }
            }
        } else {
            val isKeyErr = reason.contains("400") || reason.contains("403") || reason.contains("key", ignoreCase = true)
            updateTelemetry {
                it.copy(
                    operationalState = JarvisOperationalState.ERROR,
                    liveConnectionState = GeminiLiveConnectionState.ERROR,
                    errorMessage = if (isKeyErr) {
                        "Gemini Live authentication failed ($reason). Verify your Gemini API Key in Settings."
                    } else {
                        "Gemini Live connection closed ($code: $reason). Tap Reconnect to resume."
                    },
                    statusDetail = "Connection interrupted"
                )
            }
        }
    }

    private fun startContinuousAudioLoops() {
        startAudioPlaybackLoop()
        startAudioCaptureLoop()
    }

    private fun startAudioPlaybackLoop() {
        audioPlaybackJob?.cancel()
        audioPlaybackJob = engineScope.launch {
            val sampleRate = 24000
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            releaseAudioPlayback()
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track

            while (isActive && _telemetry.value.isCallingModeActive) {
                val chunk = pcmPlaybackQueue.poll()
                if (chunk != null) {
                    if (currentSettings.voiceOutputEnabled) {
                        val amp = computePcmAmplitude(chunk)
                        updateVisualizerFromAmplitude(amp, isSpeaking = true)
                        track.write(chunk, 0, chunk.size)
                    }
                } else {
                    if (_telemetry.value.operationalState == JarvisOperationalState.SPEAKING && isModelTurnFinished) {
                        // Response playback finished -> automatically return to LISTENING
                        updateTelemetry {
                            it.copy(
                                operationalState = JarvisOperationalState.LISTENING,
                                isSpeakerActive = false,
                                audioAmplitude = 0.04f,
                                jarvisLiveResponse = "",
                                statusDetail = "Listening continuously — Speak your next command"
                            )
                        }
                    }
                    delay(20)
                }
            }
        }
    }

    private fun startAudioCaptureLoop() {
        audioCaptureJob?.cancel()
        audioCaptureJob = engineScope.launch {
            if (!hasRecordAudioPermission()) return@launch

            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            releaseAudioCapture()
            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            } catch (se: SecurityException) {
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@launch
            }

            // Attach hardware AcousticEchoCanceler, NoiseSuppressor, and AutomaticGainControl
            val sessionId = record.audioSessionId
            if ( currentSettings.echoControlEnabled && AcousticEchoCanceler.isAvailable()) {
                echoCanceler = runCatching {
                    AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                }.getOrNull()
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = runCatching {
                    NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                }.getOrNull()
            }
            if (AutomaticGainControl.isAvailable()) {
                gainControl = runCatching {
                    AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                }.getOrNull()
            }

            record.startRecording()
            audioRecord = record

            val buffer = ByteArray(3200) // 100ms chunks at 16kHz 16-bit mono
            var userSpeakingRecently = false
            var silenceFramesCount = 0
            var bargeInFramesCount = 0

            while (isActive && _telemetry.value.isCallingModeActive) {
                if (_telemetry.value.isMicMuted) {
                    updateVisualizerFromAmplitude(0.02f, isSpeaking = false)
                    delay(80)
                    continue
                }

                val bytesRead = record.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    val chunkBytes = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                    val rmsAmp = computePcmAmplitude(chunkBytes)

                    val currentlySpeaking = _telemetry.value.operationalState == JarvisOperationalState.SPEAKING

                    if (currentlySpeaking) {
                        // Echo control + Barge-in detection while JARVIS is speaking
                        if (currentSettings.bargeInEnabled && rmsAmp > 0.38f) {
                            bargeInFramesCount++
                            if (bargeInFramesCount >= 3) {
                                // User is actively speaking over JARVIS -> Trigger Barge-In!
                                bargeInFramesCount = 0
                                interruptSpeech(
                                    returnToListening = true,
                                    reason = "Barge-in detected — Listening to you"
                                )
                            }
                        } else {
                            bargeInFramesCount = 0
                        }
                        // If echo cancellation is active, we still stream or gate low-level echo
                        if (!currentSettings.echoControlEnabled || rmsAmp > 0.32f) {
                            sendPcmChunkOverWebSocket(chunkBytes)
                        }
                    } else {
                        updateVisualizerFromAmplitude(rmsAmp, isSpeaking = false)
                        sendPcmChunkOverWebSocket(chunkBytes)

                        if (rmsAmp > 0.11f) {
                            userSpeakingRecently = true
                            silenceFramesCount = 0
                            if (_telemetry.value.operationalState != JarvisOperationalState.LISTENING) {
                                updateTelemetry {
                                    it.copy(
                                        operationalState = JarvisOperationalState.LISTENING,
                                        statusDetail = "Hearing your voice..."
                                    )
                                }
                            }
                        } else if (userSpeakingRecently) {
                            silenceFramesCount++
                            // After ~900ms of silence following user speech -> show THINKING indicator
                            if (silenceFramesCount == 9) {
                                userSpeakingRecently = false
                                updateTelemetry {
                                    if (it.operationalState == JarvisOperationalState.LISTENING) {
                                        it.copy(
                                            operationalState = JarvisOperationalState.THINKING,
                                            statusDetail = "End of speech detected — Gemini thinking..."
                                        )
                                    } else {
                                        it
                                    }
                                }
                            }
                        }
                    }
                } else {
                    delay(20)
                }
            }
        }
    }

    private fun sendPcmChunkOverWebSocket(pcmBytes: ByteArray) {
        val ws = webSocket ?: return
        if (!isSetupComplete) return
        val b64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        val msg = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "mediaChunks",
                JSONArray().put(
                    JSONObject()
                        .put("mimeType", "audio/pcm;rate=16000")
                        .put("data", b64)
                )
            )
        )
        ws.send(msg.toString())
    }

    private fun computePcmAmplitude(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        var sumSquares = 0.0
        val sampleCount = pcmBytes.size / 2
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / sampleCount).toFloat()
        return (rms * 3.2f).coerceIn(0.02f, 1.0f)
    }

    private fun updateVisualizerFromAmplitude(amp: Float, isSpeaking: Boolean) {
        val now = System.currentTimeMillis() / 120.0
        val bands = List(16) { idx ->
            val wave = abs(sin(now + idx * 0.45) * cos(now * 0.7 - idx * 0.3)).toFloat()
            val base = if (isSpeaking) 0.15f else 0.06f
            (base + amp * (0.4f + 0.6f * wave)).coerceIn(0.05f, 1.0f)
        }
        updateTelemetry {
            it.copy(
                audioAmplitude = amp,
                spectrumBands = bands
            )
        }
    }

    private fun configureAudioRoutingForCall(enable: Boolean, echoControl: Boolean) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (enable) {
                am.mode = if (echoControl) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            } else {
                am.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    private fun playFeedbackChime(toneType: Int) {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            tg.startTone(toneType, 140)
        }
    }

    private fun releaseAudioCapture() {
        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        runCatching { gainControl?.release() }
        gainControl = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
    }

    private fun releaseAudioPlayback() {
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
    }

    fun isStopCommand(text: String): Boolean {
        val clean = text.lowercase(Locale.US).trim()
        return clean == "stop" ||
            clean == "jarvis stop" ||
            clean == "jarvis, stop" ||
            clean == "hey jarvis stop" ||
            clean.endsWith("jarvis stop")
    }

    private fun updateTelemetry(transform: (CallingSessionTelemetry) -> CallingSessionTelemetry) {
        _telemetry.value = transform(_telemetry.value)
    }

    fun shutdown() {
        stopCallingMode()
        runCatching { tts?.shutdown() }
        engineScope.coroutineContext.cancelChildren()
    }
}
