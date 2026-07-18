package ie.adrianszydlo.navitunes.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
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
import ie.adrianszydlo.navitunes.ui.theme.Danger
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
    val ctx = LocalContext.current
    val wifiOnly by container.preferences.wifiOnly.collectAsState(initial = false)
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var showDeletePlaylist by remember { mutableStateOf(false) }
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
                LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
                    item {
                        DetailHeader(
                            tag = "Playlist",
                            title = p.name,
                            subtitle = p.comment,
                            meta = "${p.songCount.takeIf { it > 0 } ?: songs.size} songs · ${formatDuration(p.duration)}",
                            coverArt = p.coverArt,
                            onBack = onBack,
                            trailing = {
                                IconButton(onClick = { showDeletePlaylist = true }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete playlist", tint = Danger)
                                }
                            }
                        )
                    }
                    item {
                        DetailActions(
                            onPlay = { onPlay(songs, 0) },
                            onShuffle = { onPlay(songs.shuffled(), 0) },
                            onDownload = if (songs.isNotEmpty()) {
                                {
                                    if (!container.downloadRepository.hasStorageAccess()) {
                                        Toast.makeText(ctx, "Grant storage access in Settings → Downloads first.", Toast.LENGTH_LONG).show()
                                    } else {
                                        scope.launch {
                                            container.downloadRepository.enqueueAll(songs, wifiOnly)
                                            Toast.makeText(
                                                ctx,
                                                "Downloading ${songs.size} song${if (songs.size == 1) "" else "s"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            } else null
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (songs.isEmpty()) {
                        item { EmptyState("No tracks", "") }
                    } else {
                        itemsIndexed(songs, key = { idx, s -> "${s.id}-$idx" }) { idx, song ->
                            val controller = LocalPlayerController.current
                            SongRow(
                                song = song,
                                onClick = { onPlay(songs, idx) },
                                actions = rememberSongActions(
                                    song = song,
                                    controller = controller,
                                    onOpenAlbum = null,
                                    playNow = { onPlay(songs, idx) },
                                    onRemoveFromPlaylist = { indexToRemove = idx }
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
                scope.launch {
                    runCatching { repo.removeFromPlaylist(id, indexBeingRemoved) }
                        .onSuccess {
                            container.librarySignals.notifyChanged()
                            Toast.makeText(ctx, "Removed", Toast.LENGTH_SHORT).show()
                            reloadTick++
                        }
                        .onFailure {
                            Toast.makeText(ctx, "Couldn't remove: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
            },
            onDismiss = { indexToRemove = null }
        )
    }

    if (showDeletePlaylist && playlist != null) {
        ConfirmDialog(
            title = "Delete \"${playlist!!.name}\"?",
            message = "This permanently deletes the playlist from your library. The individual songs stay.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                scope.launch {
                    runCatching { repo.deletePlaylist(id) }
                        .onSuccess {
                            container.librarySignals.notifyChanged()
                            Toast.makeText(ctx, "Playlist deleted", Toast.LENGTH_SHORT).show()
                            // The playlist no longer exists — sending the user back to
                            // wherever they came from (e.g. Library) would show a stale
                            // entry until that screen reloads. Jump to Home for clarity.
                            onGoHome()
                        }
                        .onFailure {
                            Toast.makeText(ctx, "Couldn't delete: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
            },
            onDismiss = { showDeletePlaylist = false }
        )
    }
}
