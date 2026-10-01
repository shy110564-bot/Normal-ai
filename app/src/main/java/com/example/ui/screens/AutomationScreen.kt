package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.ui.theme.JarvisNeonGradient
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisPink
import com.example.ui.theme.JarvisPurple
import com.example.ui.theme.JarvisSurfaceCard
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationScreen(
    isAccessibilityConnected: Boolean,
    latestSnapshot: ScreenSnapshot?,
    isNotificationListenerConnected: Boolean,
    notifications: List<DeviceNotificationItem>,
    multiStepProgress: List<MultiStepStepStatus>,
    isTorchOn: Boolean,
    onToggleTorch: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCaptureScreenNow: () -> Unit,
    onExecuteScreenAction: (action: String, targetText: String, inputText: String, scrollDirection: String) -> Unit,
    onRunYouTubeWorkflow: (searchQuery: String) -> Unit,
    onSendAutomationPrompt: (prompt: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var youtubeQuery by remember { mutableStateOf("Arijit Singh") }
    var targetElementText by remember { mutableStateOf("") }
    var textToType by remember { mutableStateOf("Hello") }
    var isLiveVisionActive by remember { mutableStateOf(true) }
    var selectedViewerTarget by remember { mutableStateOf("JARVIS AI Vision") }

    // Real-time 2-second screen vision check when Live Vision + Accessibility Service are active
    LaunchedEffect(isLiveVisionActive, isAccessibilityConnected) {
        while (isLiveVisionActive && isAccessibilityConnected) {
            onCaptureScreenNow()
            delay(2000L)
        }
    }

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
                title = "SCREEN SHARE VISION & GOD-MODE CONTROL",
                subtitle = "16:9 Live Vision, 2x2 Control Grid (Click, Scroll, Type, Stop) & Full Phone Control"
            )
        }

        // 1. SCREEN 4: SCREEN SHARE + LIVE VISION (16:9 Preview + 🔴 LIVE Badge + 2x2 Control Grid)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, JarvisNeonGradient, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SCREEN SHARE + VISION",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HudStatusBadge(
                            label = if (isLiveVisionActive) "🔴 LIVE (2s VISION)" else "PAUSED",
                            color = if (isLiveVisionActive) JarvisCrimson else JarvisTextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 16:9 Live Screen Preview Glass Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(JarvisObsidian, RoundedCornerShape(16.dp))
                            .border(1.dp, JarvisCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (latestSnapshot != null) {
                                        "APP: ${latestSnapshot.packageName}"
                                    } else if (isAccessibilityConnected) {
                                        "VISION READY — Tap 'Read Screen'"
                                    } else {
                                        "ENABLE ACCESSIBILITY FOR LIVE OCR VISION"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisCyan
                                )
                                Text(
                                    text = "Viewer: $selectedViewerTarget",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisPink
                                )
                            }

                            if (latestSnapshot != null) {
                                Column {
                                    Text(
                                        text = "Detected: ${latestSnapshot.buttons.size} Buttons • ${latestSnapshot.textFields.size} Text Fields • ${latestSnapshot.lists.size} Lists",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = JarvisEmerald
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = latestSnapshot.visibleTextSummary.ifBlank { "Ji… screen dekh rahi hun 💕" }.take(200),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = JarvisTextPrimary,
                                        maxLines = 3
                                    )
                                }
                            } else {
                                Text(
                                    text = "\"Ji… apna screen share ya Accessibility on karo, main har 2 second mein screen dekh ke click, scroll, aur type kar dungi 💕\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextSecondary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OCR + Button/Field Detection",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextMuted
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("2s Auto-Check", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = isLiveVisionActive,
                                        onCheckedChange = { isLiveVisionActive = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = JarvisObsidian,
                                            checkedTrackColor = JarvisCyan
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Viewers / Share Target Chips
                    Text(
                        text = "Share / Connect Screen With:",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisTextSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("JARVIS AI Vision", "WhatsApp Contact", "Google Meet", "Zoom").forEach { viewer ->
                            FilterChip(
                                selected = selectedViewerTarget == viewer,
                                onClick = { selectedViewerTarget = viewer },
                                label = { Text(viewer) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisPurple.copy(alpha = 0.25f),
                                    selectedLabelColor = JarvisCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = targetElementText,
                        onValueChange = { targetElementText = it },
                        label = { Text("Target Button / Field Text (e.g. 'Search', 'Send')") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = textToType,
                        onValueChange = { textToType = it },
                        label = { Text("Text to Type (e.g. 'main aa raha hun')") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2x2 Control Grid: Click, Scroll, Type, Stop
                    Text(
                        text = "2x2 SCREEN CONTROL GRID:",
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onExecuteScreenAction("CLICK", targetElementText, "", "DOWN") },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisObsidian),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("1. CLICK", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onExecuteScreenAction("SCROLL", "", "", "DOWN") },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisPurple, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("2. SCROLL", fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onExecuteScreenAction("TYPE_TEXT", targetElementText, textToType, "DOWN") },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald, contentColor = JarvisObsidian),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("3. TYPE", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { isLiveVisionActive = false },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCrimson, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("4. STOP", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("BACK", "", "", "DOWN") },
                            border = BorderStroke(1.dp, JarvisTextSecondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("HOME", "", "", "DOWN") },
                            border = BorderStroke(1.dp, JarvisTextSecondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Home")
                        }
                        OutlinedButton(
                            onClick = { onExecuteScreenAction("RECENTS", "", "", "DOWN") },
                            border = BorderStroke(1.dp, JarvisTextSecondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Recents")
                        }
                    }
                }
            }
        }

        // 2. Android Accessibility Service Status Card
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
                    Spacer(modifier = Modifier.height(8.dp))
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
                        OutlinedButton(
                            onClick = onCaptureScreenNow,
                            border = BorderStroke(1.dp, JarvisCyan)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Read Screen", color = JarvisCyan)
                        }
                    }
                }
            }
        }

        // 3. Multi-Step Task Orchestrator
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
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = youtubeQuery,
                        onValueChange = { youtubeQuery = it },
                        label = { Text("Exact Search Topic (e.g. 'Arijit Singh' or 'AK EXPLOITS')") },
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

        // 4. SECTION 6: FULL PHONE CONTROL ("GOD MODE") QUICK HUB
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
                            text = "FULL PHONE CONTROL (GOD MODE)",
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
                        AutomationChipButton(
                            label = if (isTorchOn) "Torch ON (Tap Off)" else "Torch / Flash ON",
                            icon = Icons.Default.FlashlightOn
                        ) {
                            onToggleTorch(!isTorchOn)
                        }
                        AutomationChipButton("Selfie / Camera", Icons.Default.CameraAlt) {
                            onSendAutomationPrompt("Camera open karo")
                        }
                        AutomationChipButton("WhatsApp") {
                            onSendAutomationPrompt("WhatsApp pe Mummy ko bolo main aa raha hun")
                        }
                        AutomationChipButton("YouTube Music") {
                            onSendAutomationPrompt("YouTube par Arijit Singh search karo")
                        }
                        AutomationChipButton("Spotify") {
                            onSendAutomationPrompt("Open Spotify")
                        }
                        AutomationChipButton("Google Maps") {
                            onSendAutomationPrompt("Open Google Maps")
                        }
                        AutomationChipButton("Amazon") {
                            onSendAutomationPrompt("Open Amazon")
                        }
                        AutomationChipButton("Flipkart") {
                            onSendAutomationPrompt("Open Flipkart")
                        }
                        AutomationChipButton("Wi-Fi Settings") {
                            onSendAutomationPrompt("Open Wi-Fi Settings")
                        }
                        AutomationChipButton("Bluetooth") {
                            onSendAutomationPrompt("Open Bluetooth Settings")
                        }
                        AutomationChipButton("Hotspot") {
                            onSendAutomationPrompt("Open Hotspot Settings")
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
                        AutomationChipButton("Battery & Status") {
                            onSendAutomationPrompt("Meri battery aur phone status batao")
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
        shape = RoundedCornerShape(12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(label, color = JarvisTextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}
