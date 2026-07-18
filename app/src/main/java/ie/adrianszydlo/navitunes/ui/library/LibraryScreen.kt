package ie.adrianszydlo.navitunes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.api.Starred
import ie.adrianszydlo.navitunes.ui.common.AlbumCard
import ie.adrianszydlo.navitunes.ui.common.ArtistRow
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.PlaylistRow
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.Surface
import ie.adrianszydlo.navitunes.ui.theme.Text2
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val repo = NavitunesApp.container().libraryRepository
    var tab by remember { mutableStateOf(LibTab.Albums) }
    var state by remember { mutableStateOf<TabState>(TabState.Loading) }
    val scope = rememberCoroutineScope()

    fun load(t: LibTab, silent: Boolean) {
        scope.launch {
            if (!silent) state = TabState.Loading
            val res = when (t) {
                LibTab.Albums -> runCatching { TabState.Albums(repo.albumList("newest", 200)) }
                LibTab.Artists -> runCatching { TabState.Artists(repo.allArtists()) }
                LibTab.Playlists -> runCatching { TabState.Playlists(repo.allPlaylists()) }
                LibTab.Favorites -> runCatching { TabState.Favorites(repo.starred()) }
            }
            // On a silent refresh, keep whatever's on screen if the fetch fails.
            state = res.getOrElse { if (silent) state else TabState.Error(it.message ?: "Unknown error") }
        }
    }

    // Tab change → show the spinner; a same-tab refresh (poll / signal) → silent.
    var loadedTab by remember { mutableStateOf<LibTab?>(null) }
    val signalTick by NavitunesApp.container().librarySignals.refresh.collectAsState()
    LaunchedEffect(tab, signalTick) {
        val silent = tab == loadedTab
        loadedTab = tab
        load(tab, silent)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Library")
        TabRow(current = tab, onSelect = { tab = it })

        when (val s = state) {
            TabState.Loading -> Loading()
            is TabState.Error -> ErrorState("Could not load library", s.message, onRetry = { load(tab, silent = false) })
            is TabState.Albums -> AlbumsGrid(s.list, onAlbum)
            is TabState.Artists -> ArtistsList(s.list, onArtist)
            is TabState.Playlists -> PlaylistsList(s.list, onPlaylist)
            is TabState.Favorites -> FavoritesContent(s.starred, onAlbum, onPlay)
        }
    }
}

@Composable
private fun TabRow(current: LibTab, onSelect: (LibTab) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LibTab.entries.forEach { t ->
            val selected = t == current
            Text(
                t.label,
                color = if (selected) AccentOn else Text2,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Accent else Surface)
                    .clickable { onSelect(t) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun AlbumsGrid(albums: List<Album>, onAlbum: (String) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState("No albums", "Add music and trigger a rescan in Navidrome.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album = album, onClick = { onAlbum(album.id) })
        }
    }
}

@Composable
private fun ArtistsList(artists: List<Artist>, onArtist: (String) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState("No artists", "Your library is empty.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp).let {
        PaddingValues(
            start = it.calculateStartPadding(LayoutDirection.Ltr),
            end = it.calculateEndPadding(LayoutDirection.Ltr),
            top = it.calculateTopPadding(),
            bottom = 200.dp
        )
    }) {
        items(artists, key = { it.id }) { a ->
            ArtistRow(a, onClick = { onArtist(a.id) })
        }
    }
}

@Composable
private fun PlaylistsList(playlists: List<Playlist>, onPlaylist: (String) -> Unit) {
    if (playlists.isEmpty()) {
        EmptyState("No playlists", "Create playlists in Navidrome.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 200.dp)) {
        items(playlists, key = { it.id }) { p ->
            PlaylistRow(p, onClick = { onPlaylist(p.id) })
        }
    }
}

@Composable
private fun FavoritesContent(
    starred: Starred,
    onAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val controller = LocalPlayerController.current
    if (starred.album.isEmpty() && starred.song.isEmpty()) {
        EmptyState("No favorites yet", "Tap the heart on songs to favorite them.")
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
        if (starred.album.isNotEmpty()) {
            item { SectionHead("Albums") }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(240.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(starred.album.take(4), key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onAlbum(album.id) })
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (starred.song.isNotEmpty()) {
            item { SectionHead("Songs") }
            items(starred.song, key = { it.id }) { s ->
                val actions = rememberSongActions(
                    song = s,
                    controller = controller,
                    onOpenAlbum = { s.albumId?.let { onAlbum(it) } },
                    playNow = { onPlay(listOf(s), 0) }
                )
                SongRow(
                    song = s,
                    onClick = { onPlay(starred.song, starred.song.indexOf(s)) },
                    actions = actions
                )
            }
        }
    }
}

private enum class LibTab(val label: String) {
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
    Favorites("Favorites")
}

private sealed interface TabState {
    data object Loading : TabState
    data class Error(val message: String) : TabState
    data class Albums(val list: List<Album>) : TabState
    data class Artists(val list: List<Artist>) : TabState
    data class Playlists(val list: List<Playlist>) : TabState
    data class Favorites(val starred: Starred) : TabState
}
