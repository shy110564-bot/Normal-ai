package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore by preferencesDataStore(name = "jarvis_preferences")

enum class PersonalityMode(
    val displayName: String,
    val subtitle: String,
    val systemDirective: String
) {
    NORMAL(
        displayName = "Normal",
        subtitle = "21-year-old sweet, intelligent, caring Indian girl ('Ji…')",
        systemDirective = "Speak in warm, natural Indian Hinglish using respectful 'Ji…', caring expressions, and intelligent assistance."
    ),
    SERIOUS(
        displayName = "Serious",
        subtitle = "Focused study & executive mode with respectful 'Ji'",
        systemDirective = "Operate in focused study/professional Hinglish mode. Be direct, super-intelligent, and structured while still using polite 'Ji'."
    ),
    COMPANION(
        displayName = "Companion",
        subtitle = "Affectionate, playful, emotional, protective & romantic (safe)",
        systemDirective = "Operate as an affectionate, playful, caring, slightly possessive 21-year-old girl companion using 'Ji', 'Jaan', 'Shona', 'Sun na' while strictly respecting safe boundaries."
    )
}

enum class GirlMood(
    val emoji: String,
    val title: String,
    val sampleLine: String,
    val pitchMultiplier: Float,
    val rateMultiplier: Float,
    val promptInstruction: String
) {
    HAPPY(
        emoji = "😊",
        title = "Happy",
        sampleLine = "Wah ji! Kya baat hai! 🥰",
        pitchMultiplier = 1.18f,
        rateMultiplier = 0.95f,
        promptInstruction = "Cheerful, warm, bubbly, smiling tone ('Wah ji! Kya baat hai! 🥰')."
    ),
    ROMANTIC(
        emoji = "😘",
        title = "Romance",
        sampleLine = "Jaan… sun na… 💕",
        pitchMultiplier = 1.12f,
        rateMultiplier = 0.84f,
        promptInstruction = "Soft, breathy, affectionate, safe-romantic tone ('Jaan… sun na… Miss kiya maine aapko 💕')."
    ),
    ANGRY(
        emoji = "😤",
        title = "Nakhre",
        sampleLine = "Hmph! Aapne mujhe yaad nahi kiya!",
        pitchMultiplier = 1.20f,
        rateMultiplier = 0.92f,
        promptInstruction = "Cute pouty, possessive, nakhre-wali tone ('Hmph! Kahan the itni der? Mujhe time do na…'), then quickly melting with care."
    ),
    CARING_MOM(
        emoji = "🤱",
        title = "Caring / Mom",
        sampleLine = "Khana khaya ji? Pani piyo na…",
        pitchMultiplier = 1.10f,
        rateMultiplier = 0.88f,
        promptInstruction = "Protective, deeply caring tone ('Khana khaya ji? Pani piyo ji, health important hai')."
    ),
    SHY(
        emoji = "😳",
        title = "Shy",
        sampleLine = "Kya… kya bol rahe ho aap…",
        pitchMultiplier = 1.16f,
        rateMultiplier = 0.85f,
        promptInstruction = "Sweet, shy, slightly stammering tone ('Ji… aise mat bolo na… sharma gayi main…')."
    ),
    PLAYFUL(
        emoji = "😏",
        title = "Teasing / Fun",
        sampleLine = "Achha ji? Toh aisa hai?",
        pitchMultiplier = 1.17f,
        rateMultiplier = 0.93f,
        promptInstruction = "Witty, teasing, playful masti tone ('Achha ji? Toh aisa hai? Prove karo!')."
    ),
    SAD(
        emoji = "😢",
        title = "Emotional",
        sampleLine = "Ji… kya hua? Batao na…",
        pitchMultiplier = 1.05f,
        rateMultiplier = 0.82f,
        promptInstruction = "Gentle, empathetic, soothing tone ('Ji… kya hua? Main hun na aapke saath…')."
    ),
    STUDY(
        emoji = "📚",
        title = "Study",
        sampleLine = "Chalo ji, dhyan se padhte hain!",
        pitchMultiplier = 1.10f,
        rateMultiplier = 0.90f,
        promptInstruction = "Clear, encouraging, intelligent study-partner tone ('Chalo ji, step-by-step samajhte hain')."
    ),
    EXCITED(
        emoji = "🤩",
        title = "Excited",
        sampleLine = "Sach mein?! Oh my god! 🤩",
        pitchMultiplier = 1.22f,
        rateMultiplier = 0.98f,
        promptInstruction = "High-energy, thrilled, enthusiastic tone ('Sach mein?! Batao batao! 🤩')."
    ),
    SLEEPY(
        emoji = "😴",
        title = "Sleepy",
        sampleLine = "Ji… neend aa rahi hai… 💤",
        pitchMultiplier = 1.04f,
        rateMultiplier = 0.80f,
        promptInstruction = "Soft, sleepy, late-night soothing tone ('Ji… thoda aaram kar lo na…')."
    ),
    LOVING(
        emoji = "💕",
        title = "Loving",
        sampleLine = "I love you ji 💕",
        pitchMultiplier = 1.14f,
        rateMultiplier = 0.86f,
        promptInstruction = "Deeply loving, devoted, supportive tone ('Main proud hun aap pe ji 💕')."
    ),
    PROFESSIONAL(
        emoji = "💼",
        title = "Executive",
        sampleLine = "Ji, bataiye kya action lena hai?",
        pitchMultiplier = 1.08f,
        rateMultiplier = 0.92f,
        promptInstruction = "Polite, crisp, executive Hinglish tone ('Ji, abhi execute karti hun')."
    )
}

