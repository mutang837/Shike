package dev.shike.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.shike.domain.Appearance
import java.time.LocalTime
import java.time.Instant

val LocalShikeNow = compositionLocalOf { Instant.now() }

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
    val canvas = Color(if (bg != 0L) bg else if (dark) 0xFF14171B else 0xFFF4F5F7)
    val panel = Color(if (surface != 0L) surface else if (dark) 0xFF1D2228 else 0xFFFFFFFF)
    val ink = Color(if (text != 0L) text else if (dark) 0xFFF0F2F4 else 0xFF202630)
    val muted = if (dark) Color(0xFFB0BBC7) else Color(0xFF556273)
    val softAccent = primary.copy(alpha = if (dark) .16f else .11f).compositeOver(panel)
    val onAccent = if (primary.luminance() > .179f) Color.Black else Color.White
    val colors =
        if (dark)
            darkColorScheme(
                primary = primary,
                onPrimary = onAccent,
                primaryContainer = softAccent,
                onPrimaryContainer = ink,
                secondaryContainer = softAccent,
                onSecondaryContainer = ink,
                background = canvas,
                surface = panel,
                surfaceContainer = Color(0xFF252C34),
                surfaceVariant = Color(0xFF303843),
                onBackground = ink,
                onSurface = ink,
                onSurfaceVariant = muted,
                outline = Color(0xFF83909E),
                outlineVariant = Color(0xFF343E49),
                error = Color(0xFFFFB4AB),
            )
        else
            lightColorScheme(
                primary = primary,
                onPrimary = onAccent,
                primaryContainer = softAccent,
                onPrimaryContainer = ink,
                secondaryContainer = softAccent,
                onSecondaryContainer = ink,
                background = canvas,
                surface = panel,
                surfaceContainer = Color(0xFFEBEEF2),
                surfaceVariant = Color(0xFFE4E8EE),
                onBackground = ink,
                onSurface = ink,
                onSurfaceVariant = muted,
                outline = Color(0xFF6E7A8A),
                outlineVariant = Color(0xFFE1E5EA),
                error = Color(0xFFB42318),
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
            bodyLarge = style(base.bodyLarge.copy(fontSize = 16.sp)),
            bodyMedium = style(base.bodyMedium.copy(fontSize = 14.sp)),
            bodySmall = style(base.bodySmall.copy(fontSize = 12.sp)),
            titleLarge = style(base.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold)),
            titleMedium = style(base.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)),
            titleSmall = style(base.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)),
            headlineSmall = style(base.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)),
            headlineMedium = style(base.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold)),
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
                        onSurfaceVariant = if (dark) Color.White else Color.Black,
                    )
                else colors,
            typography = type,
            content = content,
        )
    }
}
