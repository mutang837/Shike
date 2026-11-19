package dev.shike.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.shike.domain.Appearance
import java.time.LocalTime

@Composable
fun ShikeTheme(s: Appearance, content: @Composable () -> Unit) {
    val dark =
        when (s.theme) {
            "dark" -> true
            "light" -> false
            "schedule" -> LocalTime.now().hour in 19..23 || LocalTime.now().hour in 0..6
            else -> isSystemInDarkTheme()
        }
    val primary = Color(if (dark) s.darkAccent else s.accent)
    val bg = if (dark) s.darkBackground else s.background
    val surface = if (dark) s.darkSurface else s.surface
    val text = if (dark) s.darkText else s.text
    val colors =
        if (dark)
            darkColorScheme(
                primary = primary,
                onPrimary = Color(0xFF193524),
                primaryContainer = Color(0xFF2B4535),
                onPrimaryContainer = Color(0xFFD8EBDC),
                background = Color(if (bg == 0L) 0xFF191D1A else bg),
                surface = Color(if (surface == 0L) 0xFF222723 else surface),
                surfaceContainer = Color(0xFF272D28),
                onBackground = Color(if (text == 0L) 0xFFE3E9E2 else text),
                onSurface = Color(if (text == 0L) 0xFFE3E9E2 else text),
            )
        else
            lightColorScheme(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE6EEE4),
                onPrimaryContainer = Color(0xFF314B38),
                background = Color(if (bg == 0L) 0xFFF5F6F2 else bg),
                surface = Color(if (surface == 0L) 0xFFFFFFFF else surface),
                surfaceContainer = Color(0xFFEEF1EA),
                onBackground = Color(if (text == 0L) 0xFF26332B else text),
                onSurface = Color(if (text == 0L) 0xFF26332B else text),
                outlineVariant = Color(0xFFDDE3D9),
            )
    val family =
        when (s.font) {
            "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace
            else -> FontFamily.Default
        }
    val base = Typography()
    fun style(v: androidx.compose.ui.text.TextStyle) =
        v.copy(
            fontFamily = family,
            letterSpacing = s.letterSpacing.sp,
            lineHeight = (v.fontSize.value * s.lineHeight).sp,
        )
    val type =
        Typography(
            bodyLarge = style(base.bodyLarge),
            bodyMedium = style(base.bodyMedium),
            bodySmall = style(base.bodySmall),
            titleLarge = style(base.titleLarge),
            titleMedium = style(base.titleMedium),
            titleSmall = style(base.titleSmall),
            headlineSmall = style(base.headlineSmall),
            headlineMedium = style(base.headlineMedium),
            labelLarge = style(base.labelLarge),
            labelMedium = style(base.labelMedium),
            labelSmall = style(base.labelSmall),
        )
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * s.fontScale)) {
        MaterialTheme(
            colorScheme =
                if (s.contrast)
                    colors.copy(
                        onSurface = if (dark) Color.White else Color.Black,
                        onBackground = if (dark) Color.White else Color.Black,
                    )
                else colors,
            typography = type,
            content = content,
        )
    }
}