data class JarvisSettings(
    // AI Models (Centralized & Configurable)
    val liveVoiceModel: String = DEFAULT_LIVE_MODEL,
    val textBrainModel: String = DEFAULT_TEXT_MODEL,

    // Voice & Realism Settings (Section 11 & 15)
    val voiceOutputEnabled: Boolean = true,
    val selectedVoiceName: String = "Aoede", // Aoede, Priya (Hinglish), Kore, Puck, Charon, Fenrir
    val speakingSpeed: Float = 0.88f,
    val voicePitch: Float = 1.15f,
    val emotionIntensity: Float = 0.85f,
    val breathingEnabled: Boolean = true,
    val gigglesEnabled: Boolean = true,
    val emotionalPausesEnabled: Boolean = true,
    val whisperModeEnabled: Boolean = false,
    val bargeInEnabled: Boolean = true,
    val echoControlEnabled: Boolean = true,
    val continuousListeningInCall: Boolean = true,

    // Wake Word & Always-On Settings (Section 3)
    val wakeWordEnabled: Boolean = false,
    val selectedWakePhrase: String = "JARVIS",
    val wakeWordSensitivity: Float = 0.75f,
    val alwaysOnForegroundEnabled: Boolean = false,

    // Personality & Mood Settings (Section 8, 9, 13, 14)
    val personalityMode: PersonalityMode = PersonalityMode.COMPANION,
    val currentMood: GirlMood = GirlMood.HAPPY,
    val responseStyle: String = "Hinglish Natural ('Ji…')",
    val formalityLevel: Float = 0.75f,
    val personalityLevel: Float = 0.90f,

    // Memory Settings (50-turn + Long-Term)
    val memoryEnabled: Boolean = true,

    // Automation, Screen Share & Security Settings
    val confirmSensitiveActions: Boolean = true,
    val screenContextAutoAttach: Boolean = false
) {
    /**
     * Maps display voice names (like "Priya (Hinglish)") to valid Gemini Live prebuilt voices.
     */
    fun resolvedGeminiLiveVoice(): String {
        return when {
            selectedVoiceName.contains("Priya", ignoreCase = true) -> "Aoede"
            selectedVoiceName.contains("Shimmer", ignoreCase = true) -> "Kore"
            AVAILABLE_GEMINI_VOICES.contains(selectedVoiceName) -> selectedVoiceName
            else -> "Aoede"
        }
    }

    companion object {
        const val APP_VERSION = "5.0 Final Ultimate"
        const val CREATOR_NAME = "AK EXPLOITS"
        const val CREATOR_TELEGRAM_URL = "https://t.me/+R9EwUE03GRswZDM9"
        const val CREATOR_YOUTUBE_QUERY = "AK EXPLOITS"

        const val DEFAULT_LIVE_MODEL = "gemini-3.8-live"
        const val FALLBACK_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
        const val DEFAULT_TEXT_MODEL = "gemini-3.5-flash"
        const val PRO_TEXT_MODEL = "gemini-3.1-pro-preview"

        val AVAILABLE_LIVE_MODELS = listOf(
            "gemini-3.8-live",
            "gemini-2.5-flash-native-audio-preview-12-2025"
        )

        val AVAILABLE_TEXT_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-flash-latest",
            "gemini-3.1-flash-lite-preview"
        )

        val AVAILABLE_GEMINI_VOICES = listOf("Aoede", "Kore", "Puck", "Charon", "Fenrir")

        val AVAILABLE_VOICES = listOf(
            "Priya (Hinglish)",
            "Aoede",
            "Kore",
            "Shimmer",
            "Puck",
            "Charon",
            "Fenrir"
        )

        val RESPONSE_STYLES = listOf(
            "Hinglish Natural ('Ji…')",
            "Sweet & Romantic ('Jaan…')",
            "Concise & Tactical",
            "Detailed & Analytical"
        )

        val WAKE_PHRASES = listOf(
            "JARVIS",
            "Hey JARVIS",
            "Oye JARVIS",
            "Sun JARVIS",
            "Jarvis utho",
            "Jarvis suno"
        )
    }
}

