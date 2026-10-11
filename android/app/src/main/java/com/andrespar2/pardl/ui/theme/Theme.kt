package com.andrespar2.pardl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ParDLColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = AmoledBlack,
    primaryContainer = AccentTeal,
    onPrimaryContainer = AmoledBlack,
    secondary = AccentBlue,
    onSecondary = AmoledBlack,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSoft,
    outlineVariant = Surface3,
    error = Danger,
    onError = AmoledBlack,
    scrim = AmoledBlack
)

@Composable
fun ParDLTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ParDLColorScheme,
        typography = ParDLTypography,
        content = content
    )
}
