package ie.adrianszydlo.navitunes.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.offline.DownloadEntity
import ie.adrianszydlo.navitunes.data.offline.DownloadRepository
import ie.adrianszydlo.navitunes.ui.common.ConfirmDialog
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.formatBytes
import ie.adrianszydlo.navitunes.ui.common.formatDuration
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Danger
import ie.adrianszydlo.navitunes.ui.theme.Success
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val container = NavitunesApp.container()
    val downloads by container.downloadRepository.observeForActiveProfile()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var totalBytes by remember { mutableLongStateOf(0L) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var entryToRemove by remember { mutableStateOf<DownloadEntity?>(null) }

    LaunchedEffect(downloads) {
        totalBytes = container.downloadRepository.totalBytesForActiveProfile()
    }

    // Only completed downloads are playable.
    val playable = remember(downloads) {
        downloads
            .filter { it.status == DownloadRepository.STATUS_COMPLETED }
            .map { it.toSong() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Text2)
            }
            Text(
                "Device Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (downloads.isNotEmpty()) {
                TextButton(onClick = { confirmClearAll = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Danger)
                    Spacer(Modifier.height(0.dp))
                    Text("Clear all", color = Danger)
                }
            }
        }

        if (downloads.isEmpty()) {
            EmptyState(
                title = "No downloads",
                body = "Long-press a song, album or playlist to download for offline."
            )
            return@Column
        }

        Text(
            "${downloads.count { it.status == DownloadRepository.STATUS_COMPLETED }} of ${downloads.size} · ${formatBytes(totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = Text3,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(downloads, key = { "${it.profileId}-${it.songId}" }) { d ->
                DownloadRow(
                    entity = d,
                    onClick = {
                        if (d.status == DownloadRepository.STATUS_COMPLETED) {
                            // Build playable queue from all completed downloads; start at this one.
                            val idx = playable.indexOfFirst { it.id == d.songId }.coerceAtLeast(0)
                            onPlay(playable, idx)
                        }
                    },
                    onRemove = { entryToRemove = d }
                )
            }
        }
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "Clear all offline downloads?",
            message = "Removes ${downloads.size} file${if (downloads.size == 1) "" else "s"} (${formatBytes(totalBytes)}) " +
                "from this device. Songs stay on the server.",
            confirmLabel = "Clear all",
            destructive = true,
            onConfirm = {
                scope.launch { container.downloadRepository.clearAllForActiveProfile() }
            },
            onDismiss = { confirmClearAll = false }
        )
    }

    val pending = entryToRemove
    if (pending != null) {
        ConfirmDialog(
            title = "Remove \"${pending.title}\"?",
            message = "Deletes the downloaded file from this device (${formatBytes(pending.sizeBytes)}). " +
                "The song stays on your server.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                scope.launch { container.downloadRepository.removeDownload(pending.songId) }
            },
            onDismiss = { entryToRemove = null }
        )
    }
}

@Composable
private fun DownloadRow(entity: DownloadEntity, onClick: () -> Unit, onRemove: () -> Unit) {
    val isCompleted = entity.status == DownloadRepository.STATUS_COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isCompleted, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entity.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(entity.artist)
                    if (entity.duration > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(formatDuration(entity.duration))
                    }
                    if (entity.sizeBytes > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(formatBytes(entity.sizeBytes))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Text3
            )
        }
        Text(
            statusLabel(entity.status),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor(entity.status),
            modifier = Modifier.padding(end = 8.dp)
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = Text3)
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    DownloadRepository.STATUS_QUEUED -> "Queued"
    DownloadRepository.STATUS_DOWNLOADING -> "Downloading…"
    DownloadRepository.STATUS_COMPLETED -> "Saved"
    DownloadRepository.STATUS_FAILED -> "Failed"
    else -> status
}

@Composable
private fun statusColor(status: String) = when (status) {
    DownloadRepository.STATUS_COMPLETED -> Success
    DownloadRepository.STATUS_FAILED -> Danger
    DownloadRepository.STATUS_DOWNLOADING -> Accent
    else -> Text3
}

private fun DownloadEntity.toSong(): Song = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    coverArt = coverArt,
    duration = duration
)
