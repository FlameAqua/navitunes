package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.SurfaceElev
import ie.adrianszydlo.navitunes.ui.theme.Text3
import ie.adrianszydlo.navitunes.ui.theme.Text4

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        ArtImage(
            coverId = album.coverArt,
            fallback = album.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            cornerRadius = 8.dp
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
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    position: Int? = null,
    isPlaying: Boolean = false,
    onMore: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArt) {
            ArtImage(
                coverId = song.coverArt,
                fallback = song.title,
                modifier = Modifier.size(48.dp),
                cornerRadius = 6.dp,
                requestSize = 100
            )
            Spacer(Modifier.width(12.dp))
        } else if (position != null) {
            Text(
                position.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isPlaying) Accent else Text3,
                modifier = Modifier.width(24.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isPlaying) Accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Text3)
            }
        }
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtImage(
            coverId = playlist.coverArt,
            fallback = playlist.name,
            modifier = Modifier.size(48.dp),
            cornerRadius = 6.dp,
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
}
