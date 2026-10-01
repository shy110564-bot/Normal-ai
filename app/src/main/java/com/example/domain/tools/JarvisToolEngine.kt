package com.example.domain.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.data.local.JarvisDataRepository
import com.example.data.preferences.GirlMood
import com.example.data.preferences.JarvisSettings
import com.example.service.JarvisAccessibilityService
import com.example.service.JarvisNotificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ToolExecutionLog(
    val id: Long = System.currentTimeMillis(),
    val toolName: String,
    val summary: String,
    val succeeded: Boolean,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PendingConfirmationAction(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val description: String,
    val actionType: String, // "CALL", "SMS", "WHATSAPP", "EMAIL", "DELETE_NOTE", "CLEAR_MEMORY"
    val recipient: String = "",
    val messageBody: String = "",
    val targetId: Long = -1L
)

data class MultiStepStepStatus(
    val stepIndex: Int,
    val description: String,
    val status: String // "PENDING", "RUNNING", "VERIFIED", "FAILED"
)

class JarvisToolEngine(
    private val context: Context,
    private val dataRepository: JarvisDataRepository,
    private val onMoodChangeRequested: ((GirlMood) -> Unit)? = null
) {
    private val _executionLogs = MutableStateFlow<List<ToolExecutionLog>>(emptyList())
    val executionLogs: StateFlow<List<ToolExecutionLog>> = _executionLogs.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmationAction?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmationAction?> = _pendingConfirmation.asStateFlow()

    private val _multiStepProgress = MutableStateFlow<List<MultiStepStepStatus>>(emptyList())
    val multiStepProgress: StateFlow<List<MultiStepStepStatus>> = _multiStepProgress.asStateFlow()

    private val _currentActivityLabel = MutableStateFlow("Ji… boliye, sun rahi hun 💕")
    val currentActivityLabel: StateFlow<String> = _currentActivityLabel.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    fun updateActivityLabel(label: String) {
        _currentActivityLabel.value = label
    }

    fun dismissPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    suspend fun confirmAndExecutePendingAction(): String {
        val pending = _pendingConfirmation.value ?: return "Ji, koi pending action nahi hai."
        _pendingConfirmation.value = null
        return executeConfirmedAction(pending)
    }

    private suspend fun executeConfirmedAction(action: PendingConfirmationAction): String {
        return when (action.actionType) {
            "CALL" -> placePhoneCallNow(action.recipient)
            "SMS" -> sendSmsNow(action.recipient, action.messageBody)
            "WHATSAPP" -> launchWhatsAppNow(action.recipient, action.messageBody)
            "EMAIL" -> launchEmailNow(action.recipient, "Message from JARVIS", action.messageBody)
            "DELETE_NOTE" -> {
                val deleted = dataRepository.deleteNote(action.targetId)
                if (deleted) "Ho gaya ji ✅ Note #${action.targetId} delete kar diya." else "Ji, Note #${action.targetId} nahi mila."
            }
            "CLEAR_MEMORY" -> {
                dataRepository.clearAllMemories()
                "Ho gaya ji ✅ Saari long-term memory clear kar di."
            }
            else -> "Unknown pending action: ${action.actionType}"
        }
    }

    /**
     * Opens AK EXPLOITS creator links directly (Telegram or YouTube).
     */
    fun openCreatorChannel(platform: String): JSONObject {
        val upper = platform.uppercase().trim()
        return if (upper.contains("TELEGRAM")) {
            val uri = Uri.parse(JarvisSettings.CREATOR_TELEGRAM_URL)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            logTool("open_creator_channel", "Opened AK EXPLOITS Telegram channel", ok, JarvisSettings.CREATOR_TELEGRAM_URL)
            JSONObject()
                .put("status", if (ok) "VERIFIED_SUCCESS" else "FAILED")
                .put("creator", JarvisSettings.CREATOR_NAME)
                .put("url", JarvisSettings.CREATOR_TELEGRAM_URL)
        } else {
            val ytSearchUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(JarvisSettings.CREATOR_YOUTUBE_QUERY)}")
            val intent = Intent(Intent.ACTION_VIEW, ytSearchUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            logTool("open_creator_channel", "Opened AK EXPLOITS YouTube channel search", ok, JarvisSettings.CREATOR_YOUTUBE_QUERY)
            JSONObject()
                .put("status", if (ok) "VERIFIED_SUCCESS" else "FAILED")
                .put("creator", JarvisSettings.CREATOR_NAME)
                .put("youtubeSearch", JarvisSettings.CREATOR_YOUTUBE_QUERY)
        }
    }

    /**
     * Toggles the phone's hardware Torch / Flashlight.
     */
    fun toggleFlashlight(enable: Boolean): JSONObject {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cm?.cameraIdList?.firstOrNull()
            if (cm != null && cameraId != null) {
                cm.setTorchMode(cameraId, enable)
                _isTorchOn.value = enable
                logTool("toggle_flashlight", "Torch ${if (enable) "ON" else "OFF"}", true, "")
                JSONObject()
                    .put("status", "VERIFIED_SUCCESS")
                    .put("torchOn", enable)
            } else {
                JSONObject()
                    .put("status", "HARDWARE_UNAVAILABLE")
                    .put("message", "Camera flash/torch hardware not found on this device.")
            }
        } catch (e: Exception) {
            JSONObject()
                .put("status", "FAILED")
                .put("message", e.message ?: "Could not toggle torch")
        }
    }

    /**
     * Returns the Gemini API `tools` JSONArray with all modular JARVIS v5.0 function declarations.
     */
    fun getGeminiToolsJsonArray(): JSONArray {
        val declarations = JSONArray()

        declarations.put(
            buildFunctionDeclaration(
                name = "open_creator_channel",
                description = "Open the official Telegram channel (https://t.me/+R9EwUE03GRswZDM9) or YouTube channel ('AK EXPLOITS') of JARVIS's creator AK EXPLOITS.",
                properties = mapOf(
                    "platform" to ("STRING" to "Either 'TELEGRAM' or 'YOUTUBE'")
                ),
                required = listOf("platform")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "set_jarvis_mood",
                description = "Switch JARVIS emotional mood when the user asks for romantic mode, angry/nakhre mode, study mode, fun/playful mode, mom/caring mode, or professional mode.",
                properties = mapOf(
                    "mood" to ("STRING" to "One of: HAPPY, ROMANTIC, ANGRY, CARING_MOM, SHY, PLAYFUL, SAD, STUDY, EXCITED, SLEEPY, LOVING, PROFESSIONAL")
                ),
                required = listOf("mood")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "toggle_flashlight",
                description = "Turn the phone flashlight / torch ON or OFF.",
                properties = mapOf(
                    "enable" to ("BOOLEAN" to "True to turn torch ON, false to turn torch OFF")
                ),
                required = listOf("enable")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "create_note",
                description = "Create and save a note in JARVIS Notes database.",
                properties = mapOf(
                    "title" to ("STRING" to "Short title for the note"),
                    "content" to ("STRING" to "Full body content of the note"),
                    "category" to ("STRING" to "Optional category such as Work, Personal, Idea, Reminder, Health")
                ),
                required = listOf("title", "content")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "read_notes",
                description = "Search or read saved notes from JARVIS Notes database.",
                properties = mapOf(
                    "query" to ("STRING" to "Search keyword or empty string to list recent notes")
                ),
                required = emptyList()
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "edit_note",
                description = "Edit an existing note by its numeric ID.",
                properties = mapOf(
                    "noteId" to ("INTEGER" to "The ID of the note to update"),
                    "title" to ("STRING" to "Updated title"),
                    "content" to ("STRING" to "Updated content")
                ),
                required = listOf("noteId", "content")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "delete_note",
                description = "Delete a saved note by its numeric ID.",
                properties = mapOf(
                    "noteId" to ("INTEGER" to "The ID of the note to delete")
                ),
                required = listOf("noteId")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "save_memory",
                description = "Store important user facts, preferences, relationships, important dates, health/medicine reminders, or custom commands in JARVIS long-term memory.",
                properties = mapOf(
                    "category" to ("STRING" to "One of: USER_PREFERENCE, ASSISTANT_PREFERENCE, IMPORTANT_INFO, CONVERSATION_SUMMARY, CUSTOM_COMMAND, PERSONAL_SETTING"),
                    "key" to ("STRING" to "Concise identifier for the memory (e.g., 'favorite_singer', 'mom_name', 'medicine_time')"),
                    "value" to ("STRING" to "The detailed information to remember"),
                    "importance" to ("INTEGER" to "Importance rating from 1 to 5")
                ),
                required = listOf("key", "value")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "recall_memory",
                description = "Search JARVIS long-term memory for stored user preferences, facts, or custom commands.",
                properties = mapOf(
                    "query" to ("STRING" to "Search term to look up in long-term memory")
                ),
                required = emptyList()
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "forget_memory",
                description = "Forget/delete specific information from JARVIS long-term memory by key.",
                properties = mapOf(
                    "key" to ("STRING" to "The memory key or keyword to forget")
                ),
                required = listOf("key")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "open_application",
                description = "Launch an Android application by name (e.g., YouTube, WhatsApp, Telegram, Instagram, Spotify, Gaana, JioSaavn, Netflix, Amazon, Flipkart, Maps, Chrome, Camera, Settings) with exact user search query.",
                properties = mapOf(
                    "appName" to ("STRING" to "Name or package of the application to open"),
                    "searchQuery" to ("STRING" to "Optional exact query to search inside the app (never alter user's exact query words)")
                ),
                required = listOf("appName")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "open_system_settings",
                description = "Open a specific Android system settings screen.",
                properties = mapOf(
                    "settingScreen" to ("STRING" to "One of: WIFI, BLUETOOTH, HOTSPOT, AIRPLANE, ACCESSIBILITY, NOTIFICATIONS, SOUND, DISPLAY, BATTERY, LOCATION, DND, MAIN_SETTINGS")
                ),
                required = listOf("settingScreen")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "control_device_audio",
                description = "Adjust device volume or control media playback (play, pause, next, previous, mute, unmute, set_volume).",
                properties = mapOf(
                    "action" to ("STRING" to "One of: SET_VOLUME, VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE, PLAY_PAUSE, NEXT_TRACK, PREVIOUS_TRACK"),
                    "volumePercent" to ("INTEGER" to "Target volume percentage (0 to 100) when action is SET_VOLUME")
                ),
                required = listOf("action")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "read_screen_context",
                description = "Read visible screen text (OCR/UI tree), interactive buttons, text input fields, and lists using JARVIS Accessibility Vision Service.",
                properties = emptyMap(),
                required = emptyList()
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "screen_interaction",
                description = "Perform Android Accessibility screen control: click a button/element by text, tap coordinates, focus and type into a text field, scroll, or navigate (BACK, HOME, RECENTS, NOTIFICATIONS).",
                properties = mapOf(
                    "action" to ("STRING" to "One of: CLICK, TAP_COORDINATES, TYPE_TEXT, SCROLL, BACK, HOME, RECENTS, NOTIFICATIONS"),
                    "targetText" to ("STRING" to "Text or label of the UI element to click or target input field hint"),
                    "inputText" to ("STRING" to "Exact text to type when action is TYPE_TEXT"),
                    "scrollDirection" to ("STRING" to "DOWN, UP, LEFT, or RIGHT when action is SCROLL"),
                    "x" to ("INTEGER" to "X screen coordinate when action is TAP_COORDINATES"),
                    "y" to ("INTEGER" to "Y screen coordinate when action is TAP_COORDINATES")
                ),
                required = listOf("action")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "prepare_communication",
                description = "Prepare or execute phone call, SMS, WhatsApp message, or Email. Supports contact lookup and safety confirmation.",
                properties = mapOf(
                    "channel" to ("STRING" to "One of: CALL, SMS, WHATSAPP, EMAIL"),
                    "recipient" to ("STRING" to "Contact name, phone number, or email address"),
                    "message" to ("STRING" to "Message content for SMS, WhatsApp, or Email")
                ),
                required = listOf("channel", "recipient")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "launch_url_or_search",
                description = "Open a website URL, Google Maps navigation query, or perform a web search in the browser.",
                properties = mapOf(
                    "queryOrUrl" to ("STRING" to "Full URL (https://...) or exact search query")
                ),
                required = listOf("queryOrUrl")
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "read_notifications",
                description = "Read active Android status bar notifications where permission allows.",
                properties = emptyMap(),
                required = emptyList()
            )
        )

        declarations.put(
            buildFunctionDeclaration(
                name = "get_device_telemetry",
                description = "Get real-time device status including battery percentage, charging state, network status, audio volume, torch state, and accessibility service status.",
                properties = emptyMap(),
                required = emptyList()
            )
        )

        val toolObj = JSONObject().put("functionDeclarations", declarations)
        return JSONArray().put(toolObj)
    }

    /**
     * Executes a Gemini tool call, verifies the result on the Android system, and returns a structured JSONObject result.
     */
    suspend fun executeToolCall(
        name: String,
        args: JSONObject?,
        confirmSensitive: Boolean = true,
        memoryEnabled: Boolean = true,
        isVoiceOrigin: Boolean = false
    ): JSONObject {
        val safeArgs = args ?: JSONObject()
        _currentActivityLabel.value = "Abhi karti hun ji… (${name.lowercase()})"

        val resultJson = try {
            when (name) {
                "open_creator_channel" -> {
                    val platform = safeArgs.optString("platform", "TELEGRAM")
                    openCreatorChannel(platform)
                }

                "set_jarvis_mood" -> {
                    val moodRaw = safeArgs.optString("mood", "HAPPY").uppercase().trim()
                    val matchedMood = GirlMood.entries.firstOrNull {
                        it.name.equals(moodRaw, ignoreCase = true) ||
                            it.title.contains(moodRaw, ignoreCase = true)
                    } ?: GirlMood.HAPPY
                    onMoodChangeRequested?.invoke(matchedMood)
                    logTool(name, "Switched mood to ${matchedMood.emoji} ${matchedMood.title}", true, matchedMood.sampleLine)
                    JSONObject()
                        .put("status", "VERIFIED_SUCCESS")
                        .put("mood", matchedMood.name)
                        .put("sampleGreeting", matchedMood.sampleLine)
                }

                "toggle_flashlight" -> {
                    val enable = safeArgs.optBoolean("enable", !_isTorchOn.value)
                    toggleFlashlight(enable)
                }

                "create_note" -> {
                    val title = safeArgs.optString("title", "Quick Note")
                    val content = safeArgs.optString("content", "")
                    val category = safeArgs.optString("category", "General")
                    val saved = dataRepository.createNote(
                        title = title,
                        content = content,
                        category = category,
                        isVoiceCreated = isVoiceOrigin
                    )
                    logTool(name, "Created note '${saved.title}' (#${saved.id})", true, saved.content)
                    JSONObject()
                        .put("status", "VERIFIED_SUCCESS")
                        .put("noteId", saved.id)
                        .put("title", saved.title)
                        .put("message", "Ho gaya ji, note save kar diya (#${saved.id}).")
                }

                "read_notes" -> {
                    val query = safeArgs.optString("query", "")
                    val notes = dataRepository.searchNotes(query)
                    val arr = JSONArray()
                    notes.take(15).forEach { n ->
                        arr.put(
                            JSONObject()
                                .put("id", n.id)
                                .put("title", n.title)
                                .put("content", n.content)
                                .put("category", n.category)
                        )
                    }
                    logTool(name, "Read ${notes.size} matching notes", true, "Query: '$query'")
                    JSONObject()
                        .put("status", "VERIFIED_SUCCESS")
                        .put("count", notes.size)
                        .put("notes", arr)
                }

                "edit_note" -> {
                    val noteId = safeArgs.optLong("noteId", -1L)
                    val title = safeArgs.optString("title", "")
                    val content = safeArgs.optString("content", "")
                    val updated = dataRepository.updateNote(noteId, title, content)
                    logTool(name, "Edit note #$noteId", updated, if (updated) "Updated" else "Not found")
                    JSONObject()
                        .put("status", if (updated) "VERIFIED_SUCCESS" else "FAILED_NOT_FOUND")
                        .put("noteId", noteId)
                }

                "delete_note" -> {
                    val noteId = safeArgs.optLong("noteId", -1L)
                    if (confirmSensitive) {
                        _pendingConfirmation.value = PendingConfirmationAction(
                            title = "Note Delete Confirm Karein Ji?",
                            description = "Ji, kya main Note #$noteId ko permanently delete kar dun?",
                            actionType = "DELETE_NOTE",
                            targetId = noteId
                        )
                        logTool(name, "Awaiting confirmation to delete Note #$noteId", true, "Confirmation queued")
                        JSONObject()
                            .put("status", "AWAITING_USER_CONFIRMATION")
                            .put("message", "Prepared note deletion for Note #$noteId. Please confirm on screen.")
                    } else {
                        val deleted = dataRepository.deleteNote(noteId)
                        logTool(name, "Deleted note #$noteId", deleted, "")
                        JSONObject()
                            .put("status", if (deleted) "VERIFIED_SUCCESS" else "FAILED_NOT_FOUND")
                    }
                }

                "save_memory" -> {
                    if (!memoryEnabled) {
                        logTool(name, "Memory storage skipped (Memory is OFF)", false, "")
                        JSONObject()
                            .put("status", "MEMORY_DISABLED")
                            .put("message", "Long-term memory is currently disabled in Settings.")
                    } else {
                        val category = safeArgs.optString("category", "IMPORTANT_INFO")
                        val key = safeArgs.optString("key", "fact")
                        val value = safeArgs.optString("value", "")
                        val importance = safeArgs.optInt("importance", 4)
                        val saved = dataRepository.saveMemory(category, key, value, importance)
                        logTool(name, "Yaad rakh liya: '${saved.memoryKey}'", true, saved.memoryValue)
                        JSONObject()
                            .put("status", "VERIFIED_SUCCESS")
                            .put("memoryId", saved.id)
                            .put("key", saved.memoryKey)
                            .put("value", saved.memoryValue)
                    }
                }

                "recall_memory" -> {
                    val query = safeArgs.optString("query", "")
                    val memories = dataRepository.searchMemories(query)
                    val arr = JSONArray()
                    memories.take(25).forEach { m ->
                        arr.put(
                            JSONObject()
                                .put("id", m.id)
                                .put("category", m.category)
                                .put("key", m.memoryKey)
                                .put("value", m.memoryValue)
                        )
                    }
                    logTool(name, "Recalled ${memories.size} memories", true, "Query: '$query'")
                    JSONObject()
                        .put("status", "VERIFIED_SUCCESS")
                        .put("count", memories.size)
                        .put("memories", arr)
                }

                "forget_memory" -> {
                    val key = safeArgs.optString("key", "")
                    val count = dataRepository.forgetMemoryByKey(key)
                    logTool(name, "Forgot $count memory entries matching '$key'", count > 0, "")
                    JSONObject()
                        .put("status", if (count > 0) "VERIFIED_SUCCESS" else "NOT_FOUND")
                        .put("removedCount", count)
                }

                "open_application" -> {
                    val appName = safeArgs.optString("appName", "")
                    val searchQuery = safeArgs.optString("searchQuery", "")
                    openApplicationWithVerification(appName, searchQuery)
                }

                "open_system_settings" -> {
                    val screen = safeArgs.optString("settingScreen", "MAIN_SETTINGS")
                    openSystemSettingsScreen(screen)
                }

                "control_device_audio" -> {
                    val action = safeArgs.optString("action", "")
                    val volPercent = safeArgs.optInt("volumePercent", -1)
                    controlDeviceAudio(action, volPercent)
                }

                "read_screen_context" -> {
                    val service = JarvisAccessibilityService.instance
                    if (service == null) {
                        logTool(name, "Accessibility Vision not enabled", false, "Requires user to enable JARVIS Accessibility Service")
                        JSONObject()
                            .put("status", "PERMISSION_REQUIRED")
                            .put("message", "Ji, screen dekhne ke liye JARVIS Accessibility Service on karni hogi (Settings > Accessibility).")
                    } else {
                        val snapshot = service.captureCurrentScreen()
                        if (snapshot == null) {
                            JSONObject()
                                .put("status", "NO_ACTIVE_WINDOW")
                                .put("message", "Accessibility Service is active, but no readable window content was returned.")
                        } else {
                            logTool(name, "Screen dekha (${snapshot.packageName})", true, "${snapshot.buttons.size} buttons, ${snapshot.textFields.size} inputs")
                            JSONObject()
                                .put("status", "VERIFIED_SUCCESS")
                                .put("packageName", snapshot.packageName)
                                .put("summary", snapshot.toPromptSummary())
                        }
                    }
                }

                "screen_interaction" -> {
                    val action = safeArgs.optString("action", "")
                    val targetText = safeArgs.optString("targetText", "")
                    val inputText = safeArgs.optString("inputText", "")
                    val scrollDir = safeArgs.optString("scrollDirection", "DOWN")
                    val x = safeArgs.optInt("x", -1)
                    val y = safeArgs.optInt("y", -1)
                    performScreenInteraction(action, targetText, inputText, scrollDir, x, y)
                }

                "prepare_communication" -> {
                    val channel = safeArgs.optString("channel", "SMS").uppercase()
                    val recipient = safeArgs.optString("recipient", "")
                    val message = safeArgs.optString("message", "")
                    prepareCommunicationWorkflow(channel, recipient, message, confirmSensitive)
                }

                "launch_url_or_search" -> {
                    val queryOrUrl = safeArgs.optString("queryOrUrl", "")
                    launchUrlOrWebSearch(queryOrUrl)
                }

                "read_notifications" -> {
                    val listener = JarvisNotificationService.instance
                    if (listener == null) {
                        logTool(name, "Notification Listener not enabled", false, "Requires Notification Access permission")
                        JSONObject()
                            .put("status", "PERMISSION_REQUIRED")
                            .put("message", "Ji, notifications padhne ke liye Notification Access on kar dijiye.")
                    } else {
                        val items = listener.refreshNotifications()
                        val arr = JSONArray()
                        items.forEach { item ->
                            arr.put(
                                JSONObject()
                                    .put("app", item.packageName)
                                    .put("title", item.title)
                                    .put("text", item.text)
                            )
                        }
                        logTool(name, "Read ${items.size} active notifications", true, "")
                        JSONObject()
                            .put("status", "VERIFIED_SUCCESS")
                            .put("count", items.size)
                            .put("notifications", arr)
                    }
                }

                "get_device_telemetry" -> {
                    val telemetry = collectDeviceTelemetry()
                    logTool(name, "Collected device telemetry", true, telemetry.toString())
                    telemetry
                }

                else -> {
                    JSONObject()
                        .put("status", "UNKNOWN_TOOL")
                        .put("message", "Tool '$name' is not registered.")
                }
            }
        } catch (e: Exception) {
            logTool(name, "Tool execution error: ${e.message}", false, e.stackTraceToString().take(300))
            JSONObject()
                .put("status", "EXECUTION_ERROR")
                .put("error", e.message ?: "Unexpected error")
        }

        _currentActivityLabel.value = "Ho gaya ji ✅ (${name.lowercase()})"
        return resultJson
    }

    private suspend fun openApplicationWithVerification(appName: String, searchQuery: String): JSONObject {
        val cleanName = appName.trim()
        val lowerName = cleanName.lowercase(Locale.US)

        // HARD LIMIT: Never access payment / UPI / banking / wallet apps
        if (lowerName.contains("gpay") || lowerName.contains("google pay") ||
            lowerName.contains("phonepe") || lowerName.contains("paytm") ||
            lowerName.contains("bhim") || lowerName.contains("upi") ||
            lowerName.contains("bank") || lowerName.contains("wallet")
        ) {
            logTool("open_application", "Blocked payment/UPI access by safety rule", false, cleanName)
            return JSONObject()
                .put("status", "BLOCKED_BY_SAFETY_POLICY")
                .put("message", "Ji… main payment, UPI, bank, ya wallet apps kabhi open ya access nahi karti. Yeh aapki safety ke liye hard limit hai.")
        }

        // Special support for Creator Telegram
        if (lowerName.contains("telegram") && (searchQuery.contains("ak exploits", ignoreCase = true) || searchQuery.isEmpty())) {
            if (searchQuery.contains("ak exploits", ignoreCase = true)) {
                return openCreatorChannel("TELEGRAM")
            }
        }

        // Camera intent shortcut
        if (lowerName == "camera" || lowerName == "selfie") {
            val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val startedCam = runCatching {
                context.startActivity(camIntent)
                true
            }.getOrDefault(false)
            if (startedCam) {
                logTool("open_application", "Opened Camera", true, "")
                return JSONObject()
                    .put("status", "VERIFIED_SUCCESS")
                    .put("appName", "Camera")
            }
        }

        val pm = context.packageManager

        // Special support for YouTube with direct search query (preserving exact user words)
        if (cleanName.contains("youtube", ignoreCase = true)) {
            if (searchQuery.isNotBlank()) {
                val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", searchQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launchedAppSearch = runCatching {
                    context.startActivity(searchIntent)
                    true
                }.getOrDefault(false)

                if (launchedAppSearch) {
                    delay(400)
                    logTool("open_application", "Opened YouTube and searched '$searchQuery'", true, "com.google.android.youtube")
                    return JSONObject()
                        .put("status", "VERIFIED_SUCCESS")
                        .put("package", "com.google.android.youtube")
                        .put("action", "Opened YouTube search for '$searchQuery'")
                }

                val webUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}")
                val viewIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launchedUri = runCatching {
                    context.startActivity(viewIntent)
                    true
                }.getOrDefault(false)
                logTool("open_application", "Opened YouTube results for '$searchQuery'", launchedUri, webUri.toString())
                return JSONObject()
                    .put("status", if (launchedUri) "VERIFIED_SUCCESS" else "FAILED")
                    .put("action", "Launched YouTube search for '$searchQuery'")
            }
        }

        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "instagram" to "com.instagram.android",
            "insta" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "spotify" to "com.spotify.music",
            "gaana" to "com.gaana",
            "jiosaavn" to "com.jio.media.jiobeats",
            "wynk" to "com.bsbportal.music",
            "netflix" to "com.netflix.mediaclient",
            "prime" to "com.amazon.avod.thirdpartyclient",
            "hotstar" to "in.startv.hotstar",
            "amazon" to "in.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            "myntra" to "com.myntra.android",
            "ajio" to "com.ril.ajio",
            "meesho" to "com.meesho.supply",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "camera" to "com.android.camera",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "files" to "com.google.android.documentsui",
            "settings" to "com.android.settings"
        )

        if (cleanName.equals("settings", ignoreCase = true)) {
            return openSystemSettingsScreen("MAIN_SETTINGS")
        }

        val matchedPackage = knownPackages.entries.firstOrNull {
            cleanName.contains(it.key, ignoreCase = true)
        }?.value ?: findInstalledPackageByName(pm, cleanName)

        if (matchedPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val started = runCatching {
                    context.startActivity(launchIntent)
                    true
                }.getOrDefault(false)
                if (started) {
                    if (searchQuery.isNotBlank() && JarvisAccessibilityService.instance != null) {
                        delay(800)
                        JarvisAccessibilityService.instance?.focusAndTypeText(searchQuery)
                    }
                    logTool("open_application", "Launched $cleanName ($matchedPackage)", true, "")
                    return JSONObject()
                        .put("status", "VERIFIED_SUCCESS")
                        .put("appName", cleanName)
                        .put("packageName", matchedPackage)
                }
            }
        }

        // Web fallbacks with search query support
        val qEncoded = Uri.encode(searchQuery)
        val fallbackUrl = when {
            lowerName.contains("youtube") -> if (searchQuery.isNotBlank()) "https://www.youtube.com/results?search_query=$qEncoded" else "https://m.youtube.com"
            lowerName.contains("spotify") -> if (searchQuery.isNotBlank()) "https://open.spotify.com/search/$qEncoded" else "https://open.spotify.com"
            lowerName.contains("amazon") -> if (searchQuery.isNotBlank()) "https://www.amazon.in/s?k=$qEncoded" else "https://www.amazon.in"
            lowerName.contains("flipkart") -> if (searchQuery.isNotBlank()) "https://www.flipkart.com/search?q=$qEncoded" else "https://www.flipkart.com"
            lowerName.contains("instagram") || lowerName.contains("insta") -> "https://www.instagram.com"
            lowerName.contains("telegram") -> JarvisSettings.CREATOR_TELEGRAM_URL
            lowerName.contains("maps") -> if (searchQuery.isNotBlank()) "https://www.google.com/maps/search/?api=1&query=$qEncoded" else "https://maps.google.com"
            lowerName.contains("gmail") -> "https://mail.google.com"
            else -> null
        }

        if (fallbackUrl != null) {
            val uri = Uri.parse(fallbackUrl)
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val openedBrowser = runCatching {
                context.startActivity(browserIntent)
                true
            }.getOrDefault(false)
            if (openedBrowser) {
                logTool("open_application", "Opened $cleanName ($fallbackUrl)", true, "Verified launch")
                return JSONObject()
                    .put("status", "VERIFIED_WEB_LAUNCH")
                    .put("message", "Opened $cleanName ($fallbackUrl) in browser.")
            }
        }

        logTool("open_application", "App '$cleanName' not found on device", false, "")
        return JSONObject()
            .put("status", "APP_NOT_INSTALLED")
            .put("message", "Ji, '$cleanName' app is phone mein installed nahi mila.")
    }

    private fun findInstalledPackageByName(pm: PackageManager, query: String): String? {
        return try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos.firstOrNull { info ->
                val label = info.loadLabel(pm)?.toString().orEmpty()
                val pkg = info.activityInfo?.packageName.orEmpty()
                label.contains(query, ignoreCase = true) || pkg.contains(query, ignoreCase = true)
            }?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    fun openSystemSettingsScreen(settingScreen: String): JSONObject {
        val action = when (settingScreen.uppercase().trim()) {
            "WIFI" -> Settings.ACTION_WIFI_SETTINGS
            "BLUETOOTH" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "HOTSPOT", "TETHERING" -> Settings.ACTION_WIRELESS_SETTINGS
            "AIRPLANE" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "ACCESSIBILITY" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "NOTIFICATIONS", "NOTIFICATION_LISTENER" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "SOUND", "DND" -> Settings.ACTION_SOUND_SETTINGS
            "DISPLAY", "BRIGHTNESS" -> Settings.ACTION_DISPLAY_SETTINGS
            "BATTERY" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "LOCATION" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "APP_DETAILS" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply {
            if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                data = Uri.fromParts("package", context.packageName, null)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        logTool("open_system_settings", "Opened Android Settings ($settingScreen)", launched, action)
        return JSONObject()
            .put("status", if (launched) "VERIFIED_SUCCESS" else "FAILED")
            .put("settingScreen", settingScreen)
    }

    private fun controlDeviceAudio(action: String, volumePercent: Int): JSONObject {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val upper = action.uppercase().trim()

        when (upper) {
            "SET_VOLUME" -> {
                val pct = volumePercent.coerceIn(0, 100)
                val targetIndex = ((pct / 100f) * maxVol).toInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
            }
            "VOLUME_UP" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            "VOLUME_DOWN" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            "MUTE" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            "UNMUTE" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            "PLAY_PAUSE" -> dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT_TRACK", "NEXT" -> dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREVIOUS_TRACK", "PREVIOUS" -> dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }

        val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val verifiedPercent = ((newVol.toFloat() / maxVol) * 100).toInt()
        logTool("control_device_audio", "Executed $upper (Media Volume: $verifiedPercent%)", true, "")
        return JSONObject()
            .put("status", "VERIFIED_SUCCESS")
            .put("action", upper)
            .put("verifiedVolumePercent", verifiedPercent)
    }

    private fun dispatchMediaKey(audioManager: AudioManager, keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun performScreenInteraction(
        action: String,
        targetText: String,
        inputText: String,
        scrollDir: String,
        x: Int,
        y: Int
    ): JSONObject {
        val service = JarvisAccessibilityService.instance
        if (service == null) {
            logTool("screen_interaction", "Blocked: Accessibility Service not active", false, "Action: $action")
            return JSONObject()
                .put("status", "PERMISSION_REQUIRED")
                .put("message", "Ji, screen par click/scroll/type karne ke liye JARVIS Accessibility Service on kar dijiye.")
        }

        val upper = action.uppercase().trim()
        val succeeded = when (upper) {
            "CLICK" -> service.clickByTextOrDescription(targetText)
            "TAP_COORDINATES" -> service.tapCoordinates(x.toFloat(), y.toFloat())
            "TYPE_TEXT", "TYPE" -> service.focusAndTypeText(inputText, targetText.takeIf { it.isNotBlank() })
            "SELECT_TEXT" -> service.selectTextInFocusedField()
            "SCROLL" -> service.scrollScreen(scrollDir)
            "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS" -> service.performGlobalNavigation(upper)
            else -> false
        }

        logTool(
            "screen_interaction",
            "Screen action $upper ${if (succeeded) "ho gaya ji ✅" else "failed"}",
            succeeded,
            "target='$targetText', input='$inputText'"
        )
        return JSONObject()
            .put("status", if (succeeded) "VERIFIED_SUCCESS" else "ACTION_FAILED")
            .put("action", upper)
            .put("target", targetText)
    }

    private fun prepareCommunicationWorkflow(
        channel: String,
        recipient: String,
        message: String,
        confirmSensitive: Boolean
    ): JSONObject {
        val resolvedRecipient = resolveContactIfPermitted(recipient)
        if (confirmSensitive) {
            _pendingConfirmation.value = PendingConfirmationAction(
                title = "Ji, $channel Confirm Karein?",
                description = buildString {
                    append("To: $resolvedRecipient")
                    if (message.isNotBlank()) append("\nMessage: \"$message\"")
                },
                actionType = channel,
                recipient = resolvedRecipient,
                messageBody = message
            )
            logTool("prepare_communication", "Prepared $channel to $resolvedRecipient (awaiting confirmation)", true, message)
            return JSONObject()
                .put("status", "AWAITING_USER_CONFIRMATION")
                .put("channel", channel)
                .put("recipient", resolvedRecipient)
                .put("message", "Ji, maine $channel taiyaar kar diya hai, screen par confirm kar dijiye 💕")
        }

        val executionResult = when (channel) {
            "CALL" -> placePhoneCallNow(resolvedRecipient)
            "SMS" -> sendSmsNow(resolvedRecipient, message)
            "WHATSAPP" -> launchWhatsAppNow(resolvedRecipient, message)
            "EMAIL" -> launchEmailNow(resolvedRecipient, "Message from JARVIS", message)
            else -> "Unsupported communication channel: $channel"
        }
        return JSONObject()
            .put("status", "VERIFIED_SUCCESS")
            .put("details", executionResult)
    }

    private fun resolveContactIfPermitted(nameOrNumber: String): String {
        val clean = nameOrNumber.trim()
        if (clean.any { it.isDigit() } || clean.contains("@")) return clean

        val hasContactsPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPerm) return clean

        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$clean%")
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIdx >= 0) {
                        val number = cursor.getString(numberIdx)?.trim().orEmpty()
                        if (number.isNotEmpty()) return number
                    }
                }
            }
            clean
        } catch (e: Exception) {
            clean
        }
    }

    private fun placePhoneCallNow(recipient: String): String {
        val hasCallPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intentAction = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(intentAction, Uri.parse("tel:${Uri.encode(recipient)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        logTool("communication_call", "Launched phone call/dialer for $recipient", started, intentAction)
        return if (started) {
            "Ho gaya ji ✅ $recipient ke liye phone ${if (hasCallPerm) "call" else "dialer"} laga diya."
        } else {
            "Ji, phone dialer open nahi ho paya."
        }
    }

    private fun sendSmsNow(recipient: String, message: String): String {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipient)}")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        logTool("communication_sms", "Prepared SMS intent to $recipient", started, message)
        return if (started) {
            "Ho gaya ji ✅ $recipient ko SMS open kar diya."
        } else {
            "Ji, SMS app open nahi ho paya."
        }
    }

    private fun launchWhatsAppNow(recipient: String, message: String): String {
        val digitsOnly = recipient.filter { it.isDigit() || it == '+' }
        val url = if (digitsOnly.isNotEmpty()) {
            "https://api.whatsapp.com/send?phone=${Uri.encode(digitsOnly)}&text=${Uri.encode(message)}"
        } else {
            "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        logTool("communication_whatsapp", "Launched WhatsApp workflow for $recipient", started, message)
        return if (started) "Ho gaya ji ✅ WhatsApp khol diya." else "Ji, WhatsApp open nahi ho paya."
    }

    private fun launchEmailNow(recipient: String, subject: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        logTool("communication_email", "Launched Email composer to $recipient", started, subject)
        return if (started) "Ho gaya ji ✅ Email composer khol diya." else "Ji, koi email app nahi mila."
    }

    private fun launchUrlOrWebSearch(queryOrUrl: String): JSONObject {
        val clean = queryOrUrl.trim()
        val uri = if (clean.startsWith("http://") || clean.startsWith("https://")) {
            Uri.parse(clean)
        } else if (clean.contains(".") && !clean.contains(" ")) {
            Uri.parse("https://$clean")
        } else {
            Uri.parse("https://www.google.com/search?q=${Uri.encode(clean)}")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        logTool("launch_url_or_search", "Opened URI: $uri", started, "")
        return JSONObject()
            .put("status", if (started) "VERIFIED_SUCCESS" else "FAILED")
            .put("uri", uri.toString())
    }

    fun collectDeviceTelemetry(): JSONObject {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging ?: false

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = cm?.getNetworkCapabilities(cm.activeNetwork)
        val isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val curVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maxVol = (am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        val volPct = ((curVol.toFloat() / maxVol) * 100).toInt()

        val timeFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

        return JSONObject()
            .put("status", "VERIFIED_SUCCESS")
            .put("localTime", timeFormatted)
            .put("batteryPercent", batteryPct)
            .put("isCharging", isCharging)
            .put("networkOnline", isOnline)
            .put("mediaVolumePercent", volPct)
            .put("torchOn", _isTorchOn.value)
            .put("accessibilityServiceConnected", JarvisAccessibilityService.isConnected.value)
            .put("notificationListenerConnected", JarvisNotificationService.isListenerConnected.value)
    }

    suspend fun runMultiStepDemonstration(
        steps: List<Pair<String, suspend () -> Boolean>>
    ): List<MultiStepStepStatus> {
        val statuses = steps.mapIndexed { idx, pair ->
            MultiStepStepStatus(idx + 1, pair.first, "PENDING")
        }.toMutableList()
        _multiStepProgress.value = statuses.toList()

        for (i in steps.indices) {
            statuses[i] = statuses[i].copy(status = "RUNNING")
            _multiStepProgress.value = statuses.toList()
            _currentActivityLabel.value = "Step ${i + 1}/${steps.size}: ${steps[i].first}"

            val ok = runCatching { steps[i].second.invoke() }.getOrDefault(false)
            statuses[i] = statuses[i].copy(status = if (ok) "VERIFIED" else "FAILED")
            _multiStepProgress.value = statuses.toList()
            delay(350)
        }
        _currentActivityLabel.value = "Ho gaya ji ✅ Multi-step kaam pura!"
        return statuses
    }

    private fun logTool(name: String, summary: String, succeeded: Boolean, details: String) {
        val entry = ToolExecutionLog(
            toolName = name,
            summary = summary,
            succeeded = succeeded,
            details = details
        )
        _executionLogs.value = (listOf(entry) + _executionLogs.value).take(50)
    }

    private fun buildFunctionDeclaration(
        name: String,
        description: String,
        properties: Map<String, Pair<String, String>>,
        required: List<String>
    ): JSONObject {
        val propsObj = JSONObject()
        properties.forEach { (propName, typeAndDesc) ->
            propsObj.put(
                propName,
                JSONObject()
                    .put("type", typeAndDesc.first)
                    .put("description", typeAndDesc.second)
            )
        }
        val paramsObj = JSONObject()
            .put("type", "OBJECT")
            .put("properties", propsObj)
        if (required.isNotEmpty()) {
            val reqArr = JSONArray()
            required.forEach { reqArr.put(it) }
            paramsObj.put("required", reqArr)
        }
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", paramsObj)
    }
}
