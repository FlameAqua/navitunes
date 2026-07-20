package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import ie.adrianszydlo.navitunes.R
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
    val notifier = LocalNotifier.current
    val wifiOnly by container.preferences.wifiOnly.collectAsState(initial = false)
    val uploadEndpoint by container.preferences.uploadEndpoint.collectAsState(initial = null)
    val openPicker = LocalAddToPlaylistRequest.current
    val openRemove = LocalRemoveSongRequest.current
    val openInfo = LocalSongInfoRequest.current

    return remember(song.id, playNow, onOpenAlbum, wifiOnly, uploadEndpoint, onRemoveFromPlaylist) {
        SongActions(
            onPlay = playNow,
            onStartSongRadio = {
                scope.launch {
                    val queue = ie.adrianszydlo.navitunes.ui.radio.buildSongRadioQueue(
                        container.libraryRepository, song
                    )
                    if (queue.size <= 1) {
                        notifier.error(R.string.song_radio_failed)
                    } else {
                        controller.playSongRadio(song, queue)
                        notifier.info(R.string.starting_radio_named, song.title)
                    }
                }
            },
            onPlayNext = { controller.playNext(song) },
            onAddToQueue = {
                controller.addToQueue(song)
                notifier.info(R.string.added_to_queue)
            },
            onAddToPlaylist = { openPicker(song) },
            onDownload = {
                if (!container.downloadRepository.hasStorageAccess()) {
                    notifier.error(R.string.storage_permission_hint)
                } else {
                    scope.launch {
                        container.downloadRepository.enqueueSong(song, wifiOnly)
                        notifier.info(R.string.downloading_named, song.title)
                    }
                }
            },
            onFavorite = {
                scope.launch {
                    runCatching {
                        if (song.starred.isNullOrBlank()) {
                            container.playbackRepository.star(song.id)
                            notifier.success(R.string.added_to_favorites)
                        } else {
                            container.playbackRepository.unstar(song.id)
                            notifier.info(R.string.removed_from_favorites)
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
