package com.andrespar2.pardl.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens de Liquid Glass inspirados en ParSound: fondos translúcidos, bordes
 * finos, reflejos y desenfoque discreto. No se usan componentes Android grises
 * por defecto.
 */
data class GlassTokens(
    val isDark: Boolean,
    val backgroundStart: Color,
    val backgroundEnd: Color,
    val accent: Color,
    val accentSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val glassFill: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassAlpha: Float,
    val glow: Float,
    val blur: Dp,
    val animations: Boolean,
    val amoled: Boolean
)

val LocalGlass = staticCompositionLocalOf {
    GlassTokens(
        isDark = true,
        backgroundStart = Color(0xFF070B12),
        backgroundEnd = Color(0xFF0F1826),
        accent = AccentCyan,
        accentSoft = AccentBlue,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        glassFill = Color(0x12FFFFFF),
        glassStrong = Color(0x1FFFFFFF),
        glassBorder = Color(0x33FFFFFF),
        glassHighlight = Color(0x1AFFFFFF),
        glassAlpha = 0.55f,
        glow = 0.45f,
        blur = 28.dp,
        animations = true,
        amoled = false
    )
}

@Composable
fun ParDLTheme(content: @Composable () -> Unit) {
    val mode by Appearance.mode.collectAsState()
    val blurValue by Appearance.blur.collectAsState()
    val animations by Appearance.animations.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val amoled = mode == "amoled"
    val dark = amoled || mode == "dark" || (mode == "system" && systemDark)
    val animated = animations
    val colorSpec = tween<Color>(if (animated) 700 else 0)

    val backgroundStart by animateColorAsState(
        if (dark) (if (amoled) Color.Black else Color(0xFF070B12))
        else LightBackgroundStart,
        colorSpec, label = "bgStart"
    )
    val backgroundEnd by animateColorAsState(
        if (dark) (if (amoled) Color.Black else Color(0xFF101A2A))
        else LightBackgroundEnd,
        colorSpec, label = "bgEnd"
    )
    val accent by animateColorAsState(
        if (dark) AccentCyan else Color(0xFF1769D2),
        tween(if (animated) 550 else 0),
        label = "accent"
    )
    val accentSoft by animateColorAsState(
        if (dark) AccentBlue else Color(0xFF2B5CC7),
        tween(if (animated) 550 else 0),
        label = "accentSoft"
    )
    val textPrimary = if (dark) TextPrimary else LightTextPrimary
    val textSecondary = if (dark) TextSecondary else LightTextSecondary
    val glassFill = if (dark) {
        Color.White.copy(alpha = if (amoled) 0.055f else (0.045f + blurValue * 0.08f))
    } else {
        Color.White.copy(alpha = 0.52f)
    }
    val glassStrong = if (dark) {
        Color.White.copy(alpha = if (amoled) 0.085f else (0.08f + blurValue * 0.10f))
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    val glassBorder = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.82f)

    val tokens = GlassTokens(
        isDark = dark,
        backgroundStart = backgroundStart,
        backgroundEnd = backgroundEnd,
        accent = accent,
        accentSoft = accentSoft,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        glassFill = glassFill,
        glassStrong = glassStrong,
        glassBorder = glassBorder,
        glassHighlight = Color.White.copy(alpha = 0.10f),
        glassAlpha = blurValue.coerceIn(0f, 1f),
        glow = 0.45f,
        blur = (blurValue * 38f).dp,
        animations = animated,
        amoled = amoled
    )

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF04121E),
            primaryContainer = accentSoft.copy(alpha = 0.32f),
            onPrimaryContainer = textPrimary,
            secondary = accentSoft,
            onSecondary = Color.White,
            background = backgroundStart,
            onBackground = textPrimary,
            surface = backgroundEnd,
            onSurface = textPrimary,
            surfaceVariant = glassStrong,
            onSurfaceVariant = textSecondary,
            outline = glassBorder,
            error = Danger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEAFF),
            onPrimaryContainer = LightTextPrimary,
            secondary = accentSoft,
            onSecondary = Color.White,
            background = backgroundStart,
            onBackground = textPrimary,
            surface = Color.White,
            onSurface = textPrimary,
            surfaceVariant = LightSurface,
            onSurfaceVariant = textSecondary,
            outline = LightOutline,
            error = Color(0xFFC62845),
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalGlass provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ParDLTypography,
            content = content
        )
    }
}

fun lighten(color: Color, amount: Float): Color = Color(
    red = color.red + (1f - color.red) * amount,
    green = color.green + (1f - color.green) * amount,
    blue = color.blue + (1f - color.blue) * amount,
    alpha = 1f
)

fun darken(color: Color, factor: Float): Color = Color(
    red = color.red * factor,
    green = color.green * factor,
    blue = color.blue * factor,
    alpha = 1f
)
