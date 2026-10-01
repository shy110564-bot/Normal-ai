package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// "Neon Glass Future" Design System (v5.0 Final Ultimate — AK EXPLOITS)
val JarvisObsidian = Color(0xFF000000)          // AMOLED Black Base BG
val JarvisDeepNavy = Color(0xFF0A0E1A)          // Surface
val JarvisSurfaceCard = Color(0xFF121826)       // Surface Alt
val JarvisSurfaceElevated = Color(0xFF1A2133)   // Elevated
val JarvisBorderGlow = Color(0xFF283552)

val JarvisCyan = Color(0xFF00E5FF)              // Primary Neon Cyan
val JarvisCyanDim = Color(0xFF00A3B8)
val JarvisPurple = Color(0xFFB14EFF)            // Secondary Neon Purple
val JarvisPink = Color(0xFFFF2E93)              // Accent Hot Pink
val JarvisArcBlue = Color(0xFF4EA8FF)           // Info Sky Blue
val JarvisElectricViolet = Color(0xFFB14EFF)

val JarvisEmerald = Color(0xFF00FF88)           // Success Neon Green
val JarvisAmber = Color(0xFFFFB800)             // Warning Amber
val JarvisCrimson = Color(0xFFFF3B5C)           // Danger Neon Red

val JarvisTextPrimary = Color(0xFFFFFFFF)
val JarvisTextSecondary = Color(0xFFB0B8C8)
val JarvisTextMuted = Color(0xFF6B7280)

val JarvisNeonGradient = Brush.linearGradient(
    colors = listOf(JarvisCyan, JarvisPurple, JarvisPink)
)

val JarvisGlassGradient = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.09f),
        Color.White.copy(alpha = 0.02f)
    )
)
