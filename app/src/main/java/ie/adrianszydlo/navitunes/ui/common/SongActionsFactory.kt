package ie.adrianszydlo.navitunes.ui.common

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.playback.PlayerController
import ie.adrianszydlo.navitunes.ui.nav.LocalAddToPlaylistRequest
import ie.adrianszydlo.navitunes.ui.nav.LocalRemoveSongRequest
import ie.adrianszydlo.navitunes.ui.nav.LocalSongInfoRequest
import kotlinx.coroutines.launch

/**
 * Convenience: assembles the standard long-press menu actions for a song.
 *
 * Caller passes the [PlayerController] (needed for play next / add to queue) and
 * an optional onOpenAlbum routing callback. Everything else (download enqueue,
 * favorite toggle, Toast feedback) is wired here so screens stay tiny.
 */
@Composable
fun rememberSongActions(
    song: Song,
    controller: PlayerController,
    onOpenAlbum: ((String) -> Unit)? = null,
    playNow: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null
): SongActions {
    val container = NavitunesApp.container()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val wifiOnly by container.preferences.wifiOnly.collectAsState(initial = false)
    val uploadEndpoint by container.preferences.uploadEndpoint.collectAsState(initial = null)
    val openPicker = LocalAddToPlaylistRequest.current
    val openRemove = LocalRemoveSongRequest.current
    val openInfo = LocalSongInfoRequest.current

    return remember(song.id, playNow, onOpenAlbum, wifiOnly, uploadEndpoint, onRemoveFromPlaylist) {
        SongActions(
            onPlay = playNow,
            onPlayNext = { controller.playNext(song) },
            onAddToQueue = {
                controller.addToQueue(song)
                Toast.makeText(ctx, "Added to queue", Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = { openPicker(song) },
            onDownload = {
                if (!container.downloadRepository.hasStorageAccess()) {
                    Toast.makeText(ctx, "Grant storage access in Settings → Downloads first.", Toast.LENGTH_LONG).show()
                } else {
                    scope.launch {
                        container.downloadRepository.enqueueSong(song, wifiOnly)
                        Toast.makeText(ctx, "Downloading \"${song.title}\"", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onFavorite = {
                scope.launch {
                    runCatching {
                        if (song.starred.isNullOrBlank()) {
                            container.playbackRepository.star(song.id)
                            Toast.makeText(ctx, "Added to favorites", Toast.LENGTH_SHORT).show()
                        } else {
                            container.playbackRepository.unstar(song.id)
                            Toast.makeText(ctx, "Removed from favorites", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onShowInfo = { openInfo(song) },
            onOpenAlbum = if (onOpenAlbum != null && !song.albumId.isNullOrBlank()) {
                { onOpenAlbum(song.albumId) }
            } else null,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            // The remove endpoint defaults to the profile server, so this is always
            // available; it surfaces a clear error if no receiver is running there.
            onRemoveFromLibrary = { openRemove(song) }
        )
    }
}
