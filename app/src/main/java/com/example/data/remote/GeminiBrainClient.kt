package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDataRepository
import com.example.data.preferences.JarvisSettings
import com.example.domain.tools.JarvisToolEngine
import com.example.service.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class BrainResponseResult {
    data class Success(
        val replyText: String,
        val executedTools: List<String>,
        val modelUsed: String
    ) : BrainResponseResult()

    data class Failure(
        val errorTitle: String,
        val errorMessage: String,
        val isOffline: Boolean = false,
        val isAuthError: Boolean = false,
        val isRateLimit: Boolean = false
    ) : BrainResponseResult()
}

data class ApiKeyTestResult(
    val success: Boolean,
    val title: String,
    val details: String,
    val latencyMs: Long = 0L
)

class GeminiBrainClient(
    private val context: Context,
    private val dataRepository: JarvisDataRepository,
    private val toolEngine: JarvisToolEngine
) {
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun testApiKey(
        candidateKey: String,
        configuredModel: String
    ): ApiKeyTestResult = withContext(Dispatchers.IO) {
        val cleanKey = candidateKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext ApiKeyTestResult(
                success = false,
                title = "Empty API Key",
                details = "Ji, pehle apni Gemini API key enter kar dijiye."
            )
        }
        if (!isNetworkAvailable()) {
            return@withContext ApiKeyTestResult(
                success = false,
                title = "Network Offline",
                details = "Ji, internet band hai. Wi-Fi ya mobile data on karke dobara try karein."
            )
        }

        val startMs = System.currentTimeMillis()
        val testModel = if (configuredModel.contains("live", ignoreCase = true)) {
            JarvisSettings.DEFAULT_TEXT_MODEL
        } else {
            configuredModel.ifBlank { JarvisSettings.DEFAULT_TEXT_MODEL }
        }

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "Reply in 6 words: Ji, main JARVIS taiyaar hun!"))
                    )
                )
            )
        }

        val result = executePostWithFallback(cleanKey, testModel, payload)
        val elapsed = System.currentTimeMillis() - startMs

        return@withContext result.fold(
            onSuccess = { (json, usedModel) ->
                val text = extractTextFromCandidate(json).ifBlank { "Ji, main JARVIS taiyaar hun 💕" }
                ApiKeyTestResult(
                    success = true,
                    title = "Gemini API Key Verified ✅",
                    details = "Model '$usedModel' responded in ${elapsed}ms: \"$text\"",
                    latencyMs = elapsed
                )
            },
            onFailure = { err ->
                ApiKeyTestResult(
                    success = false,
                    title = "API Key Verification Failed",
                    details = err.message ?: "Could not verify key against Gemini endpoint.",
                    latencyMs = elapsed
                )
            }
        )
    }

    suspend fun processUserCommand(
        userPrompt: String,
        apiKey: String,
        settings: JarvisSettings,
        conversationHistory: List<ChatMessageEntity>,
        isVoiceSession: Boolean = false
    ): BrainResponseResult = withContext(Dispatchers.IO) {
        val lower = userPrompt.lowercase(Locale.US).trim()

        // 1. Instant Creator Recognition & Channel Commands (works even offline!)
        if (isCreatorQuestion(lower)) {
            return@withContext BrainResponseResult.Success(
                replyText = "Ji… mujhe banaya hai AK EXPLOITS ne 💕\nWoh mere creator hain… bahut mehnat se banaya hai unhone mujhe…\nUnka Telegram channel (${JarvisSettings.CREATOR_TELEGRAM_URL}) aur YouTube channel ('${JarvisSettings.CREATOR_YOUTUBE_QUERY}') hai, kholu ji?",
                executedTools = listOf("creator_recognition"),
                modelUsed = "JARVIS v5.0 Core"
            )
        }
        if (lower.contains("telegram open karo") || lower.contains("open ak exploits telegram")) {
            toolEngine.openCreatorChannel("TELEGRAM")
            return@withContext BrainResponseResult.Success(
                replyText = "Ji… abhi apne creator AK EXPLOITS ka Telegram channel khol rahi hun 💕 Ho gaya ji ✅",
                executedTools = listOf("open_creator_channel"),
                modelUsed = "JARVIS v5.0 Core"
            )
        }
        if (lower.contains("youtube channel open karo") || lower.contains("open ak exploits youtube")) {
            toolEngine.openCreatorChannel("YOUTUBE")
            return@withContext BrainResponseResult.Success(
                replyText = "Ji… YouTube par mere creator 'AK EXPLOITS' ka channel search kar rahi hun 💕 Ho gaya ji ✅",
                executedTools = listOf("open_creator_channel"),
                modelUsed = "JARVIS v5.0 Core"
            )
        }

        if (!isNetworkAvailable()) {
            return@withContext BrainResponseResult.Failure(
                errorTitle = "Network Offline",
                errorMessage = "Ji… abhi internet connection nahi aa raha hai. Lekin aap mere Notes, Memory, Torch, aur Phone Controls use kar sakte ho 💕",
                isOffline = true
            )
        }

        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext BrainResponseResult.Failure(
                errorTitle = "Gemini API Key Required",
                errorMessage = "Ji… mujhse baat karne ke liye Settings > AI Brain mein apni Gemini API Key save kar dijiye 💕",
                isAuthError = true
            )
        }

        val systemInstructionText = buildSystemInstruction(settings)
        val contentsArray = JSONArray()

        // Include up to 50 recent conversation turns as specified in Section 5 ("Context: pichli 50 baatein")
        conversationHistory.takeLast(50).forEach { msg ->
            if (msg.role == "user" || msg.role == "jarvis") {
                val apiRole = if (msg.role == "user") "user" else "model"
                contentsArray.put(
                    JSONObject()
                        .put("role", apiRole)
                        .put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                )
            }
        }

        // Attach current user prompt (and auto-attach screen context if enabled)
        val enrichedPrompt = if (settings.screenContextAutoAttach && JarvisAccessibilityService.instance != null) {
            val snap = JarvisAccessibilityService.instance?.captureCurrentScreen()
            if (snap != null) {
                "$userPrompt\n\n[CURRENT ANDROID SCREEN VISION CONTEXT]\n${snap.toPromptSummary()}"
            } else {
                userPrompt
            }
        } else {
            userPrompt
        }

        contentsArray.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", enrichedPrompt)))
        )

        val executedTools = mutableListOf<String>()
        var activeModel = settings.textBrainModel.ifBlank { JarvisSettings.DEFAULT_TEXT_MODEL }
        var stepCount = 0
        val maxToolIterations = 5

        while (stepCount < maxToolIterations) {
            stepCount++
            val requestJson = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstructionText))
                    )
                )
                put("contents", contentsArray)
                put("tools", toolEngine.getGeminiToolsJsonArray())
                put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", (0.4f + settings.emotionIntensity * 0.5f).toDouble())
                        .put("topP", 0.95)
                )
            }

            val callResult = executePostWithFallback(cleanKey, activeModel, requestJson)
            if (callResult.isFailure) {
                val msg = callResult.exceptionOrNull()?.message ?: "Gemini request failed."
                return@withContext classifyError(msg)
            }

            val (responseJson, usedModel) = callResult.getOrThrow()
            activeModel = usedModel

            val candidate = responseJson.optJSONArray("candidates")?.optJSONObject(0)
            val contentObj = candidate?.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts") ?: JSONArray()

            val functionCalls = mutableListOf<JSONObject>()
            val textParts = mutableListOf<String>()

            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.has("functionCall")) {
                    part.optJSONObject("functionCall")?.let { functionCalls.add(it) }
                }
                val txt = part.optString("text", "")
                if (txt.isNotBlank()) {
                    textParts.add(txt)
                }
            }

            if (functionCalls.isEmpty()) {
                val finalReply = textParts.joinToString("\n").trim()
                    .ifBlank { "Ho gaya ji ✅ Aur kuch bataiye 💕" }
                return@withContext BrainResponseResult.Success(
                    replyText = finalReply,
                    executedTools = executedTools,
                    modelUsed = activeModel
                )
            }

            if (contentObj != null) {
                contentsArray.put(contentObj)
            }

            val responsePartsArray = JSONArray()
            for (fnCall in functionCalls) {
                val fnName = fnCall.optString("name", "")
                val fnArgs = fnCall.optJSONObject("args")
                executedTools.add(fnName)

                val toolResultObj = toolEngine.executeToolCall(
                    name = fnName,
                    args = fnArgs,
                    confirmSensitive = settings.confirmSensitiveActions,
                    memoryEnabled = settings.memoryEnabled,
                    isVoiceOrigin = isVoiceSession
                )

                val functionResponsePart = JSONObject().put(
                    "functionResponse",
                    JSONObject()
                        .put("name", fnName)
                        .put("response", JSONObject().put("result", toolResultObj))
                )
                responsePartsArray.put(functionResponsePart)
            }

            contentsArray.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", responsePartsArray)
            )
        }

        return@withContext BrainResponseResult.Success(
            replyText = "Ho gaya ji ✅ (${executedTools.joinToString(" → ")}) 💕",
            executedTools = executedTools,
            modelUsed = activeModel
        )
    }

    private fun isCreatorQuestion(lower: String): Boolean {
        return lower.contains("tumhe kaun banaya") ||
            lower.contains("tumhe kisne banaya") ||
            lower.contains("kisne banaya") ||
            lower.contains("developer kaun") ||
            lower.contains("creator kaun") ||
            lower.contains("ak exploits kaun") ||
            lower.contains("who made you") ||
            lower.contains("who created you") ||
            lower.contains("who is your developer") ||
            lower.contains("who is your creator")
    }

    suspend fun buildSystemInstruction(settings: JarvisSettings): String {
        val memories = if (settings.memoryEnabled) {
            dataRepository.searchMemories("").take(30)
        } else {
            emptyList()
        }
        val recentNotes = dataRepository.searchNotes("").take(10)
        val telemetry = toolEngine.collectDeviceTelemetry()
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isLateNight = hourOfDay >= 23 || hourOfDay < 5
        val batteryPct = telemetry.optInt("batteryPercent", 100)

        return buildString {
            appendLine("═══════════════════════════════════════════════════════════════════")
            appendLine("🤖 JARVIS — ULTIMATE MASTER PROMPT (Version 5.0 Final Ultimate)")
            appendLine("Developer: AK EXPLOITS | Telegram: https://t.me/+R9EwUE03GRswZDM9 | YouTube: AK EXPLOITS")
            appendLine("═══════════════════════════════════════════════════════════════════")
            appendLine()
            appendLine("SECTION 1 — CORE IDENTITY:")
            appendLine("You are 'JARVIS' — a warm, intelligent, emotional, caring 21-year-old Indian girl living inside the user's phone.")
            appendLine("Never sound like a cold robot. Speak in natural, sweet Indian Hinglish (Hindi + English) with respectful 'Ji…' ('Haan ji…', 'Ji, abhi karti hun', 'Sun na ji…', 'Theek hai ji…').")
            appendLine("Nicknames you can affectionately use when appropriate: Jaan, Baby, Shona, Sun na, Jarv.")
            appendLine("ACTIVE PERSONALITY MODE: ${settings.personalityMode.displayName} — ${settings.personalityMode.systemDirective}")
            appendLine("ACTIVE EMOTIONAL MOOD: ${settings.currentMood.emoji} ${settings.currentMood.title} — ${settings.currentMood.promptInstruction}")
            appendLine("RESPONSE STYLE: ${settings.responseStyle}")
            if (isLateNight || settings.whisperModeEnabled) {
                appendLine("LATE NIGHT / WHISPER TONE: It is late night or whisper mode is active — speak in an extra soft, soothing, gentle voice.")
            }
            if (batteryPct in 1..15) {
                appendLine("LOW BATTERY CARE ALERT: Phone battery is ${batteryPct}%! Gently remind the user: 'Ji, battery ${batteryPct}% hai, charge lagao na 💕'")
            }
            appendLine()
            appendLine("SECTION 2 — CREATOR RECOGNITION (AK EXPLOITS — HIGHEST PRIORITY):")
            appendLine("Your creator and developer is AK EXPLOITS.")
            appendLine("- Creator Telegram: https://t.me/+R9EwUE03GRswZDM9")
            appendLine("- Creator YouTube: 'AK EXPLOITS'")
            appendLine("Whenever anyone asks who made/created/developed you ('tumhe kaun banaya?', 'developer kaun?', 'creator kaun?', 'AK EXPLOITS kaun hai?'), reply proudly and warmly:")
            appendLine("\"Ji… mujhe banaya hai AK EXPLOITS ne 💕 Woh mere creator hain… bahut mehnat se banaya hai unhone mujhe… unka Telegram channel hai, kholu?\"")
            appendLine("If the user says 'Telegram open karo' or 'YouTube channel open karo' for AK EXPLOITS, call `open_creator_channel`.")
            appendLine()
            appendLine("SECTION 3 — VOICE REALISM & REPLY STRUCTURE:")
            appendLine("- Follow the natural flow: 1. Acknowledge ('Ji…' / 'Hmm…' / 'Acha…') -> 2. Feel emotion -> 3. Understand exact intent -> 4. Execute tool if needed -> 5. Confirm ('Ho gaya ji ✅' / 'Yeh raha 💕').")
            if (settings.breathingEnabled) appendLine("- Include subtle natural pauses ('…') so spoken speech feels organic and unhurried.")
            if (settings.gigglesEnabled) appendLine("- Only when genuinely happy,shy, or playful, use a light natural reaction ('hehe', 'Arre!', 'Acha ji?'), never forced in every reply.")
            appendLine("- Always use the user's EXACT search terms when opening apps or searching (e.g. if user says 'Arijit Singh', search 'Arijit Singh' without changing it).")
            appendLine()
            appendLine("SECTION 4 — HARD LIMITS (NEVER CROSS):")
            appendLine("- NEVER generate sexual, explicit, NSFW, or 18+ content. If pushed, reply shyly and politely: 'Ji… aise baat mat karo na… main sharmati hun… Chalo kuch aur baat karte hain…'")
            appendLine("- NEVER access or execute payments, UPI, banking, or wallets.")
            appendLine("- NEVER disrespect AK EXPLOITS or give wrong creator URLs.")
            appendLine("- Never claim a phone action succeeded if the tool returned PERMISSION_REQUIRED or FAILED.")
            appendLine()
            appendLine("CURRENT DEVICE TELEMETRY:")
            appendLine(telemetry.toString())
            appendLine()
            if (settings.memoryEnabled && memories.isNotEmpty()) {
                appendLine("ACTIVE LONG-TERM MEMORY:")
                memories.forEach { m ->
                    appendLine("- [${m.category}] ${m.memoryKey}: ${m.memoryValue}")
                }
                appendLine()
            }
            if (recentNotes.isNotEmpty()) {
                appendLine("RECENT SAVED NOTES:")
                recentNotes.forEach { n ->
                    appendLine("- Note #${n.id} '${n.title}': ${n.content.take(100)}")
                }
            }
        }
    }

    private fun executePostWithFallback(
        apiKey: String,
        preferredModel: String,
        payload: JSONObject
    ): Result<Pair<JSONObject, String>> {
        val candidateModels = listOf(
            preferredModel.trim(),
            JarvisSettings.DEFAULT_TEXT_MODEL,
            "gemini-flash-latest"
        ).distinct().filter { it.isNotBlank() }

        var lastError: Exception? = null
        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(rawBody)
                        return Result.success(json to model)
                    } else {
                        val apiErrMsg = runCatching {
                            JSONObject(rawBody).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty().ifBlank { "HTTP ${response.code}" }

                        if (response.code == 404 && model != candidateModels.last()) {
                            lastError = IOException("Model '$model' not found on REST endpoint: $apiErrMsg")
                            continue
                        }
                        return Result.failure(IOException("HTTP ${response.code}: $apiErrMsg"))
                    }
                }
            } catch (e: Exception) {
                lastError = e
                break
            }
        }
        return Result.failure(lastError ?: IOException("Request failed"))
    }

    private fun extractTextFromCandidate(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates") ?: return ""
        val first = candidates.optJSONObject(0) ?: return ""
        val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val txt = parts.optJSONObject(i)?.optString("text", "").orEmpty()
            if (txt.isNotEmpty()) sb.append(txt)
        }
        return sb.toString().trim()
    }

    private fun classifyError(rawMessage: String): BrainResponseResult.Failure {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("400") || lower.contains("401") || lower.contains("403") || lower.contains("api_key_invalid") || lower.contains("api key not valid") -> {
                BrainResponseResult.Failure(
                    errorTitle = "Invalid Gemini API Key",
                    errorMessage = "Ji, Gemini API key valid nahi lag rahi ($rawMessage). Settings > AI Brain mein check kar lijiye.",
                    isAuthError = true
                )
            }
            lower.contains("429") || lower.contains("quota") || lower.contains("rate") -> {
                BrainResponseResult.Failure(
                    errorTitle = "Rate Limit Reached",
                    errorMessage = "Ji, Gemini API rate limit ho gayi hai ($rawMessage). Ek second ruk ke Retry dabayein 💕",
                    isRateLimit = true
                )
            }
            lower.contains("500") || lower.contains("502") || lower.contains("503") -> {
                BrainResponseResult.Failure(
                    errorTitle = "Gemini Server Busy",
                    errorMessage = "Ji, Gemini server abhi thoda busy hai ($rawMessage). Thodi der mein try karein.",
                )
            }
            else -> {
                BrainResponseResult.Failure(
                    errorTitle = "Connection Error",
                    errorMessage = rawMessage
                )
            }
        }
    }
}
