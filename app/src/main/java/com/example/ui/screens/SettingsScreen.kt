package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.JarvisSettings
import com.example.data.preferences.PersonalityMode
import com.example.data.remote.ApiKeyTestResult
import com.example.data.security.ApiKeyStatus
import com.example.ui.components.HudSectionHeader
import com.example.ui.components.HudStatusBadge
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisArcBlue
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisSurfaceCard
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: JarvisSettings,
    keyStatus: ApiKeyStatus,
    apiKeyTestResult: ApiKeyTestResult?,
    isTestingApiKey: Boolean,
    isAccessibilityConnected: Boolean,
    isNotificationConnected: Boolean,
    hasMicPermission: Boolean,
    hasContactsPermission: Boolean,
    hasCallPermission: Boolean,
    hasSmsPermission: Boolean,
    onSaveApiKey: (String) -> Unit,
    onTestApiKey: (String) -> Unit,
    onDeleteApiKey: () -> Unit,
    onUpdateLiveModel: (String) -> Unit,
    onUpdateTextModel: (String) -> Unit,
    onUpdateVoiceEnabled: (Boolean) -> Unit,
    onUpdateSelectedVoice: (String) -> Unit,
    onUpdateSpeakingSpeed: (Float) -> Unit,
    onUpdateBargeIn: (Boolean) -> Unit,
    onUpdateEchoControl: (Boolean) -> Unit,
    onUpdateWakeWordEnabled: (Boolean) -> Unit,
    onUpdateWakePhrase: (String) -> Unit,
    onUpdateWakeSensitivity: (Float) -> Unit,
    onUpdatePersonalityMode: (PersonalityMode) -> Unit,
    onUpdateResponseStyle: (String) -> Unit,
    onUpdateFormality: (Float) -> Unit,
    onUpdatePersonalityLevel: (Float) -> Unit,
    onUpdateMemoryEnabled: (Boolean) -> Unit,
    onNavigateToMemoryVault: () -> Unit,
    onClearAllMemories: () -> Unit,
    onUpdateConfirmSensitive: (Boolean) -> Unit,
    onUpdateScreenAutoAttach: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSystemSettings: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onLaunchCallingMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var customLiveModelInput by remember(settings.liveVoiceModel) { mutableStateOf(settings.liveVoiceModel) }
    var customTextModelInput by remember(settings.textBrainModel) { mutableStateOf(settings.textBrainModel) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian)
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HudSectionHeader(
                title = "JARVIS CORE CONFIGURATION",
                subtitle = "AI Brain, Gemini Live Voice, Wake Word, Personality, Memory, Automation & Security"
            )
        }

        // 1. AI BRAIN & GEMINI API KEY CONFIGURATION
        item {
            SettingsCard(
                title = "1. AI BRAIN & GEMINI API",
                icon = Icons.Default.Key,
                badgeLabel = if (keyStatus.isConfigured) "KEY CONFIGURED" else "KEY REQUIRED",
                badgeColor = if (keyStatus.isConfigured) JarvisEmerald else JarvisAmber
            ) {
                Text(
                    text = "Active Source: ${keyStatus.source.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisCyan
                )
                Text(
                    text = "Stored Key Mask: ${keyStatus.maskedPreview}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Enter or Replace Gemini API Key") },
                    placeholder = { Text("AIzaSy... (Encrypted via Android Keystore)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (apiKeyInput.isNotBlank()) {
                                onSaveApiKey(apiKeyInput)
                                apiKeyInput = ""
                            }
                        },
                        enabled = apiKeyInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = JarvisObsidian
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_api_key_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Key", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onTestApiKey(apiKeyInput) },
                        enabled = !isTestingApiKey,
                        border = BorderStroke(1.dp, JarvisEmerald),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_api_key_btn")
                    ) {
                        if (isTestingApiKey) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = JarvisEmerald
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisEmerald, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Key", color = JarvisEmerald)
                    }

                    OutlinedButton(
                        onClick = {
                            apiKeyInput = ""
                            onDeleteApiKey()
                        },
                        border = BorderStroke(1.dp, JarvisCrimson),
                        modifier = Modifier.testTag("delete_api_key_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete API Key", tint = JarvisCrimson, modifier = Modifier.size(16.dp))
                    }
                }

                if (apiKeyTestResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = if (apiKeyTestResult.success) JarvisEmerald.copy(alpha = 0.14f) else JarvisCrimson.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (apiKeyTestResult.success) JarvisEmerald else JarvisCrimson),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = apiKeyTestResult.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (apiKeyTestResult.success) JarvisEmerald else JarvisCrimson,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = apiKeyTestResult.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Configurable Gemini Live Voice Model
                Text(
                    text = "GEMINI LIVE VOICE MODEL (REAL-TIME WEBSOCKET)",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisCyan
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JarvisSettings.AVAILABLE_LIVE_MODELS.forEach { model ->
                        FilterChip(
                            selected = settings.liveVoiceModel == model,
                            onClick = {
                                customLiveModelInput = model
                                onUpdateLiveModel(model)
                            },
                            label = { Text(model) }
                        )
                    }
                }
                OutlinedTextField(
                    value = customLiveModelInput,
                    onValueChange = {
                        customLiveModelInput = it
                        onUpdateLiveModel(it)
                    },
                    label = { Text("Custom Live Voice Model Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_model_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Configurable Gemini Text / Brain Model
                Text(
                    text = "GEMINI CORE BRAIN / TOOL MODEL",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisCyan
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JarvisSettings.AVAILABLE_TEXT_MODELS.forEach { model ->
                        FilterChip(
                            selected = settings.textBrainModel == model,
                            onClick = {
                                customTextModelInput = model
                                onUpdateTextModel(model)
                            },
                            label = { Text(model) }
                        )
                    }
                }
                OutlinedTextField(
                    value = customTextModelInput,
                    onValueChange = {
                        customTextModelInput = it
                        onUpdateTextModel(it)
                    },
                    label = { Text("Custom Core Brain Model Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("text_model_input")
                )
            }
        }

        // 2. VOICE & GEMINI LIVE SETTINGS
        item {
            SettingsCard(
                title = "2. GEMINI LIVE VOICE SETTINGS",
                icon = Icons.Default.RecordVoiceOver,
                badgeLabel = settings.selectedVoiceName.uppercase(),
                badgeColor = JarvisCyan
            ) {
                SettingsToggleRow(
                    title = "Voice Output Enabled",
                    subtitle = "Use Gemini Live native audio & spoken responses",
                    checked = settings.voiceOutputEnabled,
                    onCheckedChange = onUpdateVoiceEnabled
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select Gemini Live Prebuilt Voice:",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisCyan
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    JarvisSettings.AVAILABLE_VOICES.forEach { voice ->
                        FilterChip(
                            selected = settings.selectedVoiceName == voice,
                            onClick = { onUpdateSelectedVoice(voice) },
                            label = { Text(voice) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                                selectedLabelColor = JarvisCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Speaking Speed: ${String.format(Locale.US, "%.2fx", settings.speakingSpeed)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisTextPrimary
                )
                Slider(
                    value = settings.speakingSpeed,
                    onValueChange = onUpdateSpeakingSpeed,
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                )

                SettingsToggleRow(
                    title = "Natural Interruption (Barge-In)",
                    subtitle = "Immediately stop JARVIS speaking when you start talking or say 'JARVIS, stop'",
                    checked = settings.bargeInEnabled,
                    onCheckedChange = onUpdateBargeIn
                )

                SettingsToggleRow(
                    title = "Hardware Acoustic Echo Cancellation",
                    subtitle = "Prevent JARVIS from hearing its own speaker output while speaking",
                    checked = settings.echoControlEnabled,
                    onCheckedChange = onUpdateEchoControl
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLaunchCallingMode,
                    border = BorderStroke(1.dp, JarvisCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Dedicated Calling Mode", color = JarvisCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. WAKE WORD ENGINE SETTINGS
        item {
            SettingsCard(
                title = "3. WAKE WORD ENGINE",
                icon = Icons.Default.Mic,
                badgeLabel = if (settings.wakeWordEnabled) "ARMED: ${settings.selectedWakePhrase}" else "STANDBY",
                badgeColor = if (settings.wakeWordEnabled) JarvisEmerald else JarvisTextMuted
            ) {
                SettingsToggleRow(
                    title = "Wake Word Detection ON/OFF",
                    subtitle = "Hands-free activation via separate Wake Word Engine",
                    checked = settings.wakeWordEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasMicPermission) {
                            onRequestPermission(android.Manifest.permission.RECORD_AUDIO)
                        }
                        onUpdateWakeWordEnabled(enabled)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Selected Wake Phrase:",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisCyan
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    JarvisSettings.WAKE_PHRASES.forEach { phrase ->
                        FilterChip(
                            selected = settings.selectedWakePhrase == phrase,
                            onClick = { onUpdateWakePhrase(phrase) },
                            label = { Text("\"$phrase\"") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Wake Word Sensitivity: ${(settings.wakeWordSensitivity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisTextPrimary
                )
                Slider(
                    value = settings.wakeWordSensitivity,
                    onValueChange = onUpdateWakeSensitivity,
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                )
            }
        }

        // 4. PERSONALITY CONFIGURATION (MANUAL SELECTION ONLY)
        item {
            SettingsCard(
                title = "4. PERSONALITY MATRIX",
                icon = Icons.Default.Psychology,
                badgeLabel = settings.personalityMode.displayName.uppercase(),
                badgeColor = JarvisArcBlue
            ) {
                Text(
                    text = "Manual Mode Selection (JARVIS never switches personality without your permission):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                PersonalityMode.entries.forEach { mode ->
                    val isSelected = settings.personalityMode == mode
                    Surface(
                        color = if (isSelected) JarvisCyan.copy(alpha = 0.16f) else JarvisSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isSelected) JarvisCyan else JarvisBorderGlow),
                        onClick = { onUpdatePersonalityMode(mode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("personality_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) JarvisCyan else JarvisTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextSecondary
                                )
                            }
                            if (isSelected) {
                                HudStatusBadge(label = "ACTIVE", color = JarvisCyan)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Response Style:",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisCyan
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisSettings.RESPONSE_STYLES.forEach { style ->
                        FilterChip(
                            selected = settings.responseStyle == style,
                            onClick = { onUpdateResponseStyle(style) },
                            label = { Text(style) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Formality Level: ${(settings.formalityLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisTextPrimary
                )
                Slider(
                    value = settings.formalityLevel,
                    onValueChange = onUpdateFormality,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                )

                Text(
                    text = "Personality Expressiveness: ${(settings.personalityLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisTextPrimary
                )
                Slider(
                    value = settings.personalityLevel,
                    onValueChange = onUpdatePersonalityLevel,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                )
            }
        }

        // 5. MEMORY SYSTEM CONTROLS
        item {
            SettingsCard(
                title = "5. LONG-TERM MEMORY",
                icon = Icons.Default.Memory,
                badgeLabel = if (settings.memoryEnabled) "ENABLED" else "OFF",
                badgeColor = if (settings.memoryEnabled) JarvisEmerald else JarvisTextMuted
            ) {
                SettingsToggleRow(
                    title = "Long-Term Memory System",
                    subtitle = "Remember user preferences, facts, custom commands & conversation summaries",
                    checked = settings.memoryEnabled,
                    onCheckedChange = onUpdateMemoryEnabled
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToMemoryVault,
                        border = BorderStroke(1.dp, JarvisCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("View Memory Vault", color = JarvisCyan)
                    }
                    OutlinedButton(
                        onClick = onClearAllMemories,
                        border = BorderStroke(1.dp, JarvisCrimson),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear All Memory", color = JarvisCrimson)
                    }
                }
            }
        }

        // 6. AUTOMATION & ACCESSIBILITY SETTINGS
        item {
            SettingsCard(
                title = "6. AUTOMATION & SCREEN INTELLIGENCE",
                icon = Icons.Default.SettingsApplications,
                badgeLabel = if (isAccessibilityConnected) "ACCESSIBILITY ON" else "SETUP AVAILABLE",
                badgeColor = if (isAccessibilityConnected) JarvisEmerald else JarvisAmber
            ) {
                SettingsToggleRow(
                    title = "Auto-Attach Screen Context",
                    subtitle = "Automatically include visible screen elements when Accessibility Service is active",
                    checked = settings.screenContextAutoAttach,
                    onCheckedChange = onUpdateScreenAutoAttach
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        border = BorderStroke(1.dp, JarvisCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (isAccessibilityConnected) "Accessibility (Active)" else "Enable Accessibility",
                            color = JarvisCyan
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenNotificationSettings,
                        border = BorderStroke(1.dp, JarvisCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (isNotificationConnected) "Notifications (Active)" else "Notification Access",
                            color = JarvisCyan
                        )
                    }
                }
            }
        }

        // 7. SECURITY & ANDROID PERMISSIONS MANAGEMENT
        item {
            SettingsCard(
                title = "7. SECURITY & PERMISSION MANAGEMENT",
                icon = Icons.Default.Security,
                badgeLabel = "PROTECTED",
                badgeColor = JarvisEmerald
            ) {
                SettingsToggleRow(
                    title = "Require Confirmation for Sensitive Actions",
                    subtitle = "Always ask before sending SMS, placing phone calls, or deleting data",
                    checked = settings.confirmSensitiveActions,
                    onCheckedChange = onUpdateConfirmSensitive
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ANDROID PERMISSIONS (LEAST-PRIVILEGE):",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisCyan
                )
                Spacer(modifier = Modifier.height(6.dp))

                PermissionExplainRow(
                    name = "Microphone (RECORD_AUDIO)",
                    explanation = "Required for Gemini Live real-time voice conversation, Calling Mode, and Wake Word detection.",
                    granted = hasMicPermission,
                    onRequest = { onRequestPermission(android.Manifest.permission.RECORD_AUDIO) }
                )
                PermissionExplainRow(
                    name = "Contacts (READ_CONTACTS)",
                    explanation = "Used only when you ask JARVIS to call or message a contact by name (e.g., 'Send Rahul a message').",
                    granted = hasContactsPermission,
                    onRequest = { onRequestPermission(android.Manifest.permission.READ_CONTACTS) }
                )
                PermissionExplainRow(
                    name = "Phone Calls (CALL_PHONE)",
                    explanation = "Used to place direct phone calls after your confirmation (falls back to safe dialer without permission).",
                    granted = hasCallPermission,
                    onRequest = { onRequestPermission(android.Manifest.permission.CALL_PHONE) }
                )
                PermissionExplainRow(
                    name = "SMS Messaging (SEND_SMS)",
                    explanation = "Used for SMS messaging workflows after confirmation.",
                    granted = hasSmsPermission,
                    onRequest = { onRequestPermission(android.Manifest.permission.SEND_SMS) }
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenAppSystemSettings,
                    border = BorderStroke(1.dp, JarvisTextSecondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Android App Permission Settings", color = JarvisTextPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Security Notice",
                            tint = JarvisAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.security_warning_apk),
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeLabel: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, JarvisBorderGlow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                HudStatusBadge(label = badgeLabel, color = badgeColor)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisObsidian,
                checkedTrackColor = JarvisCyan
            )
        )
    }
}

@Composable
private fun PermissionExplainRow(
    name: String,
    explanation: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Surface(
        color = JarvisObsidian.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (granted) JarvisEmerald else JarvisTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (granted) {
                HudStatusBadge(label = "GRANTED", color = JarvisEmerald)
            } else {
                OutlinedButton(
                    onClick = onRequest,
                    border = BorderStroke(1.dp, JarvisCyan)
                ) {
                    Text("Grant", color = JarvisCyan, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
