package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.runtime.compositionLocalOf
import ie.adrianszydlo.navitunes.playback.PlayerController

/**
 * Exposed by [RootNav] so any screen can grab the active PlayerController
 * (for long-press song menus, queue manipulation, etc.) without having to
 * thread it through every screen signature.
 */
val LocalPlayerController = compositionLocalOf<PlayerController> {
    error("PlayerController not provided")
}
