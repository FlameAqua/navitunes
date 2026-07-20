package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.common.ConfirmDialog
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.formatDuration
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.nav.ManagePlaylistRequest
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Text3
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(
    id: String,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onGoHome: () -> Unit
) {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    val scope = rememberCoroutineScope()
    val notifier = ie.adrianszydlo.navitunes.ui.common.LocalNotifier.current
    val wifiOnly by container.preferences.wifiOnly.collectAsState(initial = false)
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    val managePlaylist = ie.adrianszydlo.navitunes.ui.nav.LocalManagePlaylistRequest.current
    var indexToRemove by remember { mutableStateOf<Int?>(null) }
    val signalTick by container.librarySignals.refresh.collectAsState()

    // A different playlist → show the spinner; refreshes of the same playlist
    // (edits, periodic poll, library signal) update silently.
    var loadedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id, reloadTick, signalTick) {
        val silent = id == loadedId && playlist != null
        loadedId = id
        if (!silent) { loading = true; error = null }
        try {
            playlist = repo.playlist(id)
        } catch (t: Throwable) {
            if (!silent) error = t.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        when {
            loading -> Loading()
            error != null -> ErrorState("Could not load playlist", error!!, onRetry = onBack)
            playlist != null -> {
                val p = playlist!!
                val songs = p.entry
                // The real, playable tracks are `entry`; `songCount` can be inflated
                // by "orphans" (songs deleted from the library but still counted by
                // the server). Show and act on the real list.
                val orphanCount = (p.songCount - songs.size).coerceAtLeast(0)
                LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
                    item {
                        DetailHeader(
                            tag = "Playlist",
                            title = p.name,
                            subtitle = p.comment,
                            meta = "${songs.size} song${if (songs.size == 1) "" else "s"} · ${formatDuration(songs.sumOf { it.duration })}",
                            coverArt = p.coverArt,
                            onBack = onBack,
                            trailing = {
                                IconButton(onClick = { managePlaylist(ManagePlaylistRequest(p, onDeleted = onGoHome)) }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Manage playlist", tint = Text3)
                                }
                            }
                        )
                    }
                    if (orphanCount > 0) {
                        item {
                            OrphanBanner(
                                count = orphanCount,
                                onCleanUp = {
                                    scope.launch {
                                        runCatching {
                                            if (songs.isEmpty()) repo.clearPlaylist(id, p.songCount)
                                            else repo.setPlaylistSongs(id, songs.map { it.id })
                                        }
                                            .onSuccess {
                                                container.librarySignals.notifyChanged()
                                                notifier.success("Cleaned up")
                                                reloadTick++
                                            }
                                            .onFailure {
                                                notifier.error("Couldn't clean up: ${it.message}")
                                            }
                                    }
                                }
                            )
                        }
                    }
                    item {
                        DetailActions(
                            onPlay = { onPlay(songs, 0) },
                            onShuffle = { onPlay(songs.shuffled(), 0) },
                            onDownload = if (songs.isNotEmpty()) {
                                {
                                    if (!container.downloadRepository.hasStorageAccess()) {
                                        notifier.error("Grant storage access in Settings → Downloads first.")
                                    } else {
                                        scope.launch {
                                            container.downloadRepository.enqueueAll(songs, wifiOnly)
                                            notifier.info("Downloading ${songs.size} song${if (songs.size == 1) "" else "s"}")
                                        }
                                    }
                                }
                            } else null
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (songs.size > 6) {
                        item {
                            DetailSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = "Search this playlist…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    // Keep each row's true playlist index so removal stays correct even
                    // while filtered; playback uses the visible order.
                    val visible = songs.withIndex().filter { (_, s) ->
                        query.isBlank() ||
                            s.title.contains(query.trim(), ignoreCase = true) ||
                            s.artist?.contains(query.trim(), ignoreCase = true) == true ||
                            s.album?.contains(query.trim(), ignoreCase = true) == true
                    }
                    if (songs.isEmpty()) {
                        item { EmptyState("No tracks", "") }
                    } else if (visible.isEmpty()) {
                        item { EmptyState("No matches", "No tracks match \"$query\".") }
                    } else {
                        val visibleSongs = visible.map { it.value }
                        itemsIndexed(visible, key = { _, iv -> "${iv.value.id}-${iv.index}" }) { pos, iv ->
                            val song = iv.value
                            val trueIndex = iv.index
                            val controller = LocalPlayerController.current
                            SongRow(
                                song = song,
                                onClick = { onPlay(visibleSongs, pos) },
                                actions = rememberSongActions(
                                    song = song,
                                    controller = controller,
                                    onOpenAlbum = null,
                                    playNow = { onPlay(visibleSongs, pos) },
                                    onRemoveFromPlaylist = { indexToRemove = trueIndex }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    val indexBeingRemoved = indexToRemove
    if (indexBeingRemoved != null && playlist != null) {
        val songName = playlist!!.entry.getOrNull(indexBeingRemoved)?.title ?: "song"
        ConfirmDialog(
            title = "Remove from playlist?",
            message = "\"$songName\" will be removed from \"${playlist!!.name}\". The song stays in your library.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                indexToRemove = null // close the dialog immediately; index already captured
                scope.launch {
                    // Rewrite the playlist to its real entries minus the chosen one.
                    // Emptying (removing the last song) needs the index-based path.
                    val current = playlist!!
                    val keep = current.entry
                        .filterIndexed { i, _ -> i != indexBeingRemoved }
                        .map { it.id }
                    runCatching {
                        if (keep.isEmpty()) repo.clearPlaylist(id, current.songCount)
                        else repo.setPlaylistSongs(id, keep)
                    }
                        .onSuccess {
                            container.librarySignals.notifyChanged()
                            notifier.success("Removed")
                            reloadTick++
                        }
                        .onFailure {
                            notifier.error("Couldn't remove: ${it.message}")
                        }
                }
            },
            onDismiss = { indexToRemove = null }
        )
    }
}

/**
 * Shown when a playlist's server count exceeds its real tracks — i.e. some songs
 * were deleted from the library but still linger as counted "orphans". Cleaning up
 * rewrites the playlist to only its real tracks.
 */
@Composable
private fun OrphanBanner(count: Int, onCleanUp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$count track${if (count == 1) "" else "s"} no longer in your library.",
            style = MaterialTheme.typography.bodySmall,
            color = Text3,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onCleanUp) { Text("Clean up", color = Accent) }
    }
}
