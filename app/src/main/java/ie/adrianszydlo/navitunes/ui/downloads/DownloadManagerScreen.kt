package ie.adrianszydlo.navitunes.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.remote.ServerDownload
import ie.adrianszydlo.navitunes.data.remote.ServerDownloadStatus
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Danger
import ie.adrianszydlo.navitunes.ui.theme.SurfaceElev
import ie.adrianszydlo.navitunes.ui.theme.Success
import ie.adrianszydlo.navitunes.ui.theme.Text3

@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenPlaylist: (String) -> Unit = {},
    onPlay: (List<ie.adrianszydlo.navitunes.data.api.Song>, Int) -> Unit = { _, _ -> }
) {
    val container = NavitunesApp.container()
    val manager = container.downloadManager
    val repo = container.libraryRepository
    val items by manager.items.collectAsState()
    val serverOnline by manager.serverOnline.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val notifier = ie.adrianszydlo.navitunes.ui.common.LocalNotifier.current

    // A finished server download is a Spotify entity; open it by resolving the closest
    // matching library item by name (Spotify ids don't map to Navidrome ids).
    fun openInLibrary(dl: ServerDownload) {
        if (dl.status != ServerDownloadStatus.DONE) return
        scope.launch {
            val opened = runCatching {
                when (dl.type) {
                    "album" -> {
                        val r = repo.search(dl.title).album
                        val hit = r.firstOrNull { it.name.equals(dl.title, true) } ?: r.firstOrNull()
                        hit?.also { onOpenAlbum(it.id) } != null
                    }
                    "artist" -> {
                        val r = repo.search(dl.title).artist
                        val hit = r.firstOrNull { it.name.equals(dl.title, true) } ?: r.firstOrNull()
                        hit?.also { onOpenArtist(it.id) } != null
                    }
                    "track" -> {
                        val r = repo.search(dl.title).song
                        val hit = r.firstOrNull { it.title.equals(dl.title, true) } ?: r.firstOrNull()
                        hit?.also { onPlay(listOf(it), 0) } != null
                    }
                    "playlist" -> {
                        val hit = repo.allPlaylists().firstOrNull { it.name.contains(dl.title, true) }
                        hit?.also { onOpenPlaylist(it.id) } != null
                    }
                    else -> false
                }
            }.getOrDefault(false)
            if (!opened) notifier.info("Couldn't find \"${dl.title}\" in your library yet.")
        }
    }

    // Active first (newest first), then finished (newest first).
    val sorted = items.sortedWith(
        compareByDescending<ServerDownload> { it.isActive }.thenByDescending { it.requestedAt }
    )
    val hasFinished = items.any { !it.isActive }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Server Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (items.any { it.isActive }) {
                TextButton(
                    onClick = { manager.cancelServerJob() },
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("Stop", color = Danger)
                }
            }
            if (hasFinished) {
                TextButton(
                    onClick = { manager.clearFinished() },
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("Clear", color = Accent)
                }
            }
        }

        // Reachability banner — only while there's active work and the server
        // isn't answering, so a queued item's "Waiting for server…" is explained.
        if (serverOnline == false && items.any { it.isActive }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Server unreachable — waiting to retry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger
                )
            }
        }

        if (sorted.isEmpty()) {
            EmptyState(
                title = "No downloads yet",
                body = "Tap the download icon on a Spotify result in Search to fetch it here."
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sorted, key = { it.key }) { dl ->
                DownloadRow(dl, onClick = { openInLibrary(dl) })
            }
        }
    }
}

@Composable
private fun DownloadRow(dl: ServerDownload, onClick: () -> Unit) {
    val container = NavitunesApp.container()
    val done = dl.status == ServerDownloadStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = done, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = dl.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceElev)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                dl.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                statusLine(dl),
                style = MaterialTheme.typography.bodySmall,
                color = when (dl.status) {
                    ServerDownloadStatus.FAILED -> Danger
                    ServerDownloadStatus.DONE -> Success
                    else -> Text3
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        when (dl.status) {
            ServerDownloadStatus.PENDING, ServerDownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Cancel",
                    tint = Text3,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { container.downloadManager.cancel(dl.key) }
                )
            }
            ServerDownloadStatus.DONE ->
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Done", tint = Success, modifier = Modifier.size(20.dp))
            ServerDownloadStatus.FAILED ->
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Retry",
                    tint = Accent,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { container.downloadManager.retry(dl.key) }
                )
        }
    }
}

private fun statusLine(dl: ServerDownload): String = when (dl.status) {
    ServerDownloadStatus.PENDING -> "Waiting…"
    ServerDownloadStatus.DOWNLOADING -> "Downloading…"
    ServerDownloadStatus.DONE -> "Added to your library"
    ServerDownloadStatus.FAILED -> dl.error?.let { "Failed — $it" } ?: "Failed"
}
