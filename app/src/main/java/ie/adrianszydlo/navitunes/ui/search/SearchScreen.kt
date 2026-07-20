package ie.adrianszydlo.navitunes.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material3.MaterialTheme
import ie.adrianszydlo.navitunes.R
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.discovery.SpotifyResult
import ie.adrianszydlo.navitunes.data.discovery.SpotifyType
import ie.adrianszydlo.navitunes.data.remote.ServerDownloadStatus
import ie.adrianszydlo.navitunes.ui.common.AlbumCard
import ie.adrianszydlo.navitunes.ui.common.ArtistRow
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.GridSkeleton
import ie.adrianszydlo.navitunes.ui.common.LocalNotifier
import ie.adrianszydlo.navitunes.ui.common.PlaylistRow
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.BorderCol
import ie.adrianszydlo.navitunes.ui.theme.SurfaceElev
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3
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

/** Library-search state (Spotify discovery is tracked separately). */
private data class LibraryState(
    val loading: Boolean = false,
    val errorRes: Int? = null,
    val bundle: SearchBundle = SearchBundle()
)

/** Lowercase + strip everything but letters/digits, for fuzzy title/artist matching. */
private fun normalizeForMatch(s: String): String =
    s.lowercase().filter { it.isLetterOrDigit() }

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenDownloadManager: () -> Unit
) {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val recentSearches by container.preferences.recentSearches.collectAsState(initial = emptyList())

    // Open the tab ready to type: focus the field and raise the keyboard immediately.
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { searchFocus.requestFocus() }
    }

    val serverDownloads by container.downloadManager.items.collectAsState()
    val downloadByKey = remember(serverDownloads) { serverDownloads.associateBy { it.key } }
    val activeDownloads = serverDownloads.count { it.isActive }

    var lib by remember { mutableStateOf(LibraryState(loading = true)) }
    var allPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var browseSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    var spotifyConfigured by remember { mutableStateOf(false) }
    var spotifyType by remember { mutableStateOf(SpotifyType.TRACK) }
    var spotifyResults by remember { mutableStateOf<List<SpotifyResult>>(emptyList()) }
    var spotifyLoading by remember { mutableStateOf(false) }

    suspend fun loadBrowse() {
        lib = lib.copy(loading = true, errorRes = null)
        val playlists = runCatching { repo.allPlaylists() }.getOrDefault(emptyList())
        val songs = runCatching { repo.randomSongs(200) }.getOrDefault(emptyList())
        allPlaylists = playlists
        browseSongs = songs
        lib = LibraryState(bundle = SearchBundle(songs = songs, playlists = playlists))
    }

    LaunchedEffect(Unit) { spotifyConfigured = container.spotifyClient.isConfigured() }
    LaunchedEffect(Unit) { loadBrowse() }

    // Library search — keyed on the query text only. Deliberately NOT tied to the
    // 10s auto-refresh signal: polling search3 (and re-hitting Spotify) while the
    // user idles on results is wasteful and can burn the Spotify rate limit.
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(450.milliseconds)
            .distinctUntilChanged()
            .collect { q ->
                val trimmed = q.trim()
                if (trimmed.isBlank()) {
                    lib = LibraryState(bundle = SearchBundle(songs = browseSongs, playlists = allPlaylists))
                    return@collect
                }
                lib = lib.copy(loading = true, errorRes = null)
                val server = runCatching { repo.search(trimmed) }.getOrNull()
                val matchedPlaylists = allPlaylists.filter { it.name.contains(trimmed, ignoreCase = true) }
                lib = if (server == null) {
                    LibraryState(
                        loading = false,
                        errorRes = R.string.home_unreachable,
                        bundle = SearchBundle(playlists = matchedPlaylists)
                    )
                } else {
                    LibraryState(
                        loading = false,
                        bundle = SearchBundle(
                            songs = server.song,
                            albums = server.album,
                            artists = server.artist,
                            playlists = matchedPlaylists
                        )
                    )
                }
            }
    }

    // Spotify discovery — keyed on query AND the selected type, so switching the
    // selector refetches just that one type (one API call, never three).
    LaunchedEffect(Unit) {
        snapshotFlow { query.trim() to spotifyType }
            .debounce(450.milliseconds)
            .distinctUntilChanged()
            .collect { (q, type) ->
                if (q.isBlank() || !spotifyConfigured) {
                    spotifyResults = emptyList()
                    spotifyLoading = false
                    return@collect
                }
                spotifyLoading = true
                spotifyResults = runCatching {
                    container.spotifyClient.search(q, type)
                }.getOrDefault(emptyList())
                spotifyLoading = false
            }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Search",
            trailing = {
                DownloadQueueButton(count = activeDownloads, onClick = onOpenDownloadManager)
            }
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint_full)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Text3) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceElev,
                unfocusedContainerColor = SurfaceElev,
                focusedBorderColor = Accent,
                unfocusedBorderColor = BorderCol,
                cursorColor = Accent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) scope.launch { container.preferences.addRecentSearch(query.trim()) }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .focusRequester(searchFocus)
        )
        Spacer(Modifier.height(8.dp))

        if (query.isBlank() && recentSearches.isNotEmpty()) {
            RecentSearches(
                recents = recentSearches,
                onPick = { query = it },
                onClear = { scope.launch { container.preferences.clearRecentSearches() } }
            )
        }

        // First paint while browse loads.
        if (lib.loading && lib.bundle.isEmpty && query.isBlank()) {
            GridSkeleton()
            return@Column
        }

        SearchResults(
            query = query.trim(),
            lib = lib,
            spotifyConfigured = spotifyConfigured,
            spotifyType = spotifyType,
            onSpotifyType = { spotifyType = it },
            spotifyResults = spotifyResults,
            spotifyLoading = spotifyLoading,
            downloadStatusFor = { key -> downloadByKey[key]?.status },
            onAlbum = onAlbum,
            onArtist = onArtist,
            onPlaylist = onPlaylist,
            onPlay = onPlay,
            onRetry = { scope.launch { loadBrowse() } }
        )
    }
}

