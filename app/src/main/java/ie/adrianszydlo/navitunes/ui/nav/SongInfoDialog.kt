package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.common.formatBytes
import ie.adrianszydlo.navitunes.ui.common.formatDuration
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3

@Composable
fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    // Start with what we already have, then enrich from getSong.view (path, disc,
    // MBID, play count — fields the list endpoints omit).
    var full by remember(song.id) { mutableStateOf(song) }
    val downloaded = LocalDownloadedIds.current.contains(song.id)

    LaunchedEffect(song.id) {
        NavitunesApp.container().libraryRepository.song(song.id)?.let { full = it }
    }

    val rows: List<Pair<String, String>> = buildList {
        full.artist?.takeIf { it.isNotBlank() }?.let { add("Artist" to it) }
        full.album?.takeIf { it.isNotBlank() }?.let { add("Album" to it) }
        full.track?.let { t ->
            val disc = full.discNumber
            add("Track" to if (disc != null && disc > 0) "Disc $disc · #$t" else "#$t")
        }
        full.year?.let { add("Year" to it.toString()) }
        full.genre?.takeIf { it.isNotBlank() }?.let { add("Genre" to it) }
        if (full.duration > 0) add("Duration" to formatDuration(full.duration))
        formatStr(full.suffix, full.contentType, full.bitRate)?.let { add("Format" to it) }
        full.size?.takeIf { it > 0 }?.let { add("Size" to formatBytes(it)) }
        full.playCount?.takeIf { it > 0 }?.let { add("Play count" to it.toString()) }
        add("Offline" to if (downloaded) "Downloaded" else "Streaming")
        full.musicBrainzId?.takeIf { it.isNotBlank() }?.let { add("MusicBrainz ID" to it) }
        full.path?.takeIf { it.isNotBlank() }?.let { add("Path" to it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(
                    coverId = full.coverArt,
                    fallback = full.title,
                    modifier = Modifier.size(48.dp),
                    cornerRadius = 6.dp,
                    requestSize = 200
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    full.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { (k, v) -> InfoRow(k, v) }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Text3,
            modifier = Modifier.width(96.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (label == "MusicBrainz ID" || label == "Path") FontFamily.Monospace else FontFamily.Default
            ),
            color = Text2,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatStr(suffix: String?, contentType: String?, bitRate: Int?): String? {
    val codec = suffix?.uppercase()?.takeIf { it.isNotBlank() }
        ?: contentType?.substringAfter('/')?.uppercase()?.takeIf { it.isNotBlank() }
    val br = bitRate?.takeIf { it > 0 }?.let { "$it kbps" }
    return listOfNotNull(codec, br).joinToString(" · ").takeIf { it.isNotBlank() }
}
