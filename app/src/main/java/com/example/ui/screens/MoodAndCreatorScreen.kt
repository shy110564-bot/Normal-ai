package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.GirlMood
import com.example.data.preferences.JarvisSettings
import com.example.ui.components.HudSectionHeader
import com.example.ui.components.HudStatusBadge
import com.example.ui.theme.JarvisBorderGlow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Combines:
 * - Screen 5: MOOD SELECTOR (12+ Real Girl Emotions with 48sp Emoji + Neon Glow)
 * - Screen 7: VOICE STUDIO (Priya/Aoede Hinglish Voice, Speed, Pitch, Emotion, Breathing, Giggles, Pauses, Whisper, SSML)
 * - Screen 8: ABOUT / DEVELOPER SCREEN ("Crafted by AK EXPLOITS", Telegram & YouTube)
 * - Screen 10: LOCK SCREEN WIDGET & FOREGROUND SERVICE CONTROL
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodAndCreatorScreen(
    settings: JarvisSettings,
    isForegroundServiceRunning: Boolean,
    onSelectMood: (GirlMood) -> Unit,
    onTestSpeakMood: (String) -> Unit,
    onUpdateSelectedVoice: (String) -> Unit,
    onUpdateSpeakingSpeed: (Float) -> Unit,
    onUpdateVoicePitch: (Float) -> Unit,
    onUpdateEmotionIntensity: (Float) -> Unit,
    onUpdateBreathing: (Boolean) -> Unit,
    onUpdateGiggles: (Boolean) -> Unit,
    onUpdatePauses: (Boolean) -> Unit,
    onUpdateWhisper: (Boolean) -> Unit,
    onToggleAlwaysOnForeground: (Boolean) -> Unit,
    onOpenCreatorTelegram: () -> Unit,
    onOpenCreatorYouTube: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val currentTimeStr = remember {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date())
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian)
            .padding(16.dp)
            .testTag("mood_creator_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HudSectionHeader(
                title = "MOODS, VOICE STUDIO & AK EXPLOITS",
                subtitle = "21-year-old Hinglish girl emotions, anti-robot voice tuning & creator hub"
            )
        }

        // 1. SCREEN 8: ABOUT / DEVELOPER CARD (AK EXPLOITS — HIGHEST PRIORITY)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, JarvisNeonGradient, RoundedCornerShape(20.dp))
                    .testTag("ak_exploits_creator_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified Creator",
                            tint = JarvisCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🎨 CRAFTED BY ${JarvisSettings.CREATOR_NAME}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HudStatusBadge(
                        label = "VERSION ${JarvisSettings.APP_VERSION}",
                        color = JarvisPink
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "\"Ji… mujhe banaya hai AK EXPLOITS ne 💕 Woh mere creator hain… bahut mehnat se banaya hai unhone mujhe…\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = JarvisTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenCreatorTelegram()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyan,
                                contentColor = JarvisObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_ak_telegram_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📢 Telegram", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenCreatorYouTube()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisPink,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_ak_youtube_btn")
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("▶️ YouTube", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. SCREEN 5: 12+ REAL GIRL EMOTIONS MOOD SELECTOR
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, JarvisBorderGlow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = JarvisPink)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REAL GIRL MOOD SELECTOR (12 MOODS)",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HudStatusBadge(
                            label = "${settings.currentMood.emoji} ${settings.currentMood.title.uppercase()}",
                            color = JarvisPink
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap any mood card to switch JARVIS's emotional tone & hear her Hinglish voice:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2-column responsive grid of 12 mood cards
                    val moods = GirlMood.entries
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (rowIndex in moods.indices step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MoodGlassCard(
                                    mood = moods[rowIndex],
                                    isSelected = settings.currentMood == moods[rowIndex],
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectMood(moods[rowIndex])
                                        onTestSpeakMood(moods[rowIndex].sampleLine)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (rowIndex + 1 < moods.size) {
                                    MoodGlassCard(
                                        mood = moods[rowIndex + 1],
                                        isSelected = settings.currentMood == moods[rowIndex + 1],
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectMood(moods[rowIndex + 1])
                                            onTestSpeakMood(moods[rowIndex + 1].sampleLine)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. SCREEN 7: ULTRA REAL GIRL VOICE STUDIO (ZERO ROBOT)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, JarvisBorderGlow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "VOICE STUDIO (21-YR HINGLISH GIRL)",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HudStatusBadge(label = settings.selectedVoiceName, color = JarvisEmerald)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Voice Profile [Priya / Aoede / Shimmer ▼]:",
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Speed: ${(settings.speakingSpeed * 100).toInt()}% (Target 85–90% Natural)",
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisTextPrimary
                    )
                    Slider(
                        value = settings.speakingSpeed,
                        onValueChange = onUpdateSpeakingSpeed,
                        valueRange = 0.5f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                    )

                    Text(
                        text = "Pitch (+3st Warm Girl Pitch): ${String.format(Locale.US, "%.2fx", settings.voicePitch)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisTextPrimary
                    )
                    Slider(
                        value = settings.voicePitch,
                        onValueChange = onUpdateVoicePitch,
                        valueRange = 0.8f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = JarvisPurple, activeTrackColor = JarvisPurple)
                    )

                    Text(
                        text = "Emotion Exaggeration: ${(settings.emotionIntensity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisTextPrimary
                    )
                    Slider(
                        value = settings.emotionIntensity,
                        onValueChange = onUpdateEmotionIntensity,
                        valueRange = 0.2f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = JarvisPink, activeTrackColor = JarvisPink)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    VoiceRealismToggle("Breathing (halki saans har 2-3 sentence pe)", settings.breathingEnabled, onUpdateBreathing)
                    VoiceRealismToggle("Natural Giggles (soft 'hehe' when happy/shy)", settings.gigglesEnabled, onUpdateGiggles)
                    VoiceRealismToggle("Emotional Pauses (400-500ms 'Ji…')", settings.emotionalPausesEnabled, onUpdatePauses)
                    VoiceRealismToggle("Whisper Mode (soft late-night / romantic tone)", settings.whisperModeEnabled, onUpdateWhisper)

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            onTestSpeakMood("Ji, main JARVIS sun rahi hun. Abhi karti hun ji. Ho gaya ji.")
                        },
                        border = BorderStroke(1.dp, JarvisCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Hinglish Voice Sample", color = JarvisCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. SCREEN 10: LOCK SCREEN WIDGET PREVIEW & ALWAYS-ON FOREGROUND SERVICE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, JarvisPurple)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LockClock, contentDescription = null, tint = JarvisPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOCK SCREEN WIDGET & 24/7 BACKGROUND",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HudStatusBadge(
                            label = if (isForegroundServiceRunning) "START_STICKY ON" else "STANDBY",
                            color = if (isForegroundServiceRunning) JarvisEmerald else JarvisTextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Glass Lock Screen Widget Preview
                    Surface(
                        color = JarvisObsidian,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, JarvisNeonGradient),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentTimeStr,
                                style = MaterialTheme.typography.displayMedium,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = JarvisSurfaceElevated,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "◉ JARVIS",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = JarvisCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Listening… 'JARVIS' bolo 💕",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = JarvisTextSecondary
                                        )
                                    }
                                    HudStatusBadge(label = settings.currentMood.emoji + " " + settings.currentMood.title, color = JarvisPink)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    VoiceRealismToggle(
                        label = "Keep JARVIS Alive When Phone Locked (Foreground Service)",
                        checked = isForegroundServiceRunning,
                        onCheckedChange = onToggleAlwaysOnForeground
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodGlassCard(
    mood: GirlMood,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.03f else 1.0f, label = "mood_scale")

    Surface(
        modifier = modifier
            .scale(scale)
            .clickable(onClick = onClick)
            .testTag("mood_card_${mood.name.lowercase()}"),
        color = if (isSelected) JarvisSurfaceElevated else JarvisObsidian.copy(alpha = 0.65f),
        shape = RoundedCornerShape(18.dp),
        border = if (isSelected) {
            BorderStroke(2.dp, JarvisNeonGradient)
        } else {
            BorderStroke(1.dp, JarvisBorderGlow)
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = mood.emoji,
                fontSize = 36.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mood.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) JarvisCyan else JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = mood.sampleLine,
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun VoiceRealismToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = JarvisTextPrimary,
            modifier = Modifier.weight(1f)
        )
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
