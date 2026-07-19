package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.nav.LocalDownloadedIds
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.NavTheme
import ie.adrianszydlo.navitunes.ui.theme.Success
import ie.adrianszydlo.navitunes.ui.theme.SurfaceElev
import ie.adrianszydlo.navitunes.ui.theme.Text3
import ie.adrianszydlo.navitunes.ui.theme.Text4

/** Soft drop shadow under art tiles — visible in light, a no-op on the near-black dark bg. */
@Composable
private fun Modifier.artShadow(corner: Dp): Modifier =
    if (NavTheme.colors.isDark) this
    else this.shadow(6.dp, RoundedCornerShape(corner), clip = false)

/**
 * Long-press menu for a whole album/playlist: play next, add to queue, or download.
 * Self-contained via composition locals so any card can host it. [loadSongs] fetches
 * the tracks lazily (only when an action is chosen).
 */
@Composable
private fun MediaActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    loadSongs: suspend () -> List<Song>
) {
    val controller = LocalPlayerController.current
    val notifier = LocalNotifier.current
    val container = NavitunesApp.container()
    val scope = rememberCoroutineScope()

    fun withSongs(action: suspend (List<Song>) -> Unit) {
        onDismiss()
        scope.launch {
            val songs = runCatching { loadSongs() }.getOrDefault(emptyList())
            if (songs.isNotEmpty()) action(songs)
        }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Play next") },
            leadingIcon = { Icon(Icons.Outlined.QueuePlayNext, contentDescription = null) },
            onClick = { withSongs { controller.playNext(it); notifier.info("Playing ${it.size} next") } }
        )
        DropdownMenuItem(
            text = { Text("Add to queue") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null) },
            onClick = { withSongs { controller.addToQueue(it); notifier.info("Added ${it.size} to queue") } }
        )
        DropdownMenuItem(
            text = { Text("Download") },
            leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
            onClick = {
                withSongs { songs ->
                    if (!container.downloadRepository.hasStorageAccess()) {
                        notifier.error("Grant storage access in Settings → Downloads first.")
                    } else {
                        val wifi = container.preferences.wifiOnly.first()
                        container.downloadRepository.enqueueAll(songs, wifi)
                        notifier.info("Downloading ${songs.size} song${if (songs.size == 1) "" else "s"}")
                    }
                }
            }
        )
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = modifier.combinedClickableScaled(
                onClick = onClick,
                onLongClick = { menuOpen = true }
            )
        ) {
            ArtImage(
                coverId = album.coverArt,
                fallback = album.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).artShadow(12.dp),
                cornerRadius = 12.dp
            )
            Spacer(Modifier.height(10.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = Text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (album.songCount > 0) {
            Text(
                "${album.songCount} song${if (album.songCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = Text4,
                maxLines = 1
            )
        }
        }
        MediaActionsMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            loadSongs = { NavitunesApp.container().libraryRepository.album(album.id).song }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    position: Int? = null,
    isPlaying: Boolean = false,
    actions: SongActions? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = actions?.let {
        it.onPlayNext != null || it.onAddToQueue != null || it.onAddToPlaylist != null ||
            it.onDownload != null || it.onFavorite != null || it.onOpenAlbum != null ||
            it.onRemoveDownload != null || it.onRemoveFromPlaylist != null ||
            it.onRemoveFromLibrary != null || it.onShowInfo != null
    } == true

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickableScaled(
                onClick = onClick,
                onLongClick = if (hasMenu) { { menuOpen = true } } else null
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArt) {
            Box(Modifier.size(52.dp)) {
                ArtImage(
                    coverId = song.coverArt,
                    fallback = song.title,
                    modifier = Modifier.fillMaxSize().artShadow(10.dp),
                    cornerRadius = 10.dp,
                    requestSize = 100
                )
                if (isPlaying) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        NowPlayingBars(color = Color.White, modifier = Modifier.size(width = 18.dp, height = 16.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        } else if (position != null) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    NowPlayingBars(color = Accent, modifier = Modifier.size(width = 16.dp, height = 14.dp))
                } else {
                    Text(
                        position.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Text3
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        val downloaded = LocalDownloadedIds.current.contains(song.id)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isPlaying) Accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (downloaded) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.DownloadDone,
                        contentDescription = "Available offline",
                        tint = Success,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            val subtitle = buildString {
                append(song.artist.orEmpty())
                if (!song.album.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(song.album)
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            formatDuration(song.duration),
            style = MaterialTheme.typography.labelMedium,
            color = Text3
        )
        if (hasMenu) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                SongMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    actions = actions,
                    isFavorited = !song.starred.isNullOrBlank()
                )
            }
        }
    }
}

