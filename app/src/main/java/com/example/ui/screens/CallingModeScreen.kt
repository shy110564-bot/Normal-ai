package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatMessageEntity
import com.example.domain.tools.ToolExecutionLog
import com.example.service.CallingSessionTelemetry
import com.example.service.GeminiLiveConnectionState
import com.example.service.JarvisOperationalState
import com.example.ui.components.HudStatusBadge
import com.example.ui.components.JarvisCoreVisualizer
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

@Composable
fun CallingModeScreen(
    telemetry: CallingSessionTelemetry,
    recentMessages: List<ChatMessageEntity>,
    toolLogs: List<ToolExecutionLog>,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onStartCallingMode: () -> Unit,
    onStopInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    onEndCallingMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val opState = telemetry.operationalState
    val isListening = opState == JarvisOperationalState.LISTENING
    val isThinking = opState == JarvisOperationalState.THINKING || opState == JarvisOperationalState.CONNECTING
    val isSpeaking = opState == JarvisOperationalState.SPEAKING

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("calling_mode_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Telemetry Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JARVIS CALLING MODE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = JarvisCyan
                )
                Text(
                    text = "LISTEN → THINK → SPEAK → LISTEN (CONTINUOUS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )
            }
            HudStatusBadge(
                label = telemetry.liveConnectionState.label,
                color = when (telemetry.liveConnectionState) {
                    GeminiLiveConnectionState.GEMINI_CONNECTED -> JarvisEmerald
                    GeminiLiveConnectionState.CONNECTING,
                    GeminiLiveConnectionState.RECONNECTING -> JarvisAmber
                    GeminiLiveConnectionState.ERROR -> JarvisCrimson
                    GeminiLiveConnectionState.GEMINI_DISCONNECTED -> JarvisTextSecondary
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real-Time Status Matrix (Mic, Speaker, Connection, Live Model)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CallingTelemetryPill(
                label = if (telemetry.isMicMuted) "MIC MUTED" else if (telemetry.isCallingModeActive) "MIC ACTIVE" else "MIC STANDBY",
                icon = if (telemetry.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                color = if (telemetry.isMicMuted) JarvisAmber else if (telemetry.isCallingModeActive) JarvisEmerald else JarvisTextSecondary,
                modifier = Modifier.weight(1f)
            )
            CallingTelemetryPill(
                label = if (telemetry.isSpeakerActive) "SPEAKER ON" else "SPEAKER IDLE",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                color = if (telemetry.isSpeakerActive) JarvisArcBlue else JarvisTextSecondary,
                modifier = Modifier.weight(1f)
            )
            CallingTelemetryPill(
                label = telemetry.activeLiveModel.take(18),
                icon = Icons.Default.Wifi,
                color = JarvisCyan,
                modifier = Modifier.weight(1.2f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Three-Stage Continuous Loop Indicator: LISTENING -> PROCESSING -> SPEAKING
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisSurfaceCard, RoundedCornerShape(14.dp))
                .border(1.dp, JarvisBorderGlow, RoundedCornerShape(14.dp))
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StageIndicatorChip(
                title = "1. HEARING",
                active = isListening && !telemetry.isMicMuted,
                activeColor = JarvisCyan,
                icon = Icons.Default.Hearing
            )
            Text("→", color = JarvisTextMuted, style = MaterialTheme.typography.titleMedium)
            StageIndicatorChip(
                title = "2. THINKING",
                active = isThinking,
                activeColor = JarvisAmber,
                icon = Icons.Default.Psychology
            )
            Text("→", color = JarvisTextMuted, style = MaterialTheme.typography.titleMedium)
            StageIndicatorChip(
                title = "3. SPEAKING",
                active = isSpeaking,
                activeColor = JarvisArcBlue,
                icon = Icons.Default.RecordVoiceOver
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Central Holographic Core
        JarvisCoreVisualizer(
            operationalState = opState,
            amplitude = telemetry.audioAmplitude,
            spectrumBands = telemetry.spectrumBands,
            sizeDp = 180.dp,
            onCoreClick = {
                if (!hasMicPermission) {
                    onRequestMicPermission()
                } else if (!telemetry.isCallingModeActive) {
                    onStartCallingMode()
                } else if (isSpeaking) {
                    onStopInterrupt()
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = telemetry.statusDetail,
            style = MaterialTheme.typography.bodyMedium,
            color = stateColor(opState),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        if (!telemetry.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisCrimson.copy(alpha = 0.16f)),
                border = BorderStroke(1.dp, JarvisCrimson)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = telemetry.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onStartCallingMode) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconnect", tint = JarvisCyan)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Conversation Activity Feed
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
                    .padding(12.dp)
            ) {
                Text(
                    text = "LIVE CONVERSATION & TOOL ACTIVITY",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisCyan
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (telemetry.jarvisLiveResponse.isNotBlank()) {
                    Surface(
                        color = JarvisSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "JARVIS (LIVE): ${telemetry.jarvisLiveResponse}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisCyan,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(toolLogs.take(4), key = { "tool_${it.id}" }) { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JarvisObsidian.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HudStatusBadge(
                                label = log.toolName.uppercase(),
                                color = if (log.succeeded) JarvisEmerald else JarvisAmber
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = log.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary
                            )
                        }
                    }

                    items(recentMessages.takeLast(8).reversed(), key = { "msg_${it.id}" }) { msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isUser) JarvisSurfaceElevated.copy(alpha = 0.6f) else JarvisObsidian.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (isUser) "YOU: " else "JARVIS: ",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUser) JarvisEmerald else JarvisCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dedicated Calling Mode Action Bar: Mute, Stop/Interrupt, Start/End Calling Mode
        if (!telemetry.isCallingModeActive) {
            Button(
                onClick = {
                    if (!hasMicPermission) onRequestMicPermission() else onStartCallingMode()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisCyan,
                    contentColor = JarvisObsidian
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("start_calling_mode_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start Calling Mode")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACTIVATE GEMINI LIVE CALLING MODE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute / Unmute Button
                OutlinedButton(
                    onClick = onToggleMute,
                    border = BorderStroke(1.5.dp, if (telemetry.isMicMuted) JarvisAmber else JarvisCyan),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("calling_mute_btn")
                ) {
                    Icon(
                        imageVector = if (telemetry.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Microphone Mute",
                        tint = if (telemetry.isMicMuted) JarvisAmber else JarvisCyan
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (telemetry.isMicMuted) "UNMUTE" else "MUTE",
                        color = if (telemetry.isMicMuted) JarvisAmber else JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Stop / Barge-in Interrupt Button
                Button(
                    onClick = onStopInterrupt,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisAmber,
                        contentColor = JarvisObsidian
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("calling_stop_interrupt_btn")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Interrupt JARVIS")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("STOP", fontWeight = FontWeight.Bold)
                }

                // End Calling Mode Button
                Button(
                    onClick = onEndCallingMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCrimson,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(52.dp)
                        .testTag("end_calling_mode_btn")
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Calling Mode")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("END CALL", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CallingTelemetryPill(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = JarvisSurfaceCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StageIndicatorChip(
    title: String,
    active: Boolean,
    activeColor: Color,
    icon: ImageVector
) {
    val tint = if (active) activeColor else JarvisTextMuted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                if (active) activeColor.copy(alpha = 0.16f) else Color.Transparent,
                RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}
