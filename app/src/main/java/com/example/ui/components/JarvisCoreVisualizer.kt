package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.JarvisSettings
import com.example.domain.tools.PendingConfirmationAction
import com.example.service.JarvisOperationalState
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
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlin.math.cos
import kotlin.math.sin

fun stateColor(state: JarvisOperationalState): Color {
    return when (state) {
        JarvisOperationalState.STANDBY -> JarvisCyan
        JarvisOperationalState.CONNECTING, JarvisOperationalState.RECONNECTING -> JarvisAmber
        JarvisOperationalState.CONNECTED -> JarvisEmerald
        JarvisOperationalState.LISTENING -> JarvisCyan
        JarvisOperationalState.THINKING -> JarvisPurple
        JarvisOperationalState.SPEAKING -> JarvisPink
        JarvisOperationalState.INTERRUPTED -> JarvisCrimson
        JarvisOperationalState.OFFLINE -> JarvisPurple
        JarvisOperationalState.ERROR -> JarvisCrimson
    }
}

/**
 * 3D Glowing Neon Orb with 2 Rotating Neon Rings & Live Waveform Bars
 * ("Neon Glass Future" Design System — Section 14)
 */
@Composable
fun JarvisCoreVisualizer(
    operationalState: JarvisOperationalState,
    amplitude: Float,
    spectrumBands: List<Float>,
    sizeDp: Dp = 170.dp,
    onCoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb_anim")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rot"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rot"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val primaryColor = stateColor(operationalState)
    val secondaryColor = when (operationalState) {
        JarvisOperationalState.THINKING -> JarvisCyan
        JarvisOperationalState.SPEAKING -> JarvisPurple
        else -> JarvisPink
    }
    val activeBoost = when (operationalState) {
        JarvisOperationalState.LISTENING,
        JarvisOperationalState.SPEAKING -> amplitude.coerceIn(0.06f, 1.0f)
        JarvisOperationalState.THINKING -> 0.45f
        else -> 0.10f
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .clickable(onClick = onCoreClick)
            .testTag("jarvis_core_visualizer"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // 1. 3D Aurora Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.36f + activeBoost * 0.25f),
                        JarvisPurple.copy(alpha = 0.18f),
                        JarvisPink.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // 2. Outer Rotating Neon Ring (Cyan -> Purple -> Pink)
            rotate(degrees = outerRotation, pivot = center) {
                val ringRadius = maxRadius * 0.90f
                val arcSpan = 46f
                for (i in 0 until 6) {
                    val ringColor = when (i % 3) {
                        0 -> JarvisCyan
                        1 -> JarvisPurple
                        else -> JarvisPink
                    }
                    drawArc(
                        color = ringColor.copy(alpha = 0.8f),
                        startAngle = i * 60f,
                        sweepAngle = arcSpan,
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 3. Second Counter-Rotating Neon Ring
            rotate(degrees = innerRotation, pivot = center) {
                val innerRadius = maxRadius * 0.74f
                for (i in 0 until 12) {
                    drawArc(
                        color = secondaryColor.copy(alpha = 0.6f),
                        startAngle = i * 30f,
                        sweepAngle = 15f,
                        useCenter = false,
                        topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                        size = Size(innerRadius * 2, innerRadius * 2),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 4. Live Voice Waveform Bars around the 3D Orb
            val bandCount = spectrumBands.size.coerceAtLeast(1)
            val baseBarRadius = maxRadius * 0.45f
            val maxBarLength = maxRadius * 0.25f
            for (i in 0 until bandCount) {
                val angleRad = Math.toRadians((i * (360.0 / bandCount)) - 90.0)
                val bandVal = spectrumBands[i].coerceIn(0.05f, 1.0f)
                val barLen = maxBarLength * (0.25f + 0.75f * bandVal)
                val startX = center.x + (baseBarRadius * cos(angleRad)).toFloat()
                val startY = center.y + (baseBarRadius * sin(angleRad)).toFloat()
                val endX = center.x + ((baseBarRadius + barLen) * cos(angleRad)).toFloat()
                val endY = center.y + ((baseBarRadius + barLen) * sin(angleRad)).toFloat()

                drawLine(
                    color = if (i % 2 == 0) primaryColor else JarvisPink.copy(alpha = 0.85f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. Inner 3D Glowing Sphere Nucleus
            val nucleusRadius = maxRadius * 0.37f * pulseScale * (1f + activeBoost * 0.14f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.45f),
                        JarvisObsidian.copy(alpha = 0.95f)
                    ),
                    center = Offset(center.x - nucleusRadius * 0.2f, center.y - nucleusRadius * 0.2f),
                    radius = nucleusRadius * 1.2f
                ),
                radius = nucleusRadius,
                center = center
            )
            drawCircle(
                brush = JarvisNeonGradient,
                radius = nucleusRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (operationalState) {
                    JarvisOperationalState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                    JarvisOperationalState.LISTENING -> Icons.Default.Mic
                    JarvisOperationalState.INTERRUPTED -> Icons.Default.Stop
                    else -> Icons.Default.GraphicEq
                },
                contentDescription = operationalState.badgeText,
                tint = primaryColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = operationalState.badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Screen 1: Animated Neon Glass Splash Screen ("Crafted by AK EXPLOITS")
 */
@Composable
fun JarvisSplashOverlay(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(500))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(JarvisObsidian)
                .clickable(onClick = onDismiss)
                .testTag("jarvis_splash_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                JarvisCoreVisualizer(
                    operationalState = JarvisOperationalState.LISTENING,
                    amplitude = 0.65f,
                    spectrumBands = List(16) { 0.4f + (it % 4) * 0.15f },
                    sizeDp = 200.dp,
                    onCoreClick = onDismiss
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "J.A.R.V.I.S.",
                    style = MaterialTheme.typography.displayLarge.copy(
                        brush = JarvisNeonGradient,
                        fontSize = 42.sp
                    ),
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ji… boliye, main sun rahi hun 💕",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisCyan
                )
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, JarvisPurple)
                ) {
                    Text(
                        text = "🎨 Crafted by ${JarvisSettings.CREATOR_NAME} • v${JarvisSettings.APP_VERSION}",
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisTextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Screen 9: Slide-Down Neon Glass Notification Overlay Banner
 */
@Composable
fun JarvisNotificationOverlayBanner(
    visible: Boolean,
    message: String,
    onTalkNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .border(1.5.dp, JarvisNeonGradient, RoundedCornerShape(20.dp))
                .testTag("jarvis_notification_overlay"),
            colors = CardDefaults.cardColors(containerColor = JarvisSurfaceElevated.copy(alpha = 0.96f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "◉ JARVIS",
                            style = MaterialTheme.typography.labelLarge,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HudStatusBadge(label = "LIVE CARE", color = JarvisPink)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onTalkNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = JarvisObsidian
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Bolo 💕", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = JarvisTextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun HudStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PendingConfirmationBanner(
    action: PendingConfirmationAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, JarvisAmber, RoundedCornerShape(16.dp))
            .testTag("pending_confirmation_card"),
        colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Confirmation",
                    tint = JarvisAmber,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = action.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisAmber,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, JarvisTextSecondary),
                    modifier = Modifier.testTag("cancel_sensitive_action_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rehne Do")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisEmerald,
                        contentColor = JarvisObsidian
                    ),
                    modifier = Modifier.testTag("confirm_sensitive_action_btn")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Confirm", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Haan Ji, Karo ✅", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HudSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = JarvisCyan
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextSecondary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(JarvisCyan, JarvisPurple, JarvisPink, Color.Transparent)
                    )
                )
        )
    }
}
