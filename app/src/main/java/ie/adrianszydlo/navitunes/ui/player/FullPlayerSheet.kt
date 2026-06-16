package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.playback.PlayerController
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.common.formatDuration
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.Bg
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    controller: PlayerController,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bg,
        dragHandle = null
    ) {
        FullPlayerContent(controller = controller, onClose = onDismiss)
    }
}

@Composable
private fun FullPlayerContent(controller: PlayerController, onClose: () -> Unit) {
    val current by controller.currentItem.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val position by controller.position.collectAsStateWithLifecycle()
    val duration by controller.duration.collectAsStateWithLifecycle()
    val shuffle by controller.shuffle.collectAsStateWithLifecycle()
    val repeat by controller.repeat.collectAsStateWithLifecycle()
    val starred by controller.starred.collectAsStateWithLifecycle()
    val queue by controller.queue.collectAsStateWithLifecycle()
    val index by controller.currentIndex.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    val song = current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = Text2)
            }
            Text(
                if (showQueue) "Queue" else "Now Playing",
                style = MaterialTheme.typography.labelMedium,
                color = Text3,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { showQueue = !showQueue }) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = Text2)
            }
        }

        if (showQueue) {
            QueueView(queue = queue, currentIndex = index, onPlay = { idx ->
                controller.playFromQueue(idx)
                showQueue = false
            })
            return@Column
        }

        if (song == null) {
            Spacer(Modifier.height(40.dp))
            Text(
                "Nothing playing",
                style = MaterialTheme.typography.titleLarge,
                fontStyle = FontStyle.Italic
            )
            return@Column
        }

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .align(Alignment.CenterHorizontally)
        ) {
            ArtImage(
                coverId = song.coverArt,
                fallback = song.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp,
                requestSize = 600
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            song.title,
            style = MaterialTheme.typography.titleLarge,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Text(
            song.artist.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = Text2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        if (!song.album.isNullOrBlank()) {
            Text(
                song.album,
                style = MaterialTheme.typography.bodySmall,
                color = Text3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        SeekRow(
            position = position,
            duration = duration,
            onSeek = { controller.seekTo(it) }
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
        ) {
            IconButton(onClick = { controller.prev() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = { controller.seekRelative(-10_000L) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10s", tint = Text3)
            }
            IconButton(
                onClick = { controller.togglePlay() },
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onBackground)
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { controller.seekRelative(10_000L) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Text3)
            }
            IconButton(onClick = { controller.next() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally)
        ) {
            IconButton(onClick = { controller.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffle) Accent else Text3
                )
            }
            IconButton(onClick = {
                controller.toggleStarOnCurrent(NavitunesApp.container().playbackRepository) {}
            }) {
                Icon(
                    if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (starred) Accent else Text3
                )
            }
            IconButton(onClick = { controller.cycleRepeat() }) {
                Icon(
                    if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeat != Player.REPEAT_MODE_OFF) Accent else Text3
                )
            }
        }
    }
}

@Composable
private fun SeekRow(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val pct = when {
        dragValue != null -> dragValue!!
        duration > 0 -> (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = pct,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                if (duration > 0) onSeek((pct * duration).toLong())
                dragValue = null
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onBackground,
                activeTrackColor = MaterialTheme.colorScheme.onBackground,
                inactiveTrackColor = Text3.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        val displayPositionSec: Int = if (dragValue != null && duration > 0) {
            (dragValue!! * duration / 1000f).toInt()
        } else {
            (position / 1000L).toInt()
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(displayPositionSec),
                style = MaterialTheme.typography.labelMedium,
                color = Text3
            )
            Text(
                formatDuration((duration / 1000L).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = Text3
            )
        }
    }
}

@Composable
private fun QueueView(
    queue: List<ie.adrianszydlo.navitunes.data.api.Song>,
    currentIndex: Int,
    onPlay: (Int) -> Unit
) {
    if (queue.isEmpty()) {
        Spacer(Modifier.height(40.dp))
        Text(
            "Queue is empty",
            style = MaterialTheme.typography.bodyLarge,
            color = Text2
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(queue, key = { i, s -> "${s.id}-$i" }) { idx, song ->
            ie.adrianszydlo.navitunes.ui.common.SongRow(
                song = song,
                onClick = { onPlay(idx) },
                showArt = false,
                position = idx + 1,
                isPlaying = idx == currentIndex
            )
        }
    }
}
