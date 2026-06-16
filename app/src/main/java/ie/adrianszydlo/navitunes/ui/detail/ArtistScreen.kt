package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.ui.common.AlbumCard
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.Loading

@Composable
fun ArtistScreen(
    id: String,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit
) {
    val repo = NavitunesApp.container().libraryRepository
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf<Artist?>(null) }

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

    Column(Modifier.fillMaxSize()) {
        when {
            loading -> Loading()
            error != null -> ErrorState("Could not load artist", error!!, onRetry = onBack)
            artist != null -> {
                val a = artist!!
                val albums = a.album
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
                            meta = "${a.albumCount.takeIf { it > 0 } ?: albums.size} album${if ((a.albumCount.takeIf { it > 0 } ?: albums.size) == 1) "" else "s"}",
                            coverArt = a.coverArt,
                            onBack = onBack
                        )
                    }
                    if (albums.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState("No albums", "")
                        }
                    } else {
                        items(albums, key = { it.id }) { album ->
                            AlbumCard(album = album, onClick = { onAlbum(album.id) })
                        }
                    }
                }
            }
        }
    }
}