/** Public song context menu — for hosts (e.g. the Recently Played carousel) that
 *  want the same long-press actions without a full [SongRow]. */
@Composable
fun SongContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: Song,
    actions: SongActions
) {
    SongMenu(expanded, onDismiss, actions, isFavorited = !song.starred.isNullOrBlank())
}

@Composable
private fun SongMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: SongActions,
    isFavorited: Boolean
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.onPlay?.let {
            DropdownMenuItem(
                text = { Text("Play now") },
                leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onShowInfo?.let {
            DropdownMenuItem(
                text = { Text("Song info") },
                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onPlayNext?.let {
            DropdownMenuItem(
                text = { Text("Play next") },
                leadingIcon = { Icon(Icons.Outlined.QueuePlayNext, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onAddToQueue?.let {
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onAddToPlaylist?.let {
            DropdownMenuItem(
                text = { Text("Add to playlist…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onDownload?.let {
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onRemoveDownload?.let {
            DropdownMenuItem(
                text = { Text("Remove download") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onFavorite?.let {
            DropdownMenuItem(
                text = { Text(if (isFavorited) "Remove favorite" else "Add to favorites") },
                leadingIcon = { Icon(Icons.Outlined.Favorite, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onOpenAlbum?.let {
            DropdownMenuItem(
                text = { Text("Go to album") },
                leadingIcon = { Icon(Icons.Outlined.Album, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onRemoveFromPlaylist?.let {
            DropdownMenuItem(
                text = { Text("Remove from playlist") },
                leadingIcon = { Icon(Icons.Outlined.PlaylistRemove, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        actions.onRemoveFromLibrary?.let {
            DropdownMenuItem(
                text = {
                    Text(
                        "Remove song from library",
                        color = ie.adrianszydlo.navitunes.ui.theme.Danger
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = ie.adrianszydlo.navitunes.ui.theme.Danger
                    )
                },
                onClick = { onDismiss(); it() }
            )
        }
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableScaled(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .background(SurfaceElev),
            contentAlignment = Alignment.Center
        ) {
            ArtImage(
                coverId = artist.coverArt,
                fallback = artist.name,
                modifier = Modifier.size(48.dp),
                cornerRadius = 24.dp,
                requestSize = 100
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${artist.albumCount} album${if (artist.albumCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = Text3
            )
        }
    }
}

@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickableScaled(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtImage(
                coverId = playlist.coverArt,
                fallback = playlist.name,
                modifier = Modifier.size(48.dp).artShadow(10.dp),
                cornerRadius = 10.dp,
                requestSize = 100
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlist.songCount} song${if (playlist.songCount == 1) "" else "s"} · ${formatDuration(playlist.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Text3
                )
            }
        }
        MediaActionsMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            loadSongs = { NavitunesApp.container().libraryRepository.playlist(playlist.id).entry }
        )
    }
}

/**
 * Compact animated equalizer shown on the currently-playing row — three bars that
 * bounce out of phase. Purely decorative motion, cheap (an infinite transition).
 */
@Composable
fun NowPlayingBars(
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val durations = listOf(560, 420, 680, 500)
    Row(
        modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(barCount) { i ->
            val h by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durations[i % durations.size], easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$i"
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(color)
            )
        }
    }
}
