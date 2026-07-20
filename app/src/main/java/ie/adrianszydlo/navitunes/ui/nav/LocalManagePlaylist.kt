package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.runtime.compositionLocalOf
import ie.adrianszydlo.navitunes.data.api.Playlist

/**
 * A request to open the playlist-management sheet. [onDeleted] lets the caller react
 * to a successful delete — the detail screen passes a "go home" action so it doesn't
 * linger on a playlist that no longer exists; list rows leave it null (the list just
 * refreshes via the library signal).
 */
data class ManagePlaylistRequest(
    val playlist: Playlist,
    val onDeleted: (() -> Unit)? = null
)

/**
 * Bus for "manage this playlist" requests (rename / edit description / public toggle
 * / delete) bubbled up from a playlist's long-press menu or its detail header. The
 * shell owns the management sheet so a dialog isn't rendered inside every list row.
 */
val LocalManagePlaylistRequest = compositionLocalOf<(ManagePlaylistRequest) -> Unit> {
    { /* no-op when unbound */ }
}