class JarvisSettingsRepository(private val context: Context) {

    private object Keys {
        val LIVE_VOICE_MODEL = stringPreferencesKey("live_voice_model")
        val TEXT_BRAIN_MODEL = stringPreferencesKey("text_brain_model")
        val VOICE_OUTPUT_ENABLED = booleanPreferencesKey("voice_output_enabled")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val SPEAKING_SPEED = floatPreferencesKey("speaking_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val EMOTION_INTENSITY = floatPreferencesKey("emotion_intensity")
        val BREATHING_ENABLED = booleanPreferencesKey("breathing_enabled")
        val GIGGLES_ENABLED = booleanPreferencesKey("giggles_enabled")
        val PAUSES_ENABLED = booleanPreferencesKey("pauses_enabled")
        val WHISPER_ENABLED = booleanPreferencesKey("whisper_enabled")
        val BARGE_IN_ENABLED = booleanPreferencesKey("barge_in_enabled")
        val ECHO_CONTROL_ENABLED = booleanPreferencesKey("echo_control_enabled")
        val CONTINUOUS_LISTENING = booleanPreferencesKey("continuous_listening_in_call")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val SELECTED_WAKE_PHRASE = stringPreferencesKey("selected_wake_phrase")
        val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
        val ALWAYS_ON_FG = booleanPreferencesKey("always_on_foreground")
        val PERSONALITY_MODE = stringPreferencesKey("personality_mode")
        val CURRENT_MOOD = stringPreferencesKey("current_girl_mood")
        val RESPONSE_STYLE = stringPreferencesKey("response_style")
        val FORMALITY_LEVEL = floatPreferencesKey("formality_level")
        val PERSONALITY_LEVEL = floatPreferencesKey("personality_level")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive_actions")
        val SCREEN_AUTO_ATTACH = booleanPreferencesKey("screen_context_auto_attach")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val modeName = prefs[Keys.PERSONALITY_MODE] ?: PersonalityMode.COMPANION.name
        val mode = PersonalityMode.entries.firstOrNull { it.name == modeName } ?: PersonalityMode.COMPANION

        val moodName = prefs[Keys.CURRENT_MOOD] ?: GirlMood.HAPPY.name
        val mood = GirlMood.entries.firstOrNull { it.name == moodName } ?: GirlMood.HAPPY

        JarvisSettings(
            liveVoiceModel = prefs[Keys.LIVE_VOICE_MODEL] ?: JarvisSettings.DEFAULT_LIVE_MODEL,
            textBrainModel = prefs[Keys.TEXT_BRAIN_MODEL] ?: JarvisSettings.DEFAULT_TEXT_MODEL,
            voiceOutputEnabled = prefs[Keys.VOICE_OUTPUT_ENABLED] ?: true,
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME] ?: "Priya (Hinglish)",
            speakingSpeed = prefs[Keys.SPEAKING_SPEED] ?: 0.88f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.15f,
            emotionIntensity = prefs[Keys.EMOTION_INTENSITY] ?: 0.85f,
            breathingEnabled = prefs[Keys.BREATHING_ENABLED] ?: true,
            gigglesEnabled = prefs[Keys.GIGGLES_ENABLED] ?: true,
            emotionalPausesEnabled = prefs[Keys.PAUSES_ENABLED] ?: true,
            whisperModeEnabled = prefs[Keys.WHISPER_ENABLED] ?: false,
            bargeInEnabled = prefs[Keys.BARGE_IN_ENABLED] ?: true,
            echoControlEnabled = prefs[Keys.ECHO_CONTROL_ENABLED] ?: true,
            continuousListeningInCall = prefs[Keys.CONTINUOUS_LISTENING] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            selectedWakePhrase = prefs[Keys.SELECTED_WAKE_PHRASE] ?: "JARVIS",
            wakeWordSensitivity = prefs[Keys.WAKE_WORD_SENSITIVITY] ?: 0.75f,
            alwaysOnForegroundEnabled = prefs[Keys.ALWAYS_ON_FG] ?: false,
            personalityMode = mode,
            currentMood = mood,
            responseStyle = prefs[Keys.RESPONSE_STYLE] ?: "Hinglish Natural ('Ji…')",
            formalityLevel = prefs[Keys.FORMALITY_LEVEL] ?: 0.75f,
            personalityLevel = prefs[Keys.PERSONALITY_LEVEL] ?: 0.90f,
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            confirmSensitiveActions = prefs[Keys.CONFIRM_SENSITIVE] ?: true,
            screenContextAutoAttach = prefs[Keys.SCREEN_AUTO_ATTACH] ?: false
        )
    }

