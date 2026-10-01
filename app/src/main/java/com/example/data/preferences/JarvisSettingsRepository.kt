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
        subtitle = "Intelligent, composed, helpful futuristic assistant",
        systemDirective = "Adopt a composed, intelligent, articulate JARVIS persona. Be helpful, clear, and proactive while maintaining futuristic sophistication."
    ),
    SERIOUS(
        displayName = "Serious",
        subtitle = "Tactical, mission-critical, ultra-concise executive mode",
        systemDirective = "Operate in tactical mission-critical mode. Deliver direct, high-precision, zero-fluff analytical responses with strict operational focus."
    ),
    COMPANION(
        displayName = "Companion",
        subtitle = "Warm, conversational, witty, and supportive partner",
        systemDirective = "Operate as a warm, witty, supportive personal companion. Be engaging, empathetic, and conversational while keeping JARVIS's signature charm."
    )
}

data class JarvisSettings(
    // AI Models (Centralized & Configurable)
    val liveVoiceModel: String = DEFAULT_LIVE_MODEL,
    val textBrainModel: String = DEFAULT_TEXT_MODEL,

    // Voice & Gemini Live Settings
    val voiceOutputEnabled: Boolean = true,
    val selectedVoiceName: String = "Puck", // Puck, Charon, Kore, Fenrir, Aoede
    val speakingSpeed: Float = 1.0f,
    val bargeInEnabled: Boolean = true,
    val echoControlEnabled: Boolean = true,
    val continuousListeningInCall: Boolean = true,

    // Wake Word Settings
    val wakeWordEnabled: Boolean = false,
    val selectedWakePhrase: String = "JARVIS", // "JARVIS" or "Hey JARVIS"
    val wakeWordSensitivity: Float = 0.72f,

    // Personality Settings (Manual selection only)
    val personalityMode: PersonalityMode = PersonalityMode.NORMAL,
    val responseStyle: String = "Balanced & Articulate", // "Concise & Tactical", "Balanced & Articulate", "Detailed & Analytical"
    val formalityLevel: Float = 0.7f,
    val personalityLevel: Float = 0.75f,

    // Memory Settings
    val memoryEnabled: Boolean = true,

    // Automation & Security Settings
    val confirmSensitiveActions: Boolean = true,
    val screenContextAutoAttach: Boolean = false
) {
    companion object {
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

        val AVAILABLE_VOICES = listOf(
            "Puck",
            "Charon",
            "Kore",
            "Fenrir",
            "Aoede"
        )

        val RESPONSE_STYLES = listOf(
            "Concise & Tactical",
            "Balanced & Articulate",
            "Detailed & Analytical"
        )

        val WAKE_PHRASES = listOf(
            "JARVIS",
            "Hey JARVIS"
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
        val BARGE_IN_ENABLED = booleanPreferencesKey("barge_in_enabled")
        val ECHO_CONTROL_ENABLED = booleanPreferencesKey("echo_control_enabled")
        val CONTINUOUS_LISTENING = booleanPreferencesKey("continuous_listening_in_call")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val SELECTED_WAKE_PHRASE = stringPreferencesKey("selected_wake_phrase")
        val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
        val PERSONALITY_MODE = stringPreferencesKey("personality_mode")
        val RESPONSE_STYLE = stringPreferencesKey("response_style")
        val FORMALITY_LEVEL = floatPreferencesKey("formality_level")
        val PERSONALITY_LEVEL = floatPreferencesKey("personality_level")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive_actions")
        val SCREEN_AUTO_ATTACH = booleanPreferencesKey("screen_context_auto_attach")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val modeName = prefs[Keys.PERSONALITY_MODE] ?: PersonalityMode.NORMAL.name
        val mode = PersonalityMode.entries.firstOrNull { it.name == modeName } ?: PersonalityMode.NORMAL

        JarvisSettings(
            liveVoiceModel = prefs[Keys.LIVE_VOICE_MODEL] ?: JarvisSettings.DEFAULT_LIVE_MODEL,
            textBrainModel = prefs[Keys.TEXT_BRAIN_MODEL] ?: JarvisSettings.DEFAULT_TEXT_MODEL,
            voiceOutputEnabled = prefs[Keys.VOICE_OUTPUT_ENABLED] ?: true,
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME] ?: "Puck",
            speakingSpeed = prefs[Keys.SPEAKING_SPEED] ?: 1.0f,
            bargeInEnabled = prefs[Keys.BARGE_IN_ENABLED] ?: true,
            echoControlEnabled = prefs[Keys.ECHO_CONTROL_ENABLED] ?: true,
            continuousListeningInCall = prefs[Keys.CONTINUOUS_LISTENING] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            selectedWakePhrase = prefs[Keys.SELECTED_WAKE_PHRASE] ?: "JARVIS",
            wakeWordSensitivity = prefs[Keys.WAKE_WORD_SENSITIVITY] ?: 0.72f,
            personalityMode = mode,
            responseStyle = prefs[Keys.RESPONSE_STYLE] ?: "Balanced & Articulate",
            formalityLevel = prefs[Keys.FORMALITY_LEVEL] ?: 0.7f,
            personalityLevel = prefs[Keys.PERSONALITY_LEVEL] ?: 0.75f,
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

    suspend fun updatePersonalityMode(mode: PersonalityMode) {
        context.jarvisDataStore.edit { it[Keys.PERSONALITY_MODE] = mode.name }
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
