package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatMessageEntity
import com.example.data.preferences.JarvisSettings
import com.example.data.security.ApiKeyStatus
import com.example.domain.tools.PendingConfirmationAction
import com.example.service.CallingSessionTelemetry
import com.example.service.JarvisOperationalState
import com.example.service.WakeWordState
import com.example.ui.components.HudStatusBadge
import com.example.ui.components.JarvisCoreVisualizer
import com.example.ui.components.PendingConfirmationBanner
import com.example.ui.components.stateColor
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandDeckScreen(
    operationalState: JarvisOperationalState,
    settings: JarvisSettings,
    keyStatus: ApiKeyStatus,
    liveTelemetry: CallingSessionTelemetry,
    wakeWordState: WakeWordState,
    wakeMicRms: Float,
    isBrainThinking: Boolean,
    currentActivityLabel: String,
    errorBanner: String?,
    hasFailedCommandToRetry: Boolean,
    pendingConfirmation: PendingConfirmationAction?,
    chatMessages: List<ChatMessageEntity>,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onToggleContinuousVoice: () -> Unit,
    onLaunchCallingMode: () -> Unit,
    onStopInterrupt: () -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onRetryFailedCommand: () -> Unit,
    onDismissError: () -> Unit,
    onConfirmPendingAction: () -> Unit,
    onCancelPendingAction: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val effectiveAmplitude = if (liveTelemetry.isCallingModeActive || liveTelemetry.isSpeakerActive) {
        liveTelemetry.audioAmplitude
    } else {
        wakeMicRms
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("command_deck_screen")
    ) {
        // 1. TOP FUTURISTIC JARVIS HUD HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.headlineLarge,
                        color = JarvisCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HudStatusBadge(
                        label = operationalState.badgeText,
                        color = stateColor(operationalState)
                    )
                }
                Text(
                    text = "GEMINI CORE • ${settings.personalityMode.displayName.uppercase()} MODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                HudStatusBadge(
                    label = if (keyStatus.isConfigured) "AI READY" else "SETUP KEY",
                    color = if (keyStatus.isConfigured) JarvisEmerald else JarvisAmber,
                    modifier = Modifier.padding(end = 6.dp)
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("header_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        tint = JarvisCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. CURRENT ACTIVITY & CONNECTION TELEMETRY STRIP
        Surface(
            color = JarvisSurfaceCard,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, JarvisBorderGlow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentActivityLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (settings.wakeWordEnabled) "WAKE: '${settings.selectedWakePhrase}'" else "WAKE: OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (settings.wakeWordEnabled) JarvisEmerald else JarvisTextMuted
                )
            }
        }

        if (isBrainThinking) {
            LinearProgressIndicator(
                color = JarvisAmber,
                trackColor = JarvisSurfaceCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(2.dp)
            )
        }

        // 3. PENDING SENSITIVE ACTION CONFIRMATION BANNER
        if (pendingConfirmation != null) {
            Spacer(modifier = Modifier.height(8.dp))
            PendingConfirmationBanner(
                action = pendingConfirmation,
                onConfirm = onConfirmPendingAction,
                onCancel = onCancelPendingAction
            )
        }

        // 4. OFFLINE / ERROR RECOVERY BANNER
        if (!errorBanner.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisCrimson.copy(alpha = 0.16f)),
                border = BorderStroke(1.dp, JarvisCrimson),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("error_recovery_banner")
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Alert", tint = JarvisCrimson, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = errorBanner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (hasFailedCommandToRetry) {
                            OutlinedButton(
                                onClick = onRetryFailedCommand,
                                border = BorderStroke(1.dp, JarvisCyan),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = JarvisCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", color = JarvisCyan, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            border = BorderStroke(1.dp, JarvisAmber),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Settings", color = JarvisAmber, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onDismissError,
                            border = BorderStroke(1.dp, JarvisTextSecondary)
                        ) {
                            Text("Dismiss", color = JarvisTextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. CENTRAL HOLOGRAPHIC VOICE VISUALIZER & CALLING / CONTINUOUS MIC CONTROLS
        Card(
            colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, JarvisBorderGlow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                JarvisCoreVisualizer(
                    operationalState = operationalState,
                    amplitude = effectiveAmplitude,
                    spectrumBands = liveTelemetry.spectrumBands,
                    sizeDp = 112.dp,
                    onCoreClick = {
                        if (!hasMicPermission) {
                            onRequestMicPermission()
                        } else {
                            onToggleContinuousVoice()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = operationalState.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextPrimary,
                        fontWeight = FontWeight.Medium
                    )

                    if (wakeWordState.lastDetectedTranscript.isNotBlank()) {
                        Text(
                            text = "Heard: \"${wakeWordState.lastDetectedTranscript}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Continuous Hands-Free Mic Loop Button
                        Button(
                            onClick = {
                                if (!hasMicPermission) onRequestMicPermission() else onToggleContinuousVoice()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (wakeWordState.isContinuousCommandSession) JarvisEmerald else JarvisCyan,
                                contentColor = JarvisObsidian
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("continuous_voice_loop_btn")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (wakeWordState.isContinuousCommandSession) "Listening" else "Voice Loop",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Dedicated Calling Mode Button
                        OutlinedButton(
                            onClick = {
                                if (!hasMicPermission) onRequestMicPermission() else onLaunchCallingMode()
                            },
                            border = BorderStroke(1.dp, JarvisArcBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("launch_calling_mode_btn")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Live Call",
                                color = JarvisCyan,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Instant Stop / Barge-in Button
                    if (operationalState == JarvisOperationalState.SPEAKING ||
                        operationalState == JarvisOperationalState.LISTENING ||
                        wakeWordState.isContinuousCommandSession
                    ) {
                        OutlinedButton(
                            onClick = onStopInterrupt,
                            border = BorderStroke(1.dp, JarvisCrimson),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .testTag("main_stop_interrupt_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = JarvisCrimson, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("JARVIS, STOP", color = JarvisCrimson, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 6. QUICK ACTIONS BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickCommandChip("Open YouTube & Search AI") {
                onSendMessage("JARVIS, open YouTube and search for Android AI tutorials")
            }
            QuickCommandChip("Create a Note") {
                onSendMessage("JARVIS, create a note titled 'Project Ideas' saying 'Build modular Gemini Live voice tools'")
            }
            QuickCommandChip("Remember Preference") {
                onSendMessage("JARVIS, remember that I prefer concise technical summaries")
            }
            QuickCommandChip("Read Screen") {
                onSendMessage("JARVIS, read the current screen and tell me what buttons are visible")
            }
            QuickCommandChip("Send Message Demo") {
                onSendMessage("JARVIS, send Rahul a message saying I'm coming home")
            }
            QuickCommandChip("Device Status") {
                onSendMessage("JARVIS, check my battery level, audio volume, and system status")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 7. NORMAL TEXT CHAT AREA (INDEPENDENT FROM CALLING MODE)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, JarvisBorderGlow)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONVERSATION LOG (${chatMessages.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisCyan
                    )
                    if (chatMessages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("clear_conversation_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Conversation",
                                tint = JarvisTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_messages_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            onCopy = { textToCopy ->
                                copyToClipboard(context, textToCopy)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 8. TEXT CHAT COMPOSER BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = {
                    Text(
                        text = "Command JARVIS or ask anything...",
                        color = JarvisTextMuted
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            onSendMessage(textInput)
                            textInput = ""
                        }
                    }
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                enabled = textInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisCyan,
                    contentColor = JarvisObsidian
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("send_message_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message"
                )
            }
        }
    }
}

@Composable
private fun QuickCommandChip(
    label: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = JarvisTextPrimary
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = JarvisSurfaceCard
        ),
        border = BorderStroke(1.dp, JarvisBorderGlow)
    )
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessageEntity,
    onCopy: (String) -> Unit
) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    val timeLabel = remember(message.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(message.timestamp))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(
                    color = when {
                        isUser -> JarvisArcBlue.copy(alpha = 0.22f)
                        isSystem -> JarvisCrimson.copy(alpha = 0.16f)
                        else -> JarvisSurfaceElevated
                    },
                    shape = RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp
                    )
                )
                .border(
                    width = 1.dp,
                    color = when {
                        isUser -> JarvisArcBlue.copy(alpha = 0.55f)
                        isSystem -> JarvisCrimson.copy(alpha = 0.6f)
                        else -> JarvisCyan.copy(alpha = 0.35f)
                    },
                    shape = RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp
                    )
                )
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = when {
                            isUser -> if (message.isVoice) "YOU (VOICE)" else "YOU"
                            isSystem -> "SYSTEM ALERT"
                            else -> "J.A.R.V.I.S."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isUser -> JarvisEmerald
                            isSystem -> JarvisCrimson
                            else -> JarvisCyan
                        },
                        fontWeight = FontWeight.Bold
                    )
                    if (!message.toolName.isNullOrBlank()) {
                        HudStatusBadge(
                            label = "TOOL: ${message.toolName.uppercase()}",
                            color = JarvisAmber
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted
                    )
                    if (!isUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onCopy(message.content) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                tint = JarvisTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = JarvisTextPrimary
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("JARVIS Response", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
