package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberBlue,
    secondary = CyberTeal,
    tertiary = ElectricViolet,
    background = MidnightBlue,
    surface = DeepSlate,
    onPrimary = MidnightBlue,
    onSecondary = MidnightBlue,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = LightSlate,
    onSurfaceVariant = TextPrimary,
    error = ErrorCoral,
    errorContainer = MidnightBlue,
    onErrorContainer = ErrorCoral
)

private val LightColorScheme = lightColorScheme(
    primary = DeepSlate,
    secondary = LightSlate,
    tertiary = CyberTeal,
    background = TextPrimary,
    surface = TextPrimary,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = MidnightBlue,
    onSurface = MidnightBlue,
    error = ErrorCoral
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force premium dark theme for cohesive high tech branding
    dynamicColor: Boolean = false, // Set to false to preserve the customized brand layout
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
