package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.tools.MultiStepStepStatus
import com.example.service.DeviceNotificationItem
import com.example.service.ScreenSnapshot
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationScreen(
    isAccessibilityConnected: Boolean,
    latestSnapshot: ScreenSnapshot?,
    isNotificationListenerConnected: Boolean,
    notifications: List<DeviceNotificationItem>,
    multiStepProgress: List<MultiStepStepStatus>,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCaptureScreenNow: () -> Unit,
    onExecuteScreenAction: (action: String, targetText: String, inputText: String, scrollDirection: String) -> Unit,
    onRunYouTubeWorkflow: (searchQuery: String) -> Unit,
    onSendAutomationPrompt: (prompt: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var youtubeQuery by remember { mutableStateOf("Android AI tutorials") }
    var targetElementText by remember { mutableStateOf("") }
    var textToType by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian)
            .padding(16.dp)
            .testTag("automation_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HudSectionHeader(
                title = "SCREEN INTELLIGENCE & PHONE AUTOMATION",
                subtitle = "Accessibility screen reading, UI interaction, multi-step workflows & device control"
            )
        }

        // 1. Android Accessibility & Notification Service Status Cards
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (isAccessibilityConnected) JarvisEmerald else JarvisAmber
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessibilityNew,
                                contentDescription = "Accessibility Service",
                                tint = if (isAccessibilityConnected) JarvisEmerald else JarvisAmber
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ANDROID ACCESSIBILITY SERVICE",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HudStatusBadge(
                            label = if (isAccessibilityConnected) "ACTIVE" else "PERMISSION NEEDED",
                            color = if (isAccessibilityConnected) JarvisEmerald else JarvisAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAccessibilityConnected) {
                            "JARVIS can read visible screen text, identify buttons/inputs/lists, and perform tap, type, scroll, and navigation actions."
                        } else {
                            "Enable 'JARVIS Screen Intelligence & Control' in Android Accessibility Settings to unlock real-time screen reading and UI interaction."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyan,
                                contentColor = JarvisObsidian
                            ),
                            modifier = Modifier.testTag("open_accessibility_settings_btn")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configure Accessibility", fontWeight = FontWeight.Bold)
                        }
                        if (isAccessibilityConnected) {
                            OutlinedButton(
                                onClick = onCaptureScreenNow,
                                border = BorderStroke(1.dp, JarvisCyan)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Read Screen Now", color = JarvisCyan)
                            }
                        }
                    }
                }
            }
        }

        // 2. Multi-Step Task Runner (e.g., Open YouTube -> Search -> Inspect)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisBorderGlow)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "MULTI-STEP TASK ORCHESTRATOR",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Break complex voice/text requests into sequential verified Android actions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = youtubeQuery,
                        onValueChange = { youtubeQuery = it },
                        label = { Text("YouTube Search Topic") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("youtube_workflow_query_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onRunYouTubeWorkflow(youtubeQuery) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisArcBlue,
                            contentColor = JarvisTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_youtube_workflow_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Execute: Open YouTube → Search '$youtubeQuery'",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (multiStepProgress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        multiStepProgress.forEach { step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(JarvisObsidian.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${step.stepIndex}. ${step.description}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                HudStatusBadge(
                                    label = step.status,
                                    color = when (step.status) {
                                        "VERIFIED" -> JarvisEmerald
                                        "RUNNING" -> JarvisAmber
                                        "FAILED" -> JarvisCrimson
                                        else -> JarvisTextMuted
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Screen Reading Inspector & Direct Screen Control
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisBorderGlow)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SCREEN CONTROL & UI INTERACTION",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (latestSnapshot != null) {
                        Surface(
                            color = JarvisSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "LAST CAPTURED SCREEN (${latestSnapshot.packageName})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = JarvisEmerald
                                )
                                Text(
                                    text = "Buttons: ${latestSnapshot.buttons.size} | Inputs: ${latestSnapshot.textFields.size} | Lists: ${latestSnapshot.lists.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisTextSecondary
                                )
                                if (latestSnapshot.visibleTextSummary.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = latestSnapshot.visibleTextSummary.take(260),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = JarvisTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetElementText,
                        onValueChange = { targetElementText = it },
                        label = { Text("Target Button / Field Label (e.g. 'Search')") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = textToType,
                        onValueChange = { textToType = it },
                        label = { Text("Text to Type into Focused/Target Field") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("CLICK", targetElementText, "", "DOWN") },
                            border = BorderStroke(1.dp, JarvisCyan)
                        ) {
                            Text("Tap Element", color = JarvisCyan)
                        }
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("TYPE_TEXT", targetElementText, textToType, "DOWN") },
                            border = BorderStroke(1.dp, JarvisCyan)
                        ) {
                            Text("Focus & Type", color = JarvisCyan)
                        }
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("SCROLL", "", "", "DOWN") },
                            border = BorderStroke(1.dp, JarvisCyan)
                        ) {
                            Text("Scroll Down", color = JarvisCyan)
                        }
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("SCROLL", "", "", "UP") },
                            border = BorderStroke(1.dp, JarvisCyan)
                        ) {
                            Text("Scroll Up", color = JarvisCyan)
                        }
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("BACK", "", "", "DOWN") },
                            border = BorderStroke(1.dp, JarvisTextSecondary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("HOME", "", "", "DOWN") },
                            border = BorderStroke(1.dp, JarvisTextSecondary)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Home")
                        }
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("RECENTS", "", "", "DOWN") },
                            border = BorderStroke(1.dp, JarvisTextSecondary)
                        ) {
                            Text("Recent Apps")
                        }
                    }
                }
            }
        }

        // 4. Quick Phone Automation & Device Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisBorderGlow)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QUICK PHONE AUTOMATION & DEVICE CONTROLS",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutomationChipButton("Open YouTube") {
                            onSendAutomationPrompt("Open YouTube")
                        }
                        AutomationChipButton("Open Maps") {
                            onSendAutomationPrompt("Open Google Maps")
                        }
                        AutomationChipButton("Open Chrome") {
                            onSendAutomationPrompt("Open Chrome browser")
                        }
                        AutomationChipButton("Wi-Fi Settings") {
                            onSendAutomationPrompt("Open Wi-Fi Settings")
                        }
                        AutomationChipButton("Bluetooth Settings") {
                            onSendAutomationPrompt("Open Bluetooth Settings")
                        }
                        AutomationChipButton("Volume Up", Icons.AutoMirrored.Filled.VolumeUp) {
                            onSendAutomationPrompt("Turn media volume up")
                        }
                        AutomationChipButton("Volume Down", Icons.AutoMirrored.Filled.VolumeDown) {
                            onSendAutomationPrompt("Turn media volume down")
                        }
                        AutomationChipButton("Mute Audio", Icons.AutoMirrored.Filled.VolumeOff) {
                            onSendAutomationPrompt("Mute media audio")
                        }
                        AutomationChipButton("Device Status") {
                            onSendAutomationPrompt("Report current device telemetry and battery status")
                        }
                    }
                }
            }
        }

        // 5. Notification Reader Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisBorderGlow)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NOTIFICATION INTELLIGENCE",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HudStatusBadge(
                            label = if (isNotificationListenerConnected) "CONNECTED (${notifications.size})" else "NOT ENABLED",
                            color = if (isNotificationListenerConnected) JarvisEmerald else JarvisTextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Allows JARVIS to read incoming notifications when you ask 'JARVIS, read my notifications'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenNotificationSettings,
                        border = BorderStroke(1.dp, JarvisCyan)
                    ) {
                        Text("Configure Notification Access", color = JarvisCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationChipButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, JarvisBorderGlow),
        shape = RoundedCornerShape(10.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(label, color = JarvisTextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}
