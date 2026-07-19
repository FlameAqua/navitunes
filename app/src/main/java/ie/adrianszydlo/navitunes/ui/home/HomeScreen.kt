package ie.adrianszydlo.navitunes.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.HomeSkeleton
import ie.adrianszydlo.navitunes.ui.common.SectionCard
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongContextMenu
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.clickableScaled
import ie.adrianszydlo.navitunes.ui.common.combinedClickableScaled
import ie.adrianszydlo.navitunes.ui.common.LocalNotifier
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.BorderCol
import ie.adrianszydlo.navitunes.ui.theme.NavTheme
import ie.adrianszydlo.navitunes.ui.theme.Surface
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    val notifier = LocalNotifier.current
    val activeId by container.profileStore.activeId.collectAsState()
    val profiles by container.profileStore.profiles.collectAsState()
    val activeName = remember(profiles, activeId) { profiles.firstOrNull { it.id == activeId }?.name }

    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var showCreate by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

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

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize()
    ) {
        when (val s = state) {
            HomeState.Loading -> Column(Modifier.fillMaxSize()) {
                GreetingHeader(activeName)
                Spacer(Modifier.height(8.dp))
                HomeSkeleton()
            }
            is HomeState.Error -> Column(Modifier.fillMaxSize()) {
                GreetingHeader(activeName)
                ErrorState("Could not load home", s.message, onRetry = { reloadTick++ })
            }
            is HomeState.Ready -> {
                val empty = s.recentSongs.isEmpty() && s.playlists.isEmpty() &&
                    s.newestSongs.isEmpty() && s.shuffle.isEmpty()
                if (empty) {
                    Column(Modifier.fillMaxSize()) {
                        GreetingHeader(activeName)
                        EmptyState(
                            title = "Your library is empty",
                            body = "Add music to Navidrome and rescan."
                        )
                    }
                } else {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 12 }
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 200.dp)
                        ) {
                            GreetingHeader(activeName)

                            if (s.recentSongs.isNotEmpty()) {
                                SectionCard(Modifier.padding(horizontal = 20.dp)) {
                                    Column(Modifier.padding(vertical = 14.dp)) {
                                        Box(Modifier.padding(horizontal = 16.dp)) {
                                            SectionHead("Recently Played", "Continue Listening")
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        RecentlyPlayedCarousel(
                                            songs = s.recentSongs,
                                            onPlay = { idx -> onPlay(s.recentSongs, idx) },
                                            onAlbum = onAlbum
                                        )
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            SectionCard(Modifier.padding(horizontal = 20.dp)) {
                                Column(Modifier.padding(vertical = 14.dp)) {
                                    PlaylistsSection(
                                        playlists = s.playlists,
                                        onPlaylist = onPlaylist,
                                        onCreate = { showCreate = true }
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))

                            if (s.newestSongs.isNotEmpty()) {
                                CardSongSection(
                                    title = "Recently Added",
                                    subtitle = "Freshly added",
                                    songs = s.newestSongs,
                                    onPlay = { idx -> onPlay(s.newestSongs, idx) },
                                    onAlbum = onAlbum,
                                    controller = controller
                                )
                            }

                            if (s.shuffle.isNotEmpty()) {
                                CardSongSection(
                                    title = "Shuffle",
                                    subtitle = "Random picks",
                                    songs = s.shuffle.take(10),
                                    onPlay = { idx -> onPlay(s.shuffle, idx) },
                                    onAlbum = onAlbum,
                                    controller = controller,
                                    onShuffleAll = { onPlay(s.shuffle.shuffled(), 0) }
                                )
                            }
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
                            notifier.success("Created \"$name\"")
                            reloadTick++
                        }
                        .onFailure {
                            notifier.error("Couldn't create playlist")
                        }
                }
                showCreate = false
            }
        )
    }
}

/** Time-aware greeting hero — the app's front door. */
@Composable
private fun GreetingHeader(name: String?) {
    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            greeting,
            style = MaterialTheme.typography.displayMedium,
            color = NavTheme.colors.textHi,
            textAlign = TextAlign.Center
        )
        Text(
            if (name != null) "Welcome back, $name" else "What do you want to hear?",
            style = MaterialTheme.typography.labelMedium,
            color = Text3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun RecentlyPlayedCarousel(
    songs: List<Song>,
    onPlay: (Int) -> Unit,
    onAlbum: (String) -> Unit
) {
    val controller = LocalPlayerController.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(songs.take(10), key = { _, s -> s.id }) { idx, song ->
            var menuOpen by remember(song.id) { mutableStateOf(false) }
            val actions = rememberSongActions(
                song = song,
                controller = controller,
                onOpenAlbum = onAlbum,
                playNow = { onPlay(idx) }
            )
            Box {
                Column(
                    Modifier
                        .width(140.dp)
                        .combinedClickableScaled(
                            onClick = { onPlay(idx) },
                            onLongClick = { menuOpen = true }
                        )
                ) {
                    ArtImage(
                        coverId = song.coverArt,
                        fallback = song.title,
                        modifier = Modifier.size(140.dp),
                        cornerRadius = 14.dp,
                        requestSize = 300
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SongContextMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    song = song,
                    actions = actions
                )
            }
        }
    }
}

/** A titled section whose song rows live inside a clear card. */
@Composable
private fun CardSongSection(
    title: String,
    subtitle: String,
    songs: List<Song>,
    onPlay: (Int) -> Unit,
    onAlbum: (String) -> Unit,
    controller: ie.adrianszydlo.navitunes.playback.PlayerController,
    onShuffleAll: (() -> Unit)? = null
) {
    SectionCard(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
            Box(Modifier.padding(horizontal = 16.dp)) { SectionHead(title, subtitle) }
            Spacer(Modifier.height(4.dp))
            Column(Modifier.padding(horizontal = 8.dp)) {
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
                if (onShuffleAll != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableScaled(onClick = onShuffleAll)
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle all", color = Accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
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
            .padding(start = 16.dp, end = 8.dp, bottom = 10.dp)
    ) {
        Box(Modifier.weight(1f)) {
            SectionHead("My Playlists")
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
                .padding(horizontal = 16.dp)
                .clickableScaled(onClick = onCreate)
                .background(Surface, RoundedCornerShape(16.dp))
                .border(1.dp, BorderCol, RoundedCornerShape(16.dp))
                .padding(24.dp),
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
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(playlists, key = { it.id }) { p ->
            PlaylistCard(p, onClick = { onPlaylist(p.id) })
        }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = 152.dp, height = 202.dp)
            .clickableScaled(onClick = onClick)
    ) {
        ArtImage(
            coverId = playlist.coverArt,
            fallback = playlist.name,
            modifier = Modifier.size(152.dp),
            cornerRadius = 14.dp,
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
