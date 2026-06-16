package ie.adrianszydlo.navitunes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentOn,
    secondary = Accent2,
    onSecondary = AccentOn,
    background = Bg,
    onBackground = TextHi,
    surface = Surface,
    onSurface = TextHi,
    surfaceVariant = SurfaceElev,
    onSurfaceVariant = Text2,
    outline = BorderCol,
    outlineVariant = BorderStrong,
    error = Danger,
    onError = Color.White
)

@Composable
fun NavitunesTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always dark — matches the PWA's locked-in look.
    val scheme = DarkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = scheme.background.luminance() > 0.5f
            insets.isAppearanceLightNavigationBars = scheme.background.luminance() > 0.5f
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = NavitunesTypography,
        content = content
    )
}
