package tare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tare.body.Rating

@Immutable
data class TareColors(
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val accent: Color,
    val accentSoft: Color,
    val selection: Color,
    val headerTop: Color,
    val headerMid: Color,
    val headerBottom: Color,
    val background: Color,
    val card: Color,
    val tile: Color,
    val tileOnHeader: Color,
    val divider: Color,
    val good: Pair<Color, Color>,
    val neutral: Pair<Color, Color>,
    val caution: Pair<Color, Color>,
    val alert: Pair<Color, Color>,
    val dark: Boolean,
) {
    val headerBrush: Brush get() = Brush.verticalGradient(listOf(headerTop, headerMid, headerBottom))

    /** Background and text colours for a rating chip. */
    fun chip(tone: Rating.Tone): Pair<Color, Color> = when (tone) {
        Rating.Tone.GOOD -> good
        Rating.Tone.NEUTRAL -> neutral
        Rating.Tone.CAUTION -> caution
        Rating.Tone.ALERT -> alert
    }
}

private val Light = TareColors(
    ink = Color(0xFF0F2742),
    inkSoft = Color(0xFF52627A),
    inkFaint = Color(0xFF8A97AA),
    accent = Color(0xFF138A80),
    accentSoft = Color(0xFFD5EEEC),
    selection = Color(0xFFB9DDE4),
    headerTop = Color(0xFFEEF3FE),
    headerMid = Color(0xFFDDEBFA),
    headerBottom = Color(0xFFCBE5EF),
    background = Color(0xFFF3F5F9),
    card = Color(0xFFFFFFFF),
    tile = Color(0xFFF4F6FA),
    tileOnHeader = Color(0xB3FFFFFF),
    divider = Color(0xFFE4E8EF),
    good = Color(0xFFDDF3EA) to Color(0xFF13795B),
    neutral = Color(0xFFE6ECF5) to Color(0xFF3B4C63),
    caution = Color(0xFFFCEBD7) to Color(0xFF94570F),
    alert = Color(0xFFFBDCDC) to Color(0xFFA3282B),
    dark = false,
)

private val Dark = TareColors(
    ink = Color(0xFFEAF1FA),
    inkSoft = Color(0xFFA3B3C7),
    inkFaint = Color(0xFF6E8097),
    accent = Color(0xFF55D3C3),
    accentSoft = Color(0xFF1C4650),
    selection = Color(0xFF214A5A),
    headerTop = Color(0xFF0D1828),
    headerMid = Color(0xFF0F2536),
    headerBottom = Color(0xFF122F3F),
    background = Color(0xFF0A131F),
    card = Color(0xFF132131),
    tile = Color(0xFF1A2A3D),
    tileOnHeader = Color(0x33FFFFFF),
    divider = Color(0xFF243548),
    good = Color(0xFF173F35) to Color(0xFF7EE0BE),
    neutral = Color(0xFF233247) to Color(0xFFB9C6D8),
    caution = Color(0xFF47331A) to Color(0xFFF4C27F),
    alert = Color(0xFF4A2224) to Color(0xFFF59C9C),
    dark = true,
)

val LocalTareColors = staticCompositionLocalOf { Light }

object Tare {
    val colors: TareColors @Composable get() = LocalTareColors.current

    val bigNumber = TextStyle(fontSize = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, lineHeight = 64.sp)
    val tileNumber = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    val section = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 16.sp)
    val label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontSize = 13.sp)
}

@Composable
fun TareTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color(0xFF00201C), secondary = c.accent,
            background = c.background, surface = c.card, onSurface = c.ink, onBackground = c.ink,
            surfaceContainerLow = c.card, surfaceContainer = c.card, surfaceContainerHigh = c.tile,
            outline = c.inkFaint, outlineVariant = c.divider,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = Color.White, secondary = c.accent,
            background = c.background, surface = c.card, onSurface = c.ink, onBackground = c.ink,
            surfaceContainerLow = c.card, surfaceContainer = c.card, surfaceContainerHigh = c.tile,
            outline = c.inkFaint, outlineVariant = c.divider,
        )
    }
    CompositionLocalProvider(LocalTareColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