@Composable
private fun SearchResults(
    query: String,
    lib: LibraryState,
    spotifyConfigured: Boolean,
    spotifyType: SpotifyType,
    onSpotifyType: (SpotifyType) -> Unit,
    spotifyResults: List<SpotifyResult>,
    spotifyLoading: Boolean,
    downloadStatusFor: (String) -> ServerDownloadStatus?,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onRetry: () -> Unit
) {
    val controller = LocalPlayerController.current
    val bundle = lib.bundle
    val showSpotify = query.isNotBlank() && spotifyConfigured
    val deduped = remember(spotifyResults, spotifyType, bundle) {
        dedupeSpotify(spotifyResults, spotifyType, bundle)
    }

    val libraryEmptyNoError = !lib.loading && lib.errorRes == null && bundle.isEmpty
    val nothingAtAll = libraryEmptyNoError &&
        (!showSpotify || (!spotifyLoading && deduped.isEmpty()))

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (lib.errorRes != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(lib.errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.retry), color = Accent, modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 4.dp))
                }
            }
        }

        if (bundle.artists.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHead("Artists") }
            bundle.artists.forEach { artist ->
                item(key = "ar-${artist.id}", span = { GridItemSpan(maxLineSpan) }) {
                    ArtistRow(artist, onClick = { onArtist(artist.id) })
                }
            }
        }
        if (bundle.albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp)); SectionHead("Albums")
            }
            bundle.albums.forEach { album ->
                item(key = "al-${album.id}") { AlbumCard(album = album, onClick = { onAlbum(album.id) }) }
            }
        }
        if (bundle.playlists.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp)); SectionHead("Playlists")
            }
            bundle.playlists.forEach { playlist ->
                item(key = "pl-${playlist.id}", span = { GridItemSpan(maxLineSpan) }) {
                    PlaylistRow(playlist, onClick = { onPlaylist(playlist.id) })
                }
            }
        }
        if (bundle.songs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp)); SectionHead(if (query.isBlank()) stringResource(R.string.lib_all_songs) else "Songs")
            }
            bundle.songs.forEachIndexed { idx, song ->
                item(key = "sg-${song.id}-$idx", span = { GridItemSpan(maxLineSpan) }) {
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

        if (showSpotify) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SpotifyDiscoveryHeader(selected = spotifyType, onSelect = onSpotifyType)
            }
            when {
                spotifyLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                    }
                }
                deduped.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No ${spotifyType.label.lowercase()} found on Spotify.",
                        color = Text3,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                else -> deduped.forEach { result ->
                    item(key = "sp-${result.id}", span = { GridItemSpan(maxLineSpan) }) {
                        SpotifyResultRow(
                            result = result,
                            status = downloadStatusFor("${result.type.apiValue}:${result.id}")
                        )
                    }
                }
            }
        }

        if (nothingAtAll) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (query.isBlank()) {
                    EmptyState(title = stringResource(R.string.lib_empty), body = stringResource(R.string.lib_empty_body))
                } else {
                    EmptyState(
                        title = stringResource(R.string.empty_search),
                        body = if (spotifyConfigured) stringResource(R.string.search_try_different)
                        else stringResource(R.string.search_try_different_spotify)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(
    recents: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = Text3, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("RECENT", style = MaterialTheme.typography.labelMedium, color = Text3, modifier = Modifier.weight(1f))
            Text(
                "Clear",
                color = Accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onClear)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recents.forEach { q ->
                Text(
                    q,
                    style = MaterialTheme.typography.bodySmall,
                    color = Text2,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceElev)
                        .clickable { onPick(q) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyDiscoveryHeader(selected: SpotifyType, onSelect: (SpotifyType) -> Unit) {
    Column {
        Spacer(Modifier.height(8.dp))
        SectionHead(stringResource(R.string.not_in_your_library), "via Spotify")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpotifyType.entries.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = { Text(type.label, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent,
                        selectedLabelColor = AccentOn
                    )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SpotifyResultRow(result: SpotifyResult, status: ServerDownloadStatus?) {
    val notifier = LocalNotifier.current
    val manager = NavitunesApp.container().downloadManager
    val active = status == ServerDownloadStatus.PENDING || status == ServerDownloadStatus.DOWNLOADING
    val shape = if (result.type == SpotifyType.ARTIST) CircleShape else RoundedCornerShape(6.dp)

    // Tapping any result queues the whole entity (track/album/artist/playlist) for
    // a server-side download. In-flight items are locked.
    val clickable = !active

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) Modifier.clickable {
                    notifier.info("Queued \"${result.title}\" for download.")
                    manager.enqueue(result)
                } else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = result.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(shape).background(SurfaceElev)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (result.subtitle.isNotBlank()) {
                Text(
                    result.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (active) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                Icons.Outlined.Download,
                contentDescription = "Download",
                tint = if (status == ServerDownloadStatus.FAILED) MaterialTheme.colorScheme.error else Text3,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Download-queue button for the top bar: a download glyph with a small count
 * badge. Drawn inside a fixed box with the badge inset from the corner so it's
 * never clipped by the surrounding layout, and capped at "9+".
 */
@Composable
private fun DownloadQueueButton(count: Int, onClick: () -> Unit) {
    // No circular clip on the outer box — that was cropping the corner badge.
    // Extra padding gives the badge room to sit above/right of the glyph.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Download, contentDescription = "Downloads")
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    color = AccentOn,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

/** Drops Spotify results already present in the library for this query. */
private fun dedupeSpotify(
    results: List<SpotifyResult>,
    type: SpotifyType,
    bundle: SearchBundle
): List<SpotifyResult> = when (type) {
    SpotifyType.TRACK -> {
        val keys = bundle.songs.map { normalizeForMatch(it.title) to normalizeForMatch(it.artist.orEmpty()) }
        results.filterNot { r ->
            val t = normalizeForMatch(r.title)
            val a = normalizeForMatch(r.matchArtist)
            keys.any { (lt, la) ->
                lt == t && la.isNotEmpty() && a.isNotEmpty() && (a.contains(la) || la.contains(a))
            }
        }
    }
    SpotifyType.ALBUM -> {
        val keys = bundle.albums.map { normalizeForMatch(it.name) to normalizeForMatch(it.artist) }
        results.filterNot { r ->
            val t = normalizeForMatch(r.title)
            val a = normalizeForMatch(r.matchArtist)
            keys.any { (ln, la) ->
                ln == t && (la.isEmpty() || a.isEmpty() || a.contains(la) || la.contains(a))
            }
        }
    }
    SpotifyType.ARTIST -> {
        val names = bundle.artists.map { normalizeForMatch(it.name) }
        results.filterNot { normalizeForMatch(it.title) in names }
    }
    SpotifyType.PLAYLIST -> {
        val names = bundle.playlists.map { normalizeForMatch(it.name) }
        results.filterNot { normalizeForMatch(it.title) in names }
    }
}
