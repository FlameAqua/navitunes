package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ie.adrianszydlo.navitunes.playback.PlayerController
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.BorderCol
import ie.adrianszydlo.navitunes.ui.theme.SurfaceElev

@Composable
fun MiniPlayer(
    controller: PlayerController,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit
) {
    val current by controller.currentItem.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val position by controller.position.collectAsStateWithLifecycle()
    val duration by controller.duration.collectAsStateWithLifecycle()
    val song = current ?: return

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElev)
            .border(1.dp, BorderCol, RoundedCornerShape(14.dp))
            .clickable(onClick = onExpand)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtImage(
                coverId = song.coverArt,
                fallback = song.title,
                modifier = Modifier.size(44.dp),
                cornerRadius = 8.dp,
                requestSize = 200
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { controller.prev() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = { controller.togglePlay() }, modifier = Modifier.size(36.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = AccentOn
                    )
                }
            }
            IconButton(onClick = { controller.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }
        // Progress bar at the bottom edge
        val pct = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(pct)
                .height(2.dp)
                .background(Accent)
        )
    }
}
