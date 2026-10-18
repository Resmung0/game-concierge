package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val GameDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = GameDarkBackground,
    primaryContainer = CyberCyanDim,
    onPrimaryContainer = Color.White,
    secondary = NeonPurple,
    onSecondary = GameDarkBackground,
    secondaryContainer = NeonPurpleDark,
    onSecondaryContainer = Color.White,
    tertiary = NeonEmerald,
    onTertiary = GameDarkBackground,
    background = GameDarkBackground,
    onBackground = TextPrimary,
    surface = GameDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = GameDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = GameBorder
)

private val GameLightColorScheme = lightColorScheme(
    primary = CyberCyanDim,
    onPrimary = Color.White,
    secondary = NeonPurpleDark,
    onSecondary = Color.White,
    tertiary = NeonEmerald,
    onTertiary = Color.White,
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun GameConciergeTheme(
    darkTheme: Boolean = true, // Gaming UI defaults to sleek dark mode
    dynamicColor: Boolean = false, // Keep high contrast gaming palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GameDarkColorScheme
        else -> GameLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    GameConciergeTheme(darkTheme = true, dynamicColor = false, content = content)
}
