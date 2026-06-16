package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.runtime.compositionLocalOf
import ie.adrianszydlo.navitunes.data.api.Song

/**
 * Bus for "add this song to a playlist" requests bubbled up from any SongRow's
 * long-press menu. The shell owns the picker dialog so we don't end up rendering
 * a dialog inside every list item.
 */
val LocalAddToPlaylistRequest = compositionLocalOf<(Song) -> Unit> {
    { /* no-op when unbound */ }
}
