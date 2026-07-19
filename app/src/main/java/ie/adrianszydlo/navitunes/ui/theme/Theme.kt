package ie.adrianszydlo.navitunes.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Maps our semantic [NavColors] onto a Material3 [androidx.compose.material3.ColorScheme]
 *  so stock M3 components (Switch, Slider, dialogs, text fields, nav bar) theme correctly. */
private fun NavColors.toMaterialScheme() =
    if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = accentOn,
            secondary = accent2,
            onSecondary = accentOn,
            tertiary = accent,
            background = bg,
            onBackground = textHi,
            surface = surface,
            onSurface = textHi,
            surfaceVariant = surfaceElev,
            onSurfaceVariant = text2,
            surfaceContainer = surfaceElev,
            surfaceContainerHigh = surfaceHi,
            outline = border,
            outlineVariant = borderStrong,
            error = danger,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = accentOn,
            secondary = accent2,
            onSecondary = accentOn,
            tertiary = accent,
            background = bg,
            onBackground = textHi,
            surface = surface,
            onSurface = textHi,
            surfaceVariant = surfaceElev,
            onSurfaceVariant = text2,
            surfaceContainer = surfaceElev,
            surfaceContainerHigh = surfaceHi,
            outline = border,
            outlineVariant = borderStrong,
            error = danger,
            onError = Color.White,
        )
    }

@Composable
fun NavitunesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val target = when (themeMode) {
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkNavColors else LightNavColors
        ThemeMode.DARK -> DarkNavColors
        ThemeMode.LIGHT -> LightNavColors
        ThemeMode.SEQUOIA -> SequoiaNavColors
    }
    // Smoothly crossfade every colour token when the theme changes.
    val colors = animateNavColors(target)
    val dark = target.isDark
    val scheme = colors.toMaterialScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insets = WindowCompat.getInsetsController(window, view)
            // Light icons on dark backgrounds, dark icons on light backgrounds.
            insets.isAppearanceLightStatusBars = !dark
            insets.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalNavColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NavitunesTypography,
            content = content
        )
    }
}

/** Animates each colour token toward [target] so theme switches crossfade. */
@Composable
private fun animateNavColors(target: NavColors): NavColors {
    val d = 420
    @Composable fun c(value: Color, label: String) =
        animateColorAsState(value, animationSpec = tween(d), label = label).value
    return NavColors(
        isDark = target.isDark,
        bg = c(target.bg, "bg"),
        bgElevated = c(target.bgElevated, "bgElevated"),
        surface = c(target.surface, "surface"),
        surfaceElev = c(target.surfaceElev, "surfaceElev"),
        surfaceHi = c(target.surfaceHi, "surfaceHi"),
        border = c(target.border, "border"),
        borderStrong = c(target.borderStrong, "borderStrong"),
        textHi = c(target.textHi, "textHi"),
        text2 = c(target.text2, "text2"),
        text3 = c(target.text3, "text3"),
        text4 = c(target.text4, "text4"),
        accent = c(target.accent, "accent"),
        accent2 = c(target.accent2, "accent2"),
        accentOn = c(target.accentOn, "accentOn"),
        danger = c(target.danger, "danger"),
        success = c(target.success, "success"),
    )
}
