package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.example.domain.tools.PendingConfirmationAction
import com.example.service.JarvisOperationalState
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisArcBlue
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricViolet
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisSurfaceCard
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
        JarvisOperationalState.THINKING -> JarvisAmber
        JarvisOperationalState.SPEAKING -> JarvisArcBlue
        JarvisOperationalState.INTERRUPTED -> JarvisCrimson
        JarvisOperationalState.OFFLINE -> JarvisElectricViolet
        JarvisOperationalState.ERROR -> JarvisCrimson
    }
}

@Composable
fun JarvisCoreVisualizer(
    operationalState: JarvisOperationalState,
    amplitude: Float,
    spectrumBands: List<Float>,
    sizeDp: Dp = 190.dp,
    onCoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core_anim")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rot"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rot"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val primaryColor = stateColor(operationalState)
    val activeBoost = when (operationalState) {
        JarvisOperationalState.LISTENING,
        JarvisOperationalState.SPEAKING -> amplitude.coerceIn(0.05f, 1.0f)
        JarvisOperationalState.THINKING -> 0.45f
        else -> 0.08f
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

            // Ambient radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f + activeBoost * 0.25f),
                        primaryColor.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Outer segmented tech ring
            rotate(degrees = outerRotation, pivot = center) {
                val ringRadius = maxRadius * 0.90f
                val arcSpan = 42f
                for (i in 0 until 6) {
                    drawArc(
                        color = primaryColor.copy(alpha = 0.65f),
                        startAngle = i * 60f,
                        sweepAngle = arcSpan,
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Counter-rotating inner telemetry ring
            rotate(degrees = innerRotation, pivot = center) {
                val innerRadius = maxRadius * 0.74f
                for (i in 0 until 12) {
                    drawArc(
                        color = primaryColor.copy(alpha = 0.45f),
                        startAngle = i * 30f,
                        sweepAngle = 14f,
                        useCenter = false,
                        topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                        size = Size(innerRadius * 2, innerRadius * 2),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt)
                    )
                }
            }

            // Radial spectrum bars around the core
            val bandCount = spectrumBands.size.coerceAtLeast(1)
            val baseBarRadius = maxRadius * 0.46f
            val maxBarLength = maxRadius * 0.24f
            for (i in 0 until bandCount) {
                val angleRad = Math.toRadians((i * (360.0 / bandCount)) - 90.0)
                val bandVal = spectrumBands[i].coerceIn(0.05f, 1.0f)
                val barLen = maxBarLength * (0.25f + 0.75f * bandVal)
                val startX = center.x + (baseBarRadius * cos(angleRad)).toFloat()
                val startY = center.y + (baseBarRadius * sin(angleRad)).toFloat()
                val endX = center.x + ((baseBarRadius + barLen) * cos(angleRad)).toFloat()
                val endY = center.y + ((baseBarRadius + barLen) * sin(angleRad)).toFloat()

                drawLine(
                    color = primaryColor.copy(alpha = 0.85f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Core nucleus circle
            val nucleusRadius = maxRadius * 0.36f * (if (operationalState == JarvisOperationalState.THINKING) pulseScale else (1f + activeBoost * 0.18f))
            drawCircle(
                color = JarvisObsidian.copy(alpha = 0.9f),
                radius = nucleusRadius,
                center = center
            )
            drawCircle(
                color = primaryColor,
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = operationalState.badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor,
                fontWeight = FontWeight.Bold
            )
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
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
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
                    Text("Cancel")
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
                    Text("Confirm & Execute", fontWeight = FontWeight.Bold)
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
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(JarvisCyan.copy(alpha = 0.6f), JarvisBorderGlow, Color.Transparent)
                    )
                )
        )
    }
}
