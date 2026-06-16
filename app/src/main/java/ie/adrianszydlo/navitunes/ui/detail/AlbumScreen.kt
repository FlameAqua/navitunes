package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.formatDuration

@Composable
fun AlbumScreen(
    id: String,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    val scope = rememberCoroutineScope()
    val wifiOnly by container.preferences.wifiOnly.collectAsState(initial = false)
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var album by remember { mutableStateOf<Album?>(null) }

    LaunchedEffect(id) {
        loading = true; error = null
        try {
            album = repo.album(id)
        } catch (t: Throwable) {
            error = t.message ?: "Unknown error"
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
                val songs = a.song
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 200.dp)
                ) {
                    item {
                        DetailHeader(
                            tag = "Album",
                            title = a.name,
                            subtitle = a.artist,
                            meta = "${a.songCount.takeIf { it > 0 } ?: songs.size} songs · ${formatDuration(a.duration)}${if (a.year != null) " · ${a.year}" else ""}",
                            coverArt = a.coverArt,
                            onBack = onBack
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
                                    scope.launch {
                                        container.downloadRepository.enqueueAll(songs, wifiOnly)
                                    }
                                }
                            } else null
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (songs.isEmpty()) {
                        item { EmptyState("No tracks", "") }
                    } else {
                        itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
                            SongRow(
                                song,
                                onClick = { onPlay(songs, idx) },
                                showArt = false,
                                position = idx + 1
                            )
                        }
                    }
                }
            }
        }
    }
}