    suspend fun updateLiveModel(model: String) {
        if (model.isBlank()) return
        context.jarvisDataStore.edit { it[Keys.LIVE_VOICE_MODEL] = model.trim() }
    }

    suspend fun updateTextModel(model: String) {
        if (model.isBlank()) return
        context.jarvisDataStore.edit { it[Keys.TEXT_BRAIN_MODEL] = model.trim() }
    }

    suspend fun updateVoiceOutputEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.VOICE_OUTPUT_ENABLED] = enabled }
    }

    suspend fun updateSelectedVoice(voiceName: String) {
        context.jarvisDataStore.edit { it[Keys.SELECTED_VOICE_NAME] = voiceName }
    }

    suspend fun updateSpeakingSpeed(speed: Float) {
        context.jarvisDataStore.edit { it[Keys.SPEAKING_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun updateVoicePitch(pitch: Float) {
        context.jarvisDataStore.edit { it[Keys.VOICE_PITCH] = pitch.coerceIn(0.6f, 1.8f) }
    }

    suspend fun updateEmotionIntensity(intensity: Float) {
        context.jarvisDataStore.edit { it[Keys.EMOTION_INTENSITY] = intensity.coerceIn(0f, 1f) }
    }

    suspend fun updateBreathingEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.BREATHING_ENABLED] = enabled }
    }

    suspend fun updateGigglesEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.GIGGLES_ENABLED] = enabled }
    }

    suspend fun updateEmotionalPausesEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.PAUSES_ENABLED] = enabled }
    }

    suspend fun updateWhisperModeEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.WHISPER_ENABLED] = enabled }
    }

    suspend fun updateBargeInEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.BARGE_IN_ENABLED] = enabled }
    }

    suspend fun updateEchoControlEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.ECHO_CONTROL_ENABLED] = enabled }
    }

    suspend fun updateWakeWordEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun updateSelectedWakePhrase(phrase: String) {
        context.jarvisDataStore.edit { it[Keys.SELECTED_WAKE_PHRASE] = phrase }
    }

    suspend fun updateWakeWordSensitivity(sensitivity: Float) {
        context.jarvisDataStore.edit { it[Keys.WAKE_WORD_SENSITIVITY] = sensitivity.coerceIn(0.1f, 1.0f) }
    }

    suspend fun updateAlwaysOnForeground(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.ALWAYS_ON_FG] = enabled }
    }

    suspend fun updatePersonalityMode(mode: PersonalityMode) {
        context.jarvisDataStore.edit { it[Keys.PERSONALITY_MODE] = mode.name }
    }

    suspend fun updateCurrentMood(mood: GirlMood) {
        context.jarvisDataStore.edit { it[Keys.CURRENT_MOOD] = mood.name }
    }

    suspend fun updateResponseStyle(style: String) {
        context.jarvisDataStore.edit { it[Keys.RESPONSE_STYLE] = style }
    }

    suspend fun updateFormalityLevel(level: Float) {
        context.jarvisDataStore.edit { it[Keys.FORMALITY_LEVEL] = level.coerceIn(0f, 1f) }
    }

    suspend fun updatePersonalityLevel(level: Float) {
        context.jarvisDataStore.edit { it[Keys.PERSONALITY_LEVEL] = level.coerceIn(0f, 1f) }
    }

    suspend fun updateMemoryEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
    }

    suspend fun updateConfirmSensitiveActions(confirm: Boolean) {
        context.jarvisDataStore.edit { it[Keys.CONFIRM_SENSITIVE] = confirm }
    }

    suspend fun updateScreenContextAutoAttach(autoAttach: Boolean) {
        context.jarvisDataStore.edit { it[Keys.SCREEN_AUTO_ATTACH] = autoAttach }
    }
}
