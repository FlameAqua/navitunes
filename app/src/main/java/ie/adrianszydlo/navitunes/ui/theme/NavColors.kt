package ie.adrianszydlo.navitunes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic design tokens for Navitunes, resolved per theme.
 *
 * The whole app reads colours through these tokens (surfaced as theme-reactive
 * accessors in `Color.kt` and via [NavTheme]) rather than hard-coded constants,
 * so a single [LocalNavColors] swap re-themes every screen for light/dark.
 */
data class NavColors(
    val isDark: Boolean,
    /** App background — the base canvas. */
    val bg: Color,
    /** A slightly lifted background, used for ambient gradients. */
    val bgElevated: Color,
    /** Card / grouped-content surface. */
    val surface: Color,
    /** An elevated surface (chips, art placeholders, mini-player). */
    val surfaceElev: Color,
    /** The highest resting surface (pressed / hovered states). */
    val surfaceHi: Color,
    /** Hairline divider / card border. */
    val border: Color,
    /** A stronger border for emphasis. */
    val borderStrong: Color,
    /** Primary text. */
    val textHi: Color,
    /** Secondary text. */
    val text2: Color,
    /** Tertiary / metadata text. */
    val text3: Color,
    /** Faintest text / disabled glyphs. */
    val text4: Color,
    /** Brand accent (terracotta). */
    val accent: Color,
    /** A deeper accent for gradients / pressed accents. */
    val accent2: Color,
    /** Foreground colour that reads on top of [accent]. */
    val accentOn: Color,
    val danger: Color,
    val success: Color,
)

/** Dark theme — the warm near-black identity carried over (and lightly refined). */
val DarkNavColors = NavColors(
    isDark = true,
    bg = Color(0xFF0E0A08),
    bgElevated = Color(0xFF17110C),
    surface = Color(0xFF15100D),
    surfaceElev = Color(0xFF1F1813),
    surfaceHi = Color(0xFF2A201A),
    border = Color(0xFF2E241D),
    borderStrong = Color(0xFF3D2F25),
    textHi = Color(0xFFF5EDE3),
    text2 = Color(0xFFC9BCB0),
    text3 = Color(0xFF9C8E81),
    text4 = Color(0xFF6B5D51),
    accent = Color(0xFFD97757),
    accent2 = Color(0xFFC2643F),
    accentOn = Color(0xFF1A0A04),
    danger = Color(0xFFD65D5D),
    success = Color(0xFF7AB86A),
)

/** Light theme — warm paper, not clinical blue-grey. Same terracotta identity. */
val LightNavColors = NavColors(
    isDark = false,
    bg = Color(0xFFF5F0E8),
    bgElevated = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceElev = Color(0xFFFBF7F1),
    surfaceHi = Color(0xFFF0E9DE),
    border = Color(0xFFE7DDCE),
    borderStrong = Color(0xFFD8CAB6),
    textHi = Color(0xFF221B14),
    text2 = Color(0xFF574C40),
    text3 = Color(0xFF86786A),
    text4 = Color(0xFFAB9D8D),
    accent = Color(0xFFC15E38),
    accent2 = Color(0xFFA94E2C),
    accentOn = Color(0xFFFFF7F2),
    danger = Color(0xFFC0413E),
    success = Color(0xFF4C8A3C),
)

/**
 * Sequoia — a warm, cozy "twilight" theme. Deeper espresso backgrounds with richer,
 * more saturated amber/coral warmth than the neutral dark. Its own distinct mood.
 */
val SequoiaNavColors = NavColors(
    isDark = true,
    bg = Color(0xFF1A120D),
    bgElevated = Color(0xFF251811),
    surface = Color(0xFF221711),
    surfaceElev = Color(0xFF31211A),
    surfaceHi = Color(0xFF3E2B21),
    border = Color(0xFF432E23),
    borderStrong = Color(0xFF573C2D),
    textHi = Color(0xFFFCEFE2),
    text2 = Color(0xFFE0C7B3),
    text3 = Color(0xFFB0917B),
    text4 = Color(0xFF7E6250),
    accent = Color(0xFFF0895A),
    accent2 = Color(0xFFD96E3F),
    accentOn = Color(0xFF2A1006),
    danger = Color(0xFFE1655F),
    success = Color(0xFF8FBE6F),
)

/** Active token set. Dynamic so the theme-crossfade animation only recomposes readers. */
val LocalNavColors = compositionLocalOf { DarkNavColors }

/** Convenience accessor for new code: `NavTheme.colors.accent`. */
object NavTheme {
    val colors: NavColors
        @Composable @ReadOnlyComposable
        get() = LocalNavColors.current
}

/** User-selectable theme preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, SEQUOIA }
