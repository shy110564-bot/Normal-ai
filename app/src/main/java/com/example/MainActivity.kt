package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.JarvisScreenTab
import com.example.ui.JarvisViewModel
import com.example.ui.screens.AutomationScreen
import com.example.ui.screens.CallingModeScreen
import com.example.ui.screens.CommandDeckScreen
import com.example.ui.screens.NotesAndMemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                JarvisAppRoot()
            }
        }
    }
}

@Composable
fun JarvisAppRoot(
    viewModel: JarvisViewModel = viewModel()
) {
    val context = LocalContext.current
    var permissionRefreshTrigger by remember { mutableIntStateOf(0) }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionRefreshTrigger++
    }

    val hasMicPermission = remember(permissionRefreshTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val hasContactsPermission = remember(permissionRefreshTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val hasCallPermission = remember(permissionRefreshTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    }
    val hasSmsPermission = remember(permissionRefreshTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keyStatus by viewModel.keyStatus.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val wakeWordState by viewModel.wakeWordState.collectAsStateWithLifecycle()
    val wakeMicRms by viewModel.wakeMicRms.collectAsStateWithLifecycle()
    val isBrainThinking by viewModel.isBrainThinking.collectAsStateWithLifecycle()
    val currentActivityLabel by viewModel.currentActivityLabel.collectAsStateWithLifecycle()
    val lastErrorBanner by viewModel.lastErrorBanner.collectAsStateWithLifecycle()
    val lastFailedCommand by viewModel.lastFailedCommand.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val toolLogs by viewModel.toolLogs.collectAsStateWithLifecycle()
    val multiStepProgress by viewModel.multiStepProgress.collectAsStateWithLifecycle()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsStateWithLifecycle()
    val latestScreenSnapshot by viewModel.latestScreenSnapshot.collectAsStateWithLifecycle()
    val isNotificationConnected by viewModel.isNotificationListenerConnected.collectAsStateWithLifecycle()
    val activeNotifications by viewModel.activeNotifications.collectAsStateWithLifecycle()
    val apiKeyTestState by viewModel.apiKeyTestState.collectAsStateWithLifecycle()
    val isTestingApiKey by viewModel.isTestingApiKey.collectAsStateWithLifecycle()

    val effectiveOperationalState = viewModel.computeEffectiveOperationalState()

    // Ensure BackHandler returns from any secondary tab to the main Command Deck
    if (currentTab != JarvisScreenTab.COMMAND_DECK) {
        BackHandler {
            viewModel.navigateTo(JarvisScreenTab.COMMAND_DECK)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisObsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = JarvisDeepNavy,
                modifier = Modifier.testTag("jarvis_bottom_nav")
            ) {
                JarvisScreenTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.navigateTo(tab) },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    JarvisScreenTab.COMMAND_DECK -> Icons.Default.GraphicEq
                                    JarvisScreenTab.CALLING_MODE -> Icons.Default.Call
                                    JarvisScreenTab.NOTES_MEMORY -> Icons.Default.Memory
                                    JarvisScreenTab.AUTOMATION -> Icons.Default.Build
                                    JarvisScreenTab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JarvisCyan,
                            selectedTextColor = JarvisCyan,
                            indicatorColor = JarvisSurfaceElevated,
                            unselectedIconColor = JarvisTextMuted,
                            unselectedTextColor = JarvisTextMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (currentTab) {
            JarvisScreenTab.COMMAND_DECK -> {
                CommandDeckScreen(
                    operationalState = effectiveOperationalState,
                    settings = settings,
                    keyStatus = keyStatus,
                    liveTelemetry = liveTelemetry,
                    wakeWordState = wakeWordState,
                    wakeMicRms = wakeMicRms,
                    isBrainThinking = isBrainThinking,
                    currentActivityLabel = currentActivityLabel,
                    errorBanner = lastErrorBanner,
                    hasFailedCommandToRetry = lastFailedCommand != null,
                    pendingConfirmation = pendingConfirmation,
                    chatMessages = chatMessages,
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = {
                        singlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onToggleContinuousVoice = { viewModel.toggleContinuousVoiceLoopOnMainScreen() },
                    onLaunchCallingMode = { viewModel.startCallingMode() },
                    onStopInterrupt = { viewModel.stopAndInterruptAll() },
                    onSendMessage = { viewModel.sendUserMessage(it) },
                    onClearChat = { viewModel.clearChatHistory() },
                    onRetryFailedCommand = { viewModel.retryLastFailedCommand() },
                    onDismissError = { viewModel.clearErrorBanner() },
                    onConfirmPendingAction = { viewModel.confirmPendingAction() },
                    onCancelPendingAction = { viewModel.cancelPendingAction() },
                    onOpenSettings = { viewModel.navigateTo(JarvisScreenTab.SETTINGS) },
                    modifier = contentModifier
                )
            }

            JarvisScreenTab.CALLING_MODE -> {
                CallingModeScreen(
                    telemetry = liveTelemetry,
                    recentMessages = chatMessages,
                    toolLogs = toolLogs,
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = {
                        singlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStartCallingMode = { viewModel.startCallingMode() },
                    onStopInterrupt = { viewModel.stopAndInterruptAll("Interrupted by user") },
                    onToggleMute = { viewModel.toggleCallingMute() },
                    onEndCallingMode = { viewModel.endCallingMode() },
                    modifier = contentModifier
                )
            }

            JarvisScreenTab.NOTES_MEMORY -> {
                NotesAndMemoryScreen(
                    notes = notes,
                    memories = memories,
                    memoryEnabled = settings.memoryEnabled,
                    onToggleMemoryEnabled = { viewModel.updateMemoryEnabled(it) },
                    onCreateNote = { title, content, cat -> viewModel.createNote(title, content, cat) },
                    onUpdateNote = { id, title, content, cat -> viewModel.updateNote(id, title, content, cat) },
                    onDeleteNote = { viewModel.deleteNote(it) },
                    onSaveMemory = { cat, key, value, imp -> viewModel.saveMemoryItem(cat, key, value, imp) },
                    onForgetMemory = { viewModel.deleteMemoryItem(it) },
                    onClearAllMemories = { viewModel.clearAllMemories() },
                    modifier = contentModifier
                )
            }

            JarvisScreenTab.AUTOMATION -> {
                AutomationScreen(
                    isAccessibilityConnected = isAccessibilityConnected,
                    latestSnapshot = latestScreenSnapshot,
                    isNotificationListenerConnected = isNotificationConnected,
                    notifications = activeNotifications,
                    multiStepProgress = multiStepProgress,
                    onOpenAccessibilitySettings = {
                        viewModel.toolEngine.openSystemSettingsScreen("ACCESSIBILITY")
                    },
                    onOpenNotificationSettings = {
                        viewModel.toolEngine.openSystemSettingsScreen("NOTIFICATIONS")
                    },
                    onCaptureScreenNow = { viewModel.captureCurrentScreenNow() },
                    onExecuteScreenAction = { action, target, input, scroll ->
                        viewModel.executeDirectScreenAction(action, target, input, scroll)
                    },
                    onRunYouTubeWorkflow = { query ->
                        viewModel.runYouTubeMultiStepWorkflow(query)
                    },
                    onSendAutomationPrompt = { prompt ->
                        viewModel.navigateTo(JarvisScreenTab.COMMAND_DECK)
                        viewModel.sendUserMessage(prompt)
                    },
                    modifier = contentModifier
                )
            }

            JarvisScreenTab.SETTINGS -> {
                SettingsScreen(
                    settings = settings,
                    keyStatus = keyStatus,
                    apiKeyTestResult = apiKeyTestState,
                    isTestingApiKey = isTestingApiKey,
                    isAccessibilityConnected = isAccessibilityConnected,
                    isNotificationConnected = isNotificationConnected,
                    hasMicPermission = hasMicPermission,
                    hasContactsPermission = hasContactsPermission,
                    hasCallPermission = hasCallPermission,
                    hasSmsPermission = hasSmsPermission,
                    onSaveApiKey = { viewModel.saveApiKey(it) },
                    onTestApiKey = { viewModel.testApiKey(it) },
                    onDeleteApiKey = { viewModel.deleteApiKey() },
                    onUpdateLiveModel = { viewModel.updateLiveModel(it) },
                    onUpdateTextModel = { viewModel.updateTextModel(it) },
                    onUpdateVoiceEnabled = { viewModel.updateVoiceEnabled(it) },
                    onUpdateSelectedVoice = { viewModel.updateSelectedVoice(it) },
                    onUpdateSpeakingSpeed = { viewModel.updateSpeakingSpeed(it) },
                    onUpdateBargeIn = { viewModel.updateBargeIn(it) },
                    onUpdateEchoControl = { viewModel.updateEchoControl(it) },
                    onUpdateWakeWordEnabled = { viewModel.updateWakeWordEnabled(it) },
                    onUpdateWakePhrase = { viewModel.updateWakePhrase(it) },
                    onUpdateWakeSensitivity = { viewModel.updateWakeSensitivity(it) },
                    onUpdatePersonalityMode = { viewModel.updatePersonalityMode(it) },
                    onUpdateResponseStyle = { viewModel.updateResponseStyle(it) },
                    onUpdateFormality = { viewModel.updateFormality(it) },
                    onUpdatePersonalityLevel = { viewModel.updatePersonalityLevel(it) },
                    onUpdateMemoryEnabled = { viewModel.updateMemoryEnabled(it) },
                    onNavigateToMemoryVault = { viewModel.navigateTo(JarvisScreenTab.NOTES_MEMORY) },
                    onClearAllMemories = { viewModel.clearAllMemories() },
                    onUpdateConfirmSensitive = { viewModel.updateConfirmSensitive(it) },
                    onUpdateScreenAutoAttach = { viewModel.updateScreenAutoAttach(it) },
                    onOpenAccessibilitySettings = {
                        viewModel.toolEngine.openSystemSettingsScreen("ACCESSIBILITY")
                    },
                    onOpenNotificationSettings = {
                        viewModel.toolEngine.openSystemSettingsScreen("NOTIFICATIONS")
                    },
                    onOpenAppSystemSettings = {
                        viewModel.toolEngine.openSystemSettingsScreen("APP_DETAILS")
                    },
                    onRequestPermission = { perm ->
                        singlePermissionLauncher.launch(perm)
                    },
                    onLaunchCallingMode = { viewModel.startCallingMode() },
                    modifier = contentModifier
                )
            }
        }
    }
}
