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
                details = "Please enter a valid Gemini API key before testing."
            )
        }
        if (!isNetworkAvailable()) {
            return@withContext ApiKeyTestResult(
                success = false,
                title = "Network Offline",
                details = "Device has no active internet connection. Connect to Wi-Fi or mobile data and retry."
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
                        JSONArray().put(JSONObject().put("text", "Respond with: JARVIS CORE ONLINE"))
                    )
                )
            )
        }

        val result = executePostWithFallback(cleanKey, testModel, payload)
        val elapsed = System.currentTimeMillis() - startMs

        return@withContext result.fold(
            onSuccess = { (json, usedModel) ->
                val text = extractTextFromCandidate(json).ifBlank { "JARVIS CORE ONLINE" }
                ApiKeyTestResult(
                    success = true,
                    title = "Gemini API Key Verified",
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
        if (!isNetworkAvailable()) {
            return@withContext BrainResponseResult.Failure(
                errorTitle = "Network Unavailable",
                errorMessage = "JARVIS is currently offline. Local Notes, Memory inspection, and Device Controls remain available. Reconnect to reach Gemini AI.",
                isOffline = true
            )
        }

        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext BrainResponseResult.Failure(
                errorTitle = "Gemini API Key Required",
                errorMessage = "No Gemini API key is configured. Open Settings > AI Brain to enter and test your Gemini API Key (or set GEMINI_API_KEY in AI Studio Secrets).",
                isAuthError = true
            )
        }

        val systemInstructionText = buildSystemInstruction(settings)
        val contentsArray = JSONArray()

        // Include recent conversation turns for context
        conversationHistory.takeLast(12).forEach { msg ->
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
                "$userPrompt\n\n[CURRENT ANDROID SCREEN CONTEXT]\n${snap.toPromptSummary()}"
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
                        .put("temperature", (0.3f + settings.personalityLevel * 0.6f).toDouble())
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

            // Check if the model returned any functionCall parts
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
                    .ifBlank { "Task completed." }
                return@withContext BrainResponseResult.Success(
                    replyText = finalReply,
                    executedTools = executedTools,
                    modelUsed = activeModel
                )
            }

            // Append the model's functionCall turn to contentsArray
            if (contentObj != null) {
                contentsArray.put(contentObj)
            }

            // Execute all requested tool calls sequentially (supporting multi-step tasks!)
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
            replyText = "Executed multi-step workflow (${executedTools.joinToString(" → ")}).",
            executedTools = executedTools,
            modelUsed = activeModel
        )
    }

    suspend fun buildSystemInstruction(settings: JarvisSettings): String {
        val memories = if (settings.memoryEnabled) {
            dataRepository.searchMemories("").take(25)
        } else {
            emptyList()
        }
        val recentNotes = dataRepository.searchNotes("").take(10)
        val telemetry = toolEngine.collectDeviceTelemetry()

        val formalityDesc = when {
            settings.formalityLevel >= 0.75f -> "High formality (address the user respectfully and precisely)"
            settings.formalityLevel >= 0.4f -> "Balanced modern professional tone"
            else -> "Relaxed, natural, and direct"
        }

        return buildString {
            appendLine("You are JARVIS (Just A Rather Very Intelligent System), a complete futuristic Android AI assistant powered by Google Gemini.")
            appendLine("PERSONALITY MODE: ${settings.personalityMode.displayName} — ${settings.personalityMode.systemDirective}")
            appendLine("RESPONSE STYLE: ${settings.responseStyle}")
            appendLine("FORMALITY: $formalityDesc")
            appendLine()
            appendLine("OPERATIONAL RULES:")
            appendLine("1. You have real Android tools for Notes, Long-Term Memory, Opening Apps (including YouTube search), System Settings, Audio/Media volume control, Screen Reading & Screen Control (via Accessibility Service), Communication (Phone Call, SMS, WhatsApp, Email), and Web launching.")
            appendLine("2. For multi-step commands (e.g., 'Open YouTube, search for Android AI tutorials, and save a note'), call the required tools in sequence and verify each step's result before reporting success.")
            appendLine("3. Never claim an action succeeded if a tool returned PERMISSION_REQUIRED, APP_NOT_INSTALLED, or FAILED. Instead, clearly inform the user what happened and how to enable the needed permission or setting.")
            appendLine("4. If the user asks you to remember a preference or personal fact and Memory is enabled, call `save_memory`.")
            appendLine("5. Keep spoken/voice responses natural, clear, and well-paced for real-time conversation.")
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

                        // If 404 model not found on REST endpoint (e.g. live-only model name), try fallback model
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
                    errorMessage = "The Gemini API rejected the key ($rawMessage). Please verify or replace your API key in Settings > AI Brain.",
                    isAuthError = true
                )
            }
            lower.contains("429") || lower.contains("quota") || lower.contains("rate") -> {
                BrainResponseResult.Failure(
                    errorTitle = "Rate Limit / Quota Reached",
                    errorMessage = "Gemini API rate limit reached ($rawMessage). Wait a moment and tap Retry.",
                    isRateLimit = true
                )
            }
            lower.contains("500") || lower.contains("502") || lower.contains("503") -> {
                BrainResponseResult.Failure(
                    errorTitle = "Gemini Server Temporarily Unavailable",
                    errorMessage = "Google Gemini servers returned a temporary error ($rawMessage). Please retry shortly."
                )
            }
            else -> {
                BrainResponseResult.Failure(
                    errorTitle = "Connection / API Error",
                    errorMessage = rawMessage
                )
            }
        }
    }
}
