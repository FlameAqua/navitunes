package ie.adrianszydlo.navitunes.ui.search

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import ie.adrianszydlo.navitunes.ui.common.Loading
import ie.adrianszydlo.navitunes.ui.common.PlaylistRow
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongRow
import ie.adrianszydlo.navitunes.ui.common.rememberSongActions
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.SurfaceElev
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
    val error: String? = null,
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

    val serverDownloads by container.downloadManager.items.collectAsState()
    val downloadByKey = remember(serverDownloads) { serverDownloads.associateBy { it.key } }
    val activeDownloads = serverDownloads.count { it.isActive }

    // Re-run the library search when the library changes (download finished, song
    // removed) so dedup stays correct — e.g. a removed song reappears as a Spotify
    // result, a downloaded one drops out.
    val signalTick by container.librarySignals.refresh.collectAsState()

    var lib by remember { mutableStateOf(LibraryState(loading = true)) }
    var allPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var browseSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    var spotifyConfigured by remember { mutableStateOf(false) }
    var spotifyType by remember { mutableStateOf(SpotifyType.TRACK) }
    var spotifyResults by remember { mutableStateOf<List<SpotifyResult>>(emptyList()) }
    var spotifyLoading by remember { mutableStateOf(false) }

    suspend fun loadBrowse() {
        lib = lib.copy(loading = true, error = null)
        val playlists = runCatching { repo.allPlaylists() }.getOrDefault(emptyList())
        val songs = runCatching { repo.randomSongs(200) }.getOrDefault(emptyList())
        allPlaylists = playlists
        browseSongs = songs
        lib = LibraryState(bundle = SearchBundle(songs = songs, playlists = playlists))
    }

    LaunchedEffect(Unit) { spotifyConfigured = container.spotifyClient.isConfigured() }
    LaunchedEffect(Unit) { loadBrowse() }

    // Library search — keyed on the query text and the library-changed signal.
    LaunchedEffect(Unit) {
        snapshotFlow { query to signalTick }
            .debounce(450.milliseconds)
            .distinctUntilChanged()
            .collect { (q, _) ->
                val trimmed = q.trim()
                if (trimmed.isBlank()) {
                    lib = LibraryState(bundle = SearchBundle(songs = browseSongs, playlists = allPlaylists))
                    return@collect
                }
                lib = lib.copy(loading = true, error = null)
                val server = runCatching { repo.search(trimmed) }.getOrNull()
                val matchedPlaylists = allPlaylists.filter { it.name.contains(trimmed, ignoreCase = true) }
                lib = if (server == null) {
                    LibraryState(
                        loading = false,
                        error = "Couldn't reach your library.",
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
            placeholder = { Text("Songs, albums, artists, playlists…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))

        // First paint while browse loads.
        if (lib.loading && lib.bundle.isEmpty && query.isBlank()) {
            Loading()
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

    val libraryEmptyNoError = !lib.loading && lib.error == null && bundle.isEmpty
    val nothingAtAll = libraryEmptyNoError &&
        (!showSpotify || (!spotifyLoading && deduped.isEmpty()))

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (lib.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(lib.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Text("Retry", color = Accent, modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 4.dp))
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
                Spacer(Modifier.height(8.dp)); SectionHead(if (query.isBlank()) "All songs" else "Songs")
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
                    EmptyState(title = "Library is empty", body = "Add music to your Navidrome server.")
                } else {
                    EmptyState(
                        title = "Nothing found",
                        body = if (spotifyConfigured) "Try a different query."
                        else "Try a different query, or enable Spotify discovery in Settings."
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyDiscoveryHeader(selected: SpotifyType, onSelect: (SpotifyType) -> Unit) {
    Column {
        Spacer(Modifier.height(8.dp))
        SectionHead("Not in your library", "via Spotify")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpotifyType.entries.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = { Text(type.label) },
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
    val ctx = LocalContext.current
    val manager = NavitunesApp.container().downloadManager
    val active = status == ServerDownloadStatus.PENDING || status == ServerDownloadStatus.DOWNLOADING
    val shape = if (result.type == SpotifyType.ARTIST) CircleShape else RoundedCornerShape(6.dp)

    // Only in-flight items are locked. We deliberately don't render a persistent
    // "downloaded" check here: a Spotify result is only visible because it's NOT
    // in the library (dedup drops library matches), so a check would go stale the
    // moment the song is removed. A finished item is simply re-downloadable.
    val clickable = !active

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) Modifier.clickable {
                    Toast.makeText(ctx, "Queued \"${result.title}\" for download.", Toast.LENGTH_SHORT).show()
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
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Download, contentDescription = "Downloads")
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    color = AccentOn,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
