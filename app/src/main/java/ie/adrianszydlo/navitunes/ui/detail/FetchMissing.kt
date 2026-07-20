package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.AppContainer
import ie.adrianszydlo.navitunes.data.discovery.SpotifyResult
import ie.adrianszydlo.navitunes.data.discovery.SpotifyType
import ie.adrianszydlo.navitunes.ui.common.ConfirmDialog
import ie.adrianszydlo.navitunes.ui.theme.Accent

/**
 * Finds a Spotify entity ([type]) matching [name] (+ optional [artist]) so it can be
 * handed to the server-side spotdl download queue. Prefers an exact title match, else
 * the top result. Returns null if nothing matches or Spotify isn't reachable.
 */
suspend fun spotifyMatch(
    container: AppContainer,
    name: String,
    artist: String?,
    type: SpotifyType
): SpotifyResult? {
    val query = listOfNotNull(name.ifBlank { null }, artist?.ifBlank { null }).joinToString(" ")
    val results = container.spotifyClient.search(query, type, limit = 5)
    // For albums, prefer a full album over a single/compilation of the same name — a
    // 1-track single would install as a near-empty duplicate album.
    fun isFull(r: SpotifyResult) = r.albumType == null || r.albumType.equals("album", ignoreCase = true)
    val exactFull = results.firstOrNull { it.title.equals(name, ignoreCase = true) && isFull(it) }
    val anyFull = results.firstOrNull { isFull(it) }
    val exact = results.firstOrNull { it.title.equals(name, ignoreCase = true) }
    return if (type == SpotifyType.ALBUM) (exactFull ?: anyFull ?: exact ?: results.firstOrNull())
    else (exact ?: results.firstOrNull())
}

/** Header action that kicks off a server-side install of the whole album/artist. */
@Composable
fun InstallFromSpotifyButton(fetching: Boolean, onClick: () -> Unit) {
    if (fetching) {
        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    } else {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.CloudDownload, contentDescription = "Install from Spotify", tint = Accent)
        }
    }
}

/** Confirmation before sending an album/artist to the server's spotdl download queue. */
@Composable
fun InstallFromSpotifyDialog(
    what: String,
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = "Install this $what?",
        message = "This asks your server to download the full $what \"$title\" from Spotify, " +
            "including any tracks you don't already have. It runs in the background and can take a while.",
        confirmLabel = "Install",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
