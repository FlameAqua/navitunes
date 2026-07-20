package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.discovery.SpotifyType
import ie.adrianszydlo.navitunes.ui.common.ConfirmDialog
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.formatDuration
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController

@Composable
fun AlbumScreen(
    id: String,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenDownloadManager: () -> Unit = {}
) {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    val scope = rememberCoroutineScope()
    val notifier = ie.adrianszydlo.navitunes.ui.common.LocalNotifier.current
    val wifiOnly by container.preferences.wifiOnly.collectAsState(initial = false)
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var album by remember { mutableStateOf<Album?>(null) }
    var query by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var pendingInstall by remember { mutableStateOf<ie.adrianszydlo.navitunes.data.discovery.SpotifyResult?>(null) }

    // A different album → show the spinner; a background refresh of the same
    // album (periodic poll / library signal) updates silently.
    var loadedId by remember { mutableStateOf<String?>(null) }
    val signalTick by container.librarySignals.refresh.collectAsState()
    LaunchedEffect(id, signalTick) {
        val silent = id == loadedId && album != null
        loadedId = id
        if (!silent) { loading = true; error = null }
        try {
            album = repo.album(id)
        } catch (t: Throwable) {
            if (!silent) error = t.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        when {
            loading -> Loading()
            error != null -> ErrorState("Could not load album", error!!, onRetry = onBack)
            album != null -> {
                val a = album!!
                // Dedup by id defensively (a server hiccup can repeat an entry) and
                // report the real track count from the list we actually show.
                val songs = a.song.distinctBy { it.id }
                val visible = songs.matching(query)
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 200.dp)
                ) {
                    item {
                        DetailHeader(
                            tag = "Album",
                            title = a.name,
                            subtitle = a.artist,
                            meta = "${songs.size} song${if (songs.size == 1) "" else "s"} · ${formatDuration(a.duration)}${if (a.year != null) " · ${a.year}" else ""}",
                            coverArt = a.coverArt,
                            onBack = onBack,
                            trailing = {
                                InstallFromSpotifyButton(
                                    fetching = fetching,
                                    onClick = {
                                        // Resolve on Spotify *first* so the prompt can say
                                        // whether it's a single or a full album.
                                        scope.launch {
                                            if (!container.spotifyClient.isConfigured()) {
                                                notifier.error("Set up Spotify in Settings → Discovery first.")
                                                return@launch
                                            }
                                            fetching = true
                                            val m = spotifyMatch(container, a.name, a.artist, SpotifyType.ALBUM)
                                            fetching = false
                                            if (m == null) notifier.error("Couldn't find \"${a.name}\" on Spotify.")
                                            else pendingInstall = m
                                        }
                                    }
                                )
                            }
                        )
                    }
                    item {
                        DetailActions(
                            onPlay = { onPlay(songs, 0) },
                            onShuffle = {
                                val shuffled = songs.shuffled()
                                onPlay(shuffled, 0)
                            },
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
                    if (songs.size > 3) {
                        item {
                            DetailSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = "Search this album…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    if (songs.isEmpty()) {
                        item { EmptyState("No tracks", "") }
                    } else if (visible.isEmpty()) {
                        item { EmptyState("No matches", "No tracks match \"$query\".") }
                    } else {
                        itemsIndexed(visible, key = { _, s -> s.id }) { idx, song ->
                            val controller = LocalPlayerController.current
                            SongRow(
                                song = song,
                                onClick = { onPlay(visible, idx) },
                                showArt = false,
                                // Sequential position in the list, not the track's own
                                // (possibly bogus) metadata number.
                                position = idx + 1,
                                actions = rememberSongActions(
                                    song = song,
                                    controller = controller,
                                    onOpenAlbum = null,
                                    playNow = { onPlay(visible, idx) }
                                )
                            )
                        }
                    }
                }

                pendingInstall?.let { m ->
                    val isSingle = m.albumType.equals("single", ignoreCase = true)
                    val what = if (isSingle) "single" else "album"
                    // If the library already has a track by this name, re-installing just
                    // duplicates it — warn instead of silently spawning another copy.
                    val alreadyHave = songs.any { it.title.equals(m.title, ignoreCase = true) }
                    ConfirmDialog(
                        title = "Install this $what?",
                        message = buildString {
                            append("Spotify matched this as a $what: \"${m.title}\"")
                            if (m.subtitle.isNotBlank()) append(" — ${m.subtitle}")
                            append(".\n\nYour server will download it in the background")
                            if (!isSingle) append(", including any tracks you don't already have")
                            append(".")
                            if (alreadyHave) {
                                append("\n\nYou already have this track — installing again will " +
                                    "create a duplicate. You probably don't need to.")
                            }
                        },
                        confirmLabel = "Install",
                        destructive = alreadyHave,
                        onConfirm = {
                            val match = m
                            pendingInstall = null
                            container.downloadManager.enqueue(match)
                            notifier.info("Installing \"${match.title}\" — check Server Downloads.")
                            onOpenDownloadManager()
                        },
                        onDismiss = { pendingInstall = null }
                    )
                }
            }
        }
    }
}
