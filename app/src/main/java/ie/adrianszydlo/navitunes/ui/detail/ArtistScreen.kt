package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.common.AlbumCard
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.SongRowSkeleton
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.Surface
import ie.adrianszydlo.navitunes.ui.theme.Text2

private enum class ArtistTab { Albums, Songs }

@Composable
fun ArtistScreen(
    id: String,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenDownloadManager: () -> Unit = {}
) {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val notifier = ie.adrianszydlo.navitunes.ui.common.LocalNotifier.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf<Artist?>(null) }
    var tab by remember { mutableStateOf(ArtistTab.Albums) }
    var query by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var showInstall by remember { mutableStateOf(false) }

    // Songs are fetched lazily the first time the Songs tab is opened (getArtist only
    // returns albums, so this expands every album — potentially many requests).
    var songs by remember(id) { mutableStateOf<List<Song>?>(null) }
    var loadingSongs by remember(id) { mutableStateOf(false) }

    LaunchedEffect(id) {
        loading = true; error = null
        try {
            artist = repo.artist(id)
        } catch (t: Throwable) {
            error = t.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(tab) {
        if (tab == ArtistTab.Songs && songs == null && !loadingSongs) {
            loadingSongs = true
            songs = runCatching { repo.artistSongs(id) }.getOrDefault(emptyList())
            loadingSongs = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        when {
            loading -> Loading()
            error != null -> ErrorState("Could not load artist", error!!, onRetry = onBack)
            artist != null -> {
                val a = artist!!
                val albums = a.album.distinctBy { it.id }
                val albumCount = a.albumCount.takeIf { it > 0 } ?: albums.size
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DetailHeader(
                            tag = "Artist",
                            title = a.name,
                            subtitle = null,
                            meta = "$albumCount album${if (albumCount == 1) "" else "s"}",
                            // Artist image: explicit coverArt, else the artist's own id
                            // (Navidrome resolves it), else one of their album covers.
                            coverArt = a.coverArt?.takeIf { it.isNotBlank() } ?: a.id,
                            fallbackCoverArt = albums.firstOrNull { !it.coverArt.isNullOrBlank() }?.coverArt,
                            onBack = onBack,
                            trailing = {
                                InstallFromSpotifyButton(
                                    fetching = fetching,
                                    onClick = { showInstall = true }
                                )
                            }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            ArtistTabRow(current = tab, onSelect = { tab = it; query = "" })
                            Spacer(Modifier.height(8.dp))
                            DetailSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = if (tab == ArtistTab.Albums) "Search albums…" else "Search songs…",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    when (tab) {
                        ArtistTab.Albums -> {
                            val visible = albums.matchingAlbums(query)
                            if (visible.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    EmptyState(if (albums.isEmpty()) "No albums" else "No matches", "")
                                }
                            } else {
                                items(visible, key = { it.id }) { album ->
                                    AlbumCard(album = album, onClick = { onAlbum(album.id) })
                                }
                            }
                        }
                        ArtistTab.Songs -> {
                            val loaded = songs
                            when {
                                loaded == null || loadingSongs ->
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Column { repeat(6) { SongRowSkeleton() } }
                                    }
                                loaded.isEmpty() ->
                                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("No songs", "") }
                                else -> {
                                    val visible = loaded.matching(query)
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            "${loaded.size} song${if (loaded.size == 1) "" else "s"} in your library",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Text2,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    if (visible.isEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("No matches", "") }
                                    } else {
                                        itemsIndexed(
                                            visible,
                                            span = { _, _ -> GridItemSpan(maxLineSpan) },
                                            key = { _, s -> s.id }
                                        ) { idx, song ->
                                            val controller = LocalPlayerController.current
                                            SongRow(
                                                song = song,
                                                onClick = { onPlay(visible, idx) },
                                                actions = rememberSongActions(
                                                    song = song,
                                                    controller = controller,
                                                    onOpenAlbum = { song.albumId?.let(onAlbum) },
                                                    playNow = { onPlay(visible, idx) }
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showInstall) {
                    val a2 = a
                    InstallFromSpotifyDialog(
                        what = "artist",
                        title = a2.name,
                        onConfirm = {
                            showInstall = false
                            scope.launch {
                                if (!container.spotifyClient.isConfigured()) {
                                    notifier.error("Set up Spotify in Settings → Discovery first.")
                                    return@launch
                                }
                                fetching = true
                                val match = spotifyMatch(
                                    container, a2.name, null,
                                    ie.adrianszydlo.navitunes.data.discovery.SpotifyType.ARTIST
                                )
                                fetching = false
                                if (match == null) {
                                    notifier.error("Couldn't find \"${a2.name}\" on Spotify.")
                                } else {
                                    container.downloadManager.enqueue(match)
                                    notifier.info("Installing ${a2.name} — tracks appear as they download.")
                                    onOpenDownloadManager()
                                }
                            }
                        },
                        onDismiss = { showInstall = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistTabRow(current: ArtistTab, onSelect: (ArtistTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ArtistTab.entries.forEach { t ->
            val selected = t == current
            val bg by animateColorAsState(
                targetValue = if (selected) Accent else Surface,
                animationSpec = tween(220),
                label = "artistTabBg"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) AccentOn else Text2,
                animationSpec = tween(220),
                label = "artistTabFg"
            )
            Text(
                if (t == ArtistTab.Albums) "Albums" else "Songs",
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable { onSelect(t) }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
    }
}
