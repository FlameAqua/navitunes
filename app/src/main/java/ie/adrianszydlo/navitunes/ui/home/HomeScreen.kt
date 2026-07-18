package ie.adrianszydlo.navitunes.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.BorderCol
import ie.adrianszydlo.navitunes.ui.theme.Surface
import ie.adrianszydlo.navitunes.ui.theme.Text2
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface HomeState {
    data object Loading : HomeState
    data class Error(val message: String) : HomeState
    data class Ready(
        val recentSongs: List<Song>,
        val playlists: List<Playlist>,
        val newestSongs: List<Song>,
        val shuffle: List<Song>
    ) : HomeState
}

@Composable
fun HomeScreen(
    onAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlaylist: (String) -> Unit
) {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    val scope = rememberCoroutineScope()
    val controller = LocalPlayerController.current
    val ctx = LocalContext.current
    val activeId by container.profileStore.activeId.collectAsState()

    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var showCreate by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    suspend fun load() {
        // Only show the spinner when we have nothing yet; background refreshes
        // (periodic poll, library-changed signal) swap data in silently.
        val existing = state as? HomeState.Ready
        if (existing == null) state = HomeState.Loading
        try {
            // Snapshot recents once per load — collecting the live flow on this screen
            // would re-flow the Column every time you tap a song.
            val recentsSnapshot: List<Song> = activeId?.let { id ->
                runCatching {
                    container.recentlyPlayedStore.observe(id)
                        .first()
                }.getOrDefault(emptyList())
            } ?: emptyList()

            // Newest songs = the first track of the newest N albums (Subsonic has no
            // "recently added songs" endpoint).
            val newestAlbumsDeferred = scope.async { runCatching { repo.albumList("newest", 12) }.getOrDefault(emptyList()) }
            val playlistsDeferred = scope.async { runCatching { repo.allPlaylists() }.getOrDefault(emptyList()) }
            // Reuse the existing shuffle on a silent refresh so it doesn't visibly
            // reshuffle every poll; only fetch new random picks on the first load.
            val random = existing?.shuffle
                ?: scope.async { runCatching { repo.randomSongs(20) }.getOrDefault(emptyList()) }.await()
            val newestAlbums = newestAlbumsDeferred.await()
            val playlists = playlistsDeferred.await()

            // Fetch the first track of the top 8 newest albums in parallel.
            val newestSongs = scope.async {
                newestAlbums.take(8).mapNotNull { album ->
                    runCatching { repo.album(album.id).song.firstOrNull() }.getOrNull()
                }
            }.await()

            state = HomeState.Ready(
                recentSongs = recentsSnapshot,
                playlists = playlists,
                newestSongs = newestSongs,
                shuffle = random
            )
        } catch (t: Throwable) {
            // Keep showing existing content if a background refresh fails.
            if (state !is HomeState.Ready) state = HomeState.Error(t.message ?: "Unknown error")
        }
    }

    val signalTick by container.librarySignals.refresh.collectAsState()
    LaunchedEffect(reloadTick, activeId, signalTick) { load() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Home")
        when (val s = state) {
            HomeState.Loading -> Loading()
            is HomeState.Error -> ErrorState(
                "Could not load home",
                s.message,
                onRetry = { reloadTick++ }
            )
            is HomeState.Ready -> {
                val empty = s.recentSongs.isEmpty() && s.playlists.isEmpty() &&
                    s.newestSongs.isEmpty() && s.shuffle.isEmpty()
                if (empty) {
                    EmptyState(
                        title = "Your library is empty",
                        body = "Add music to Navidrome and rescan."
                    )
                } else {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 200.dp)
                    ) {
                        if (s.recentSongs.isNotEmpty()) {
                            SongSection(
                                title = "Recently Played",
                                subtitle = "Continue Listening",
                                songs = s.recentSongs.take(5),
                                onPlay = { idx -> onPlay(s.recentSongs, idx) },
                                onAlbum = onAlbum,
                                controller = controller
                            )
                        }

                        PlaylistsSection(
                            playlists = s.playlists,
                            onPlaylist = onPlaylist,
                            onCreate = { showCreate = true }
                        )

                        if (s.newestSongs.isNotEmpty()) {
                            SongSection(
                                title = "Recently Added",
                                subtitle = "Freshly added",
                                songs = s.newestSongs,
                                onPlay = { idx -> onPlay(s.newestSongs, idx) },
                                onAlbum = onAlbum,
                                controller = controller
                            )
                        }

                        if (s.shuffle.isNotEmpty()) {
                            SongSection(
                                title = "Shuffle",
                                subtitle = "Random picks",
                                songs = s.shuffle.take(10),
                                onPlay = { idx -> onPlay(s.shuffle, idx) },
                                onAlbum = onAlbum,
                                controller = controller
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onCancel = { showCreate = false },
            onCreate = { name ->
                scope.launch {
                    runCatching { repo.createPlaylist(name) }
                        .onSuccess {
                            container.librarySignals.notifyChanged()
                            Toast.makeText(ctx, "Created \"$name\"", Toast.LENGTH_SHORT).show()
                            reloadTick++
                        }
                        .onFailure {
                            Toast.makeText(ctx, "Couldn't create playlist", Toast.LENGTH_SHORT).show()
                        }
                }
                showCreate = false
            }
        )
    }
}

@Composable
private fun SongSection(
    title: String,
    subtitle: String,
    songs: List<Song>,
    onPlay: (Int) -> Unit,
    onAlbum: (String) -> Unit,
    controller: ie.adrianszydlo.navitunes.playback.PlayerController
) {
    SectionHead(title, subtitle)
    songs.forEachIndexed { i, song ->
        SongRow(
            song = song,
            onClick = { onPlay(i) },
            actions = rememberSongActions(
                song = song,
                controller = controller,
                onOpenAlbum = onAlbum,
                playNow = { onPlay(i) }
            )
        )
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun PlaylistsSection(
    playlists: List<Playlist>,
    onPlaylist: (String) -> Unit,
    onCreate: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "My Playlists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "YOUR SAVED QUEUES",
                style = MaterialTheme.typography.labelMedium,
                color = Text2
            )
        }
        TextButton(onClick = onCreate) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = Accent)
            Spacer(Modifier.size(4.dp))
            Text("New", color = Accent, fontWeight = FontWeight.Medium)
        }
    }

    if (playlists.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, BorderCol, RoundedCornerShape(12.dp))
                .clickable(onClick = onCreate)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Accent)
                Spacer(Modifier.size(6.dp))
                Text(
                    "Create your first playlist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Text2
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        return
    }

    LazyRow(
        contentPadding = PaddingValues(end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(playlists, key = { it.id }) { p ->
            PlaylistCard(p, onClick = { onPlaylist(p.id) })
        }
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = 152.dp, height = 200.dp)
            .clickable(onClick = onClick)
    ) {
        ArtImage(
            coverId = playlist.coverArt,
            fallback = playlist.name,
            modifier = Modifier.size(152.dp),
            cornerRadius = 8.dp,
            requestSize = 300
        )
        Spacer(Modifier.height(10.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${playlist.songCount} song${if (playlist.songCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = Text2
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onCancel: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Playlist name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Create", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
