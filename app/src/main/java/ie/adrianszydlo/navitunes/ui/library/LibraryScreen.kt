package ie.adrianszydlo.navitunes.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.GenreEntry
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.api.Starred
import ie.adrianszydlo.navitunes.ui.common.AlbumCard
import ie.adrianszydlo.navitunes.ui.common.AlphabetScrollbar
import ie.adrianszydlo.navitunes.ui.common.ArtistRow
import ie.adrianszydlo.navitunes.ui.common.buildLetterIndex
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.ErrorState
import ie.adrianszydlo.navitunes.ui.common.GridSkeleton
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.PlaylistRow
import ie.adrianszydlo.navitunes.ui.common.SongRowSkeleton
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.Bg
import ie.adrianszydlo.navitunes.ui.theme.Surface
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3
import ie.adrianszydlo.navitunes.ui.theme.TextHi
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    var refreshing by remember { mutableStateOf(false) }
    var songSheet by remember { mutableStateOf<GenreSongRequest?>(null) }
    var genreView by remember { mutableStateOf(GenreView.Categories) }
    val scope = rememberCoroutineScope()

    suspend fun loadNow(t: LibTab, silent: Boolean) {
        if (!silent) state = TabState.Loading
        val res = when (t) {
            LibTab.Albums -> runCatching { TabState.Albums(repo.albumList("alphabeticalByName", 500)) }
            LibTab.Artists -> runCatching { TabState.Artists(repo.allArtists()) }
            LibTab.Playlists -> runCatching { TabState.Playlists(repo.allPlaylists()) }
            LibTab.Genres -> runCatching { TabState.Genres(repo.genres()) }
            LibTab.Favorites -> runCatching { TabState.Favorites(repo.starred()) }
        }
        // On a silent refresh, keep whatever's on screen if the fetch fails.
        state = res.getOrElse { if (silent) state else TabState.Error(it.message ?: "Unknown error") }
    }

    fun load(t: LibTab, silent: Boolean) {
        scope.launch { loadNow(t, silent) }
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
        ScreenTopBar(
            title = "Library",
            trailing = {
                IconButton(onClick = { tab = LibTab.Favorites }) {
                    Icon(
                        if (tab == LibTab.Favorites) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorites",
                        tint = if (tab == LibTab.Favorites) Accent else Text3
                    )
                }
            }
        )
        TabRow(current = tab, onSelect = { tab = it })

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    // Keep the indicator up long enough to read as a refresh even when the
                    // fetch returns almost instantly (cached / fast network).
                    val start = System.currentTimeMillis()
                    loadNow(tab, silent = true)
                    val elapsed = System.currentTimeMillis() - start
                    if (elapsed < 600) kotlinx.coroutines.delay(600 - elapsed)
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val s = state) {
                TabState.Loading -> when (tab) {
                    LibTab.Albums, LibTab.Favorites -> GridSkeleton()
                    LibTab.Artists, LibTab.Playlists, LibTab.Genres -> Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        repeat(8) { SongRowSkeleton() }
                    }
                }
                is TabState.Error -> ErrorState("Could not load library", s.message, onRetry = { load(tab, silent = false) })
                is TabState.Albums -> AlbumsGrid(s.list, onAlbum)
                is TabState.Artists -> ArtistsList(s.list, onArtist)
                is TabState.Playlists -> PlaylistsList(s.list, onPlaylist)
                is TabState.Genres -> GenresList(
                    genres = s.list,
                    view = genreView,
                    onViewChange = { genreView = it },
                    onPickGenre = { g ->
                        songSheet = GenreSongRequest(g) { repo.songsByGenre(g) }
                    },
                    onPickCategory = { cat ->
                        songSheet = GenreSongRequest(cat.name) { repo.songsByGenres(cat.genres) }
                    }
                )
                is TabState.Favorites -> FavoritesContent(s.starred, onAlbum, onPlay)
            }
        }
    }

    songSheet?.let { req ->
        GenreSongsSheet(
            request = req,
            onAlbum = onAlbum,
            onPlay = onPlay,
            onDismiss = { songSheet = null }
        )
    }
}

/** A request to open the song sheet: a title (genre or category) + a lazy loader. */
private class GenreSongRequest(
    val title: String,
    val loader: suspend () -> List<Song>
)

private enum class GenreView(val label: String) { Categories("Categories"), All("All genres") }

