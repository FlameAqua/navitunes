package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.runtime.compositionLocalOf
import ie.adrianszydlo.navitunes.data.api.Song

/**
 * Bus for "show details for this song" requests from any SongRow's long-press
 * menu. The shell owns the info dialog so list items don't each render one.
 */
val LocalSongInfoRequest = compositionLocalOf<(Song) -> Unit> {
    { /* no-op when unbound */ }
}
