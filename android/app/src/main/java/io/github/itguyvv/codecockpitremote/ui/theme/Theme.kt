package io.github.itguyvv.codecockpitremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// CodeCockpit desktop palette: near-black blue-grey with a calm blue accent
val Bg = Color(0xFF0F1015)
val Surface1 = Color(0xFF15161B)
val Surface2 = Color(0xFF1D1F27)
val Border = Color(0xFF2A2D38)
val TextHigh = Color(0xFFE9EAF0)
val TextMid = Color(0xFF9AA0AE)
val Accent = Color(0xFF5F8BFF)
val Ok = Color(0xFF4CC38A)
val Busy = Color(0xFFF2C94C)
val Danger = Color(0xFFE5484D)

/** Idle live session — matches the desktop's gray idle dot (#6e7681) */
val Idle = Color(0xFF6E7681)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B1220),
    secondary = TextMid,
    background = Bg,
    onBackground = TextHigh,
    surface = Surface1,
    onSurface = TextHigh,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    outline = Border,
    outlineVariant = Border,
    error = Danger
)

/** A developer tool: dark by default, the desktop app is dark-only too */
@Composable
fun CodeCockpitRemoteTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme() kept for the linter; the app is deliberately dark-only
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