@Composable
private fun TabRow(current: LibTab, onSelect: (LibTab) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Favorites lives in the header (heart icon), so it's omitted from the row.
        LibTab.entries.filter { it != LibTab.Favorites }.forEach { t ->
            val selected = t == current
            val bg by animateColorAsState(
                targetValue = if (selected) Accent else Surface,
                animationSpec = tween(220),
                label = "tabBg"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) AccentOn else Text2,
                animationSpec = tween(220),
                label = "tabFg"
            )
            Text(
                t.label,
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

@Composable
private fun AlbumsGrid(albums: List<Album>, onAlbum: (String) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState("No albums", "Add music and trigger a rescan in Navidrome.")
        return
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val letterIndex = remember(albums) { buildLetterIndex(albums.map { it.name }) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album = album, onClick = { onAlbum(album.id) })
            }
        }
        AlphabetScrollbar(
            activeLetters = letterIndex.keys,
            onSelect = { c -> letterIndex[c]?.let { scope.launch { gridState.scrollToItem(it) } } },
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun ArtistsList(artists: List<Artist>, onArtist: (String) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState("No artists", "Your library is empty.")
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letterIndex = remember(artists) { buildLetterIndex(artists.map { it.name }) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 28.dp, top = 4.dp, bottom = 200.dp)
        ) {
            items(artists, key = { it.id }) { a ->
                ArtistRow(a, onClick = { onArtist(a.id) })
            }
        }
        AlphabetScrollbar(
            activeLetters = letterIndex.keys,
            onSelect = { c -> letterIndex[c]?.let { scope.launch { listState.scrollToItem(it) } } },
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp)
        )
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

@Composable
private fun GenresList(
    genres: List<GenreEntry>,
    view: GenreView,
    onViewChange: (GenreView) -> Unit,
    onPickGenre: (String) -> Unit,
    onPickCategory: (GenreCategories.Category) -> Unit
) {
    val categories = remember(genres) { GenreCategories.categorize(genres) }

    Column(Modifier.fillMaxSize()) {
        // Categories | All genres toggle. (Metadata re-fetch lives in Settings → Maintenance.)
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GenreView.entries.forEach { v ->
                val selected = v == view
                val bg by animateColorAsState(if (selected) Accent else Surface, tween(220), label = "genreViewBg")
                val fg by animateColorAsState(if (selected) AccentOn else Text2, tween(220), label = "genreViewFg")
                Text(
                    v.label,
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .clickable { onViewChange(v) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (genres.isEmpty()) {
            Text(
                "No genre tags found yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = Text3,
                modifier = Modifier.padding(20.dp)
            )
            return
        }

        when (view) {
            GenreView.Categories -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 200.dp)
            ) {
                items(categories, key = { it.name }) { cat ->
                    // No track total here: songs with multiple genres get counted once per
                    // genre, so a summed total would overstate and mismatch the opened list.
                    GenreRow(
                        title = cat.name,
                        count = null,
                        subtitle = if (cat.name == GenreCategories.UNCATEGORIZED)
                            "Untagged or generic \"Music\" tags"
                        else "${cat.genres.size} genre${if (cat.genres.size == 1) "" else "s"}",
                        onClick = { onPickCategory(cat) }
                    )
                }
            }
            GenreView.All -> {
                val sorted = remember(genres) { genres.sortedBy { it.value.lowercase() } }
                val letterIndex = remember(sorted) { buildLetterIndex(sorted.map { it.value }) }
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 20.dp, end = 28.dp, top = 2.dp, bottom = 200.dp)
                    ) {
                        items(sorted, key = { it.value }) { g ->
                            val display = if (g.value.equals("Music", ignoreCase = true))
                                "Music (untagged)" else g.value
                            GenreRow(
                                title = display,
                                count = g.songCount,
                                subtitle = null,
                                onClick = { onPickGenre(g.value) }
                            )
                        }
                    }
                    AlphabetScrollbar(
                        activeLetters = letterIndex.keys,
                        onSelect = { c -> letterIndex[c]?.let { scope.launch { listState.scrollToItem(it) } } },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreRow(title: String, count: Int?, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Text3)
            }
        }
        if (count != null) {
            Text(
                "$count song${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = Text2
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GenreSongsSheet(
    request: GenreSongRequest,
    onAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val controller = LocalPlayerController.current
    var songs by remember(request) { mutableStateOf<List<Song>?>(null) }
    LaunchedEffect(request) { songs = runCatching { request.loader() }.getOrNull() ?: emptyList() }

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        dragHandle = null,
        sheetGesturesEnabled = false
    ) {
        val dismiss = ie.adrianszydlo.navitunes.ui.common.rememberSheetDismiss(onDismiss)
        // The card carries its own background so it slides as one with the drag.
        Column(
            Modifier
                .then(dismiss.contentModifier)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg)
        ) {
        ie.adrianszydlo.navitunes.ui.common.SheetDragHandle(state = dismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 0.dp, bottom = 16.dp)
        ) {
            Text(request.title, style = MaterialTheme.typography.displayMedium, color = TextHi, maxLines = 1)
            val list = songs
            Text(
                if (list == null) "Loading…" else "${list.size} song${if (list.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = Text3
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { list?.takeIf { it.isNotEmpty() }?.let { onPlay(it, 0); onDismiss() } },
                    enabled = !list.isNullOrEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentOn),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Play", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { list?.takeIf { it.isNotEmpty() }?.let { onPlay(it.shuffled(), 0); onDismiss() } },
                    enabled = !list.isNullOrEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Shuffle")
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                list == null -> Loading(Modifier.height(160.dp))
                list.isEmpty() -> Text(
                    "No songs found for this genre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Text3,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                else -> LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    itemsIndexed(list, key = { _, s -> s.id }) { idx, s ->
                        SongRow(
                            song = s,
                            onClick = { onPlay(list, idx); onDismiss() },
                            actions = rememberSongActions(
                                song = s,
                                controller = controller,
                                onOpenAlbum = { s.albumId?.let { onAlbum(it) } },
                                playNow = { onPlay(list, idx); onDismiss() }
                            )
                        )
                    }
                }
            }
        }
        }
    }
}

private enum class LibTab(val label: String) {
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
    Genres("Genres"),
    Favorites("Favorites")
}

private sealed interface TabState {
    data object Loading : TabState
    data class Error(val message: String) : TabState
    data class Albums(val list: List<Album>) : TabState
    data class Artists(val list: List<Artist>) : TabState
    data class Playlists(val list: List<Playlist>) : TabState
    data class Genres(val list: List<GenreEntry>) : TabState
    data class Favorites(val starred: Starred) : TabState
}
