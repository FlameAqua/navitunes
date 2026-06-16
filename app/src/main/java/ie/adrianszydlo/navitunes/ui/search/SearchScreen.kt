package ie.adrianszydlo.navitunes.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import ie.adrianszydlo.navitunes.data.api.SearchResult
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.common.AlbumCard
import ie.adrianszydlo.navitunes.ui.common.ArtistRow
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongRow
import androidx.compose.material3.Text
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Error(val message: String) : SearchState
    data class Ready(val result: SearchResult, val query: String) : SearchState
}

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val repo = NavitunesApp.container().libraryRepository
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(250)
            .distinctUntilChanged()
            .collect { q ->
                val trimmed = q.trim()
                if (trimmed.isBlank()) {
                    state = SearchState.Idle
                    return@collect
                }
                state = SearchState.Loading
                state = try {
                    SearchState.Ready(repo.search(trimmed), trimmed)
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
            placeholder = { Text("Songs, albums, artists…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            SearchState.Idle -> EmptyState(
                title = "Search your library",
                body = "Find songs, albums, and artists.",
                glyph = "?"
            )
            SearchState.Loading -> Loading()
            is SearchState.Error -> ErrorState("Search failed", s.message,
                onRetry = {
                    val q = query
                    scope.launch {
                        state = SearchState.Loading
                        state = try {
                            SearchState.Ready(repo.search(q), q)
                        } catch (t: Throwable) {
                            SearchState.Error(t.message ?: "Unknown error")
                        }
                    }
                })
            is SearchState.Ready -> ResultsGrid(s.result, onAlbum, onArtist, onPlay)
        }
    }
}

@Composable
private fun ResultsGrid(
    result: SearchResult,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val empty = result.artist.isEmpty() && result.album.isEmpty() && result.song.isEmpty()
    if (empty) {
        EmptyState("Nothing found", "Try a different query.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (result.artist.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHead("Artists") }
            result.artist.forEach { artist ->
                item(
                    key = "ar-${artist.id}",
                    span = { GridItemSpan(maxLineSpan) }
                ) { ArtistRow(artist, onClick = { onArtist(artist.id) }) }
            }
        }
        if (result.album.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
                SectionHead("Albums")
            }
            result.album.forEach { album ->
                item(key = "al-${album.id}") {
                    AlbumCard(album = album, onClick = { onAlbum(album.id) })
                }
            }
        }
        if (result.song.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
                SectionHead("Songs")
            }
            result.song.forEachIndexed { idx, song ->
                item(
                    key = "sg-${song.id}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    SongRow(song, onClick = { onPlay(result.song, idx) })
                }
            }
        }
    }
}
