package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.runtime.compositionLocalOf
import ie.adrianszydlo.navitunes.data.api.Song

/**
 * Bus for "remove this song from the server library" requests bubbled up
 * from any SongRow's long-press menu. The shell owns the confirmation
 * dialog and the actual network call to keep destructive UI out of list
 * items.
 */
val LocalRemoveSongRequest = compositionLocalOf<(Song) -> Unit> {
    { /* no-op when unbound */ }
}
