package ie.adrianszydlo.navitunes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.common.AlbumCard
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongRow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private sealed interface HomeState {
    data object Loading : HomeState
    data class Error(val message: String) : HomeState
    data class Ready(val newest: List<Album>, val recent: List<Album>, val random: List<Song>) : HomeState
}

@Composable
fun HomeScreen(
    onAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val repo = NavitunesApp.container().libraryRepository
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }

    suspend fun load() {
        state = HomeState.Loading
        try {
            val newest = scope.async { runCatching { repo.albumList("newest", 12) }.getOrDefault(emptyList()) }
            val recent = scope.async { runCatching { repo.albumList("recent", 12) }.getOrDefault(emptyList()) }
            val random = scope.async { runCatching { repo.randomSongs(20) }.getOrDefault(emptyList()) }
            state = HomeState.Ready(newest.await(), recent.await(), random.await())
        } catch (t: Throwable) {
            state = HomeState.Error(t.message ?: "Unknown error")
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Home")
        when (val s = state) {
            HomeState.Loading -> Loading()
            is HomeState.Error -> ErrorState(
                "Could not load home", s.message,
                onRetry = { scope.launch { load() } }
            )
            is HomeState.Ready -> {
                if (s.newest.isEmpty() && s.recent.isEmpty() && s.random.isEmpty()) {
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
                        if (s.newest.isNotEmpty()) {
                            Section("Recently Added", "New arrivals", s.newest, onAlbum)
                        }
                        if (s.recent.isNotEmpty()) {
                            Section("Recently Played", "Last listens", s.recent, onAlbum)
                        }
                        if (s.random.isNotEmpty()) {
                            SectionHead("Shuffle", "Random picks")
                            s.random.take(10).forEachIndexed { i, song ->
                                SongRow(song, onClick = { onPlay(s.random, i) })
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String, albums: List<Album>, onAlbum: (String) -> Unit) {
    SectionHead(title, subtitle)
    LazyRow(
        contentPadding = PaddingValues(end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album = album, onClick = { onAlbum(album.id) }, modifier = Modifier.width(152.dp))
        }
    }
    Spacer(Modifier.height(32.dp))
}
