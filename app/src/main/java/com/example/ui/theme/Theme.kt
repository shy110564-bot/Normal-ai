package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisObsidian,
    primaryContainer = JarvisSurfaceElevated,
    onPrimaryContainer = JarvisCyan,
    secondary = JarvisPurple,
    onSecondary = Color.White,
    secondaryContainer = JarvisSurfaceCard,
    onSecondaryContainer = JarvisTextPrimary,
    tertiary = JarvisPink,
    onTertiary = Color.White,
    background = JarvisObsidian,
    onBackground = JarvisTextPrimary,
    surface = JarvisDeepNavy,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceCard,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisBorderGlow,
    error = JarvisCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
