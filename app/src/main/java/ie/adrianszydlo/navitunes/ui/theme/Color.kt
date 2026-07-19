package ie.adrianszydlo.navitunes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Theme-reactive colour tokens.
 *
 * These read the active [NavColors] from [LocalNavColors], so the same names the
 * app already uses (`Accent`, `Text2`, `Surface`, …) now resolve to the correct
 * light- or dark-theme value at every call site — no call-site changes needed.
 *
 * They are `@Composable` getters, so they can only be read inside composition
 * (which is where all of the UI reads them). New code can also use
 * [NavTheme].colors for the same values.
 */
val Bg: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.bg
val Surface: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.surface
val SurfaceElev: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.surfaceElev
val BorderCol: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.border
val TextHi: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.textHi
val Text2: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.text2
val Text3: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.text3
val Text4: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.text4
val Accent: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.accent
val AccentOn: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.accentOn
val Danger: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.danger
val Success: Color @Composable @ReadOnlyComposable get() = LocalNavColors.current.success
