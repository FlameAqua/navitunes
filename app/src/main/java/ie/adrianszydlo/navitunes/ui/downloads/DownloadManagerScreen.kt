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
fun DownloadManagerScreen(onBack: () -> Unit) {
    val container = NavitunesApp.container()
    val manager = container.downloadManager
    val items by manager.items.collectAsState()
    val serverOnline by manager.serverOnline.collectAsState()

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
                "Downloads",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.weight(1f)
            )
            if (items.any { it.isActive }) {
                TextButton(onClick = { manager.cancelServerJob() }) {
                    Text("Stop server", color = Danger)
                }
            }
            if (hasFinished) {
                TextButton(onClick = { manager.clearFinished() }) {
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
            items(sorted, key = { it.key }) { dl -> DownloadRow(dl) }
        }
    }
}

@Composable
private fun DownloadRow(dl: ServerDownload) {
    val container = NavitunesApp.container()
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
