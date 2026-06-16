package ie.adrianszydlo.navitunes.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private data class SearchBundle(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

private sealed interface SearchState {
    data object Loading : SearchState
    data class Error(val message: String) : SearchState
    data class Ready(val bundle: SearchBundle, val query: String) : SearchState
}

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val repo = NavitunesApp.container().libraryRepository
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Loading) }

    // All playlists are cheap to fetch once; we filter them client-side because
    // search3.view doesn't return playlists.
    var allPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    // Browse-mode pool of songs shown when the query is empty. Fetched once.
    var browseSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    suspend fun loadBrowse() {
        state = SearchState.Loading
        try {
            val playlists = runCatching { repo.allPlaylists() }.getOrDefault(emptyList())
            val songs = runCatching { repo.randomSongs(200) }.getOrDefault(emptyList())
            allPlaylists = playlists
            browseSongs = songs
            state = SearchState.Ready(
                bundle = SearchBundle(songs = songs, playlists = playlists),
                query = ""
            )
        } catch (t: Throwable) {
            state = SearchState.Error(t.message ?: "Unknown error")
        }
    }

    LaunchedEffect(Unit) { loadBrowse() }

    // Drive the query-driven search.
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(250.milliseconds)
            .distinctUntilChanged()
            .collect { q ->
                val trimmed = q.trim()
                if (trimmed.isBlank()) {
                    // Restore the browse view from cached pools.
                    state = SearchState.Ready(
                        bundle = SearchBundle(songs = browseSongs, playlists = allPlaylists),
                        query = ""
                    )
                    return@collect
                }
                state = SearchState.Loading
                state = try {
                    val server = repo.search(trimmed)
                    val matchedPlaylists = allPlaylists.filter {
                        it.name.contains(trimmed, ignoreCase = true)
                    }
                    SearchState.Ready(
                        bundle = SearchBundle(
                            songs = server.song,
                            albums = server.album,
                            artists = server.artist,
                            playlists = matchedPlaylists
                        ),
                        query = trimmed
                    )
                } catch (t: Throwable) {
                    SearchState.Error(t.message ?: "Unknown error")
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Search")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Songs, albums, artists, playlists…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            SearchState.Loading -> Loading()
            is SearchState.Error -> ErrorState(
                title = "Search failed",
                body = s.message,
                onRetry = { scope.launch { loadBrowse() } }
            )
            is SearchState.Ready -> ResultsGrid(
                bundle = s.bundle,
                query = s.query,
                onAlbum = onAlbum,
                onArtist = onArtist,
                onPlaylist = onPlaylist,
                onPlay = onPlay
            )
        }
    }
}

@Composable
private fun ResultsGrid(
    bundle: SearchBundle,
    query: String,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val controller = LocalPlayerController.current
    if (bundle.isEmpty) {
        if (query.isBlank()) {
            EmptyState(title = "Library is empty", body = "Add music to your Navidrome server.")
        } else {
            EmptyState(title = "Nothing found", body = "Try a different query.")
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (bundle.artists.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHead("Artists") }
            bundle.artists.forEach { artist ->
                item(
                    key = "ar-${artist.id}",
                    span = { GridItemSpan(maxLineSpan) }
                ) { ArtistRow(artist, onClick = { onArtist(artist.id) }) }
            }
        }
        if (bundle.albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
                SectionHead("Albums")
            }
            bundle.albums.forEach { album ->
                item(key = "al-${album.id}") {
                    AlbumCard(album = album, onClick = { onAlbum(album.id) })
                }
            }
        }
        if (bundle.playlists.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
                SectionHead("Playlists")
            }
            bundle.playlists.forEach { playlist ->
                item(
                    key = "pl-${playlist.id}",
                    span = { GridItemSpan(maxLineSpan) }
                ) { PlaylistRow(playlist, onClick = { onPlaylist(playlist.id) }) }
            }
        }
        if (bundle.songs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
                SectionHead(if (query.isBlank()) "All songs" else "Songs")
            }
            bundle.songs.forEachIndexed { idx, song ->
                item(
                    key = "sg-${song.id}-$idx",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    SongRow(
                        song = song,
                        onClick = { onPlay(bundle.songs, idx) },
                        actions = rememberSongActions(
                            song = song,
                            controller = controller,
                            onOpenAlbum = onAlbum,
                            playNow = { onPlay(bundle.songs, idx) }
                        )
                    )
                }
            }
        }
    }
}
