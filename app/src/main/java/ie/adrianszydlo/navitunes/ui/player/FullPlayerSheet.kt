package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import ie.adrianszydlo.navitunes.ui.common.NowPlayingBars
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.playback.PlayerController
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.common.formatDuration
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Bg
import ie.adrianszydlo.navitunes.ui.theme.NavTheme
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.TextHi

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    controller: PlayerController,
    onDismiss: () -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Transparent container: the moving content carries its own background, so the
        // whole card slides with the finger (revealing what's behind) instead of the
        // content sliding inside a stationary dark surface.
        containerColor = Color.Transparent,
        dragHandle = null,
        // Body drags no longer dismiss; only the top handle does (finger-following).
        sheetGesturesEnabled = false
    ) {
        val dismiss = ie.adrianszydlo.navitunes.ui.common.rememberSheetDismiss(onDismiss)
        FullPlayerContent(
            controller = controller,
            dismiss = dismiss,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist
        )
    }
}

@Composable
private fun FullPlayerContent(
    controller: PlayerController,
    dismiss: ie.adrianszydlo.navitunes.ui.common.SheetDismiss,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit
) {
    val onClose: () -> Unit = { dismiss.close() }
    val current by controller.currentItem.collectAsStateWithLifecycle()
    val playing by controller.isPlaying.collectAsStateWithLifecycle()
    val position by controller.position.collectAsStateWithLifecycle()
    val duration by controller.duration.collectAsStateWithLifecycle()
    val shuffle by controller.shuffle.collectAsStateWithLifecycle()
    val repeat by controller.repeat.collectAsStateWithLifecycle()
    val starred by controller.starred.collectAsStateWithLifecycle()
    val queue by controller.queue.collectAsStateWithLifecycle()
    val index by controller.currentIndex.collectAsStateWithLifecycle()

    val speed by controller.speed.collectAsStateWithLifecycle()
    val sleepEndMs by controller.sleepEndMs.collectAsStateWithLifecycle()
    val isLive by controller.isLiveStream.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val song = current

    // Ambient accent pulled from the current cover art — tints the backdrop + bars.
    val artAccent = rememberArtAccent(song?.coverArt)
    // Fetched once per song; shared by the bottom lyrics card and the expanded panel.
    val lyrics = rememberSongLyrics(current)

    Column(
        Modifier
            .then(dismiss.contentModifier)
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(
                Brush.verticalGradient(
                    0f to artAccent,
                    0.42f to Bg,
                    1f to Bg
                )
            )
            .statusBarsPadding()
    ) {
        // Drag this handle (only) downward — the sheet follows your finger, then settles.
        ie.adrianszydlo.navitunes.ui.common.SheetDragHandle(state = dismiss)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // In the queue/lyrics sub-views the arrow returns to Now Playing; from the
            // main view it closes the player.
            IconButton(onClick = {
                when {
                    showQueue -> showQueue = false
                    showLyrics -> showLyrics = false
                    else -> onClose()
                }
            }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = TextHi)
            }
            Text(
                when {
                    isLive -> "Live Radio"
                    showQueue -> "Queue"
                    showLyrics -> "Lyrics"
                    else -> "Now Playing"
                },
                style = MaterialTheme.typography.labelMedium,
                color = TextHi,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            // Radio has no queue/lyrics — hide the queue toggle so the header stays honest.
            if (!isLive) {
                IconButton(onClick = {
                    showQueue = !showQueue
                    if (showQueue) showLyrics = false
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (showQueue) Accent else TextHi
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        if (showQueue) {
            QueueView(
                queue = queue,
                currentIndex = index,
                onPlay = { idx -> controller.playFromQueue(idx) },   // keep the queue open
                onRemove = { idx -> controller.removeFromQueueAt(idx) },
                onMove = { from, to -> controller.moveQueueItem(from, to) }
            )
            return@Column
        }

        if (song == null) {
            Spacer(Modifier.height(40.dp))
            Text(
                "Nothing playing",
                style = MaterialTheme.typography.titleLarge,
            )
            return@Column
        }

        Spacer(Modifier.height(20.dp))

        // Media area: album art (with flanking visualizer bars) or the lyrics panel.
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (showLyrics) {
                LyricsPanel(
                    data = lyrics,
                    positionMs = position,
                    color = artAccent,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VisualizerBars(
                        isPlaying = playing,
                        color = artAccent,
                        modifier = Modifier
                            .width(36.dp)
                            .height(220.dp)
                            .padding(end = 6.dp),
                        barCount = 5,
                        mirror = false
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .swipeToSkip(
                                onSwipeLeft = { controller.next() },
                                onSwipeRight = { controller.prev() }
                            )
                    ) {
                        ArtImage(
                            coverId = song.coverArt,
                            fallback = song.title,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 14.dp,
                            requestSize = 600
                        )
                    }
                    VisualizerBars(
                        isPlaying = playing,
                        color = artAccent,
                        modifier = Modifier
                            .width(36.dp)
                            .height(220.dp)
                            .padding(start = 6.dp),
                        barCount = 5,
                        mirror = true
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            song.title,
            style = MaterialTheme.typography.titleLarge,
            color = TextHi,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        // Artist / album are tappable (when not a live stream) to jump to those screens.
        val artistId = song.artistId?.takeIf { !isLive && it.isNotBlank() }
        Text(
            song.artist.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (artistId != null) Accent else Text2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (artistId != null) Modifier.clickable { onOpenArtist(artistId); onClose() }
                    else Modifier
                )
        )
        if (!song.album.isNullOrBlank()) {
            val albumId = song.albumId?.takeIf { !isLive && it.isNotBlank() }
            Text(
                song.album,
                style = MaterialTheme.typography.bodySmall,
                color = Text2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (albumId != null) Modifier.clickable { onOpenAlbum(albumId); onClose() }
                        else Modifier
                    )
            )
        }

        Spacer(Modifier.height(28.dp))

        if (isLive) {
            // Live radio: no seeking, speed, queue, or lyrics — just a live badge and
            // play/pause. Pausing then resuming re-tunes to the live edge (see controller).
            LiveBadge()
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PlayPauseButton(playing = playing, onClick = { controller.togglePlay() })
            }
        } else {
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
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = TextHi)
                }
                IconButton(onClick = { controller.seekRelative(-10_000L) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Back 10s", tint = TextHi)
                }
                PlayPauseButton(playing = playing, onClick = { controller.togglePlay() })
                IconButton(onClick = { controller.seekRelative(10_000L) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = TextHi)
                }
                IconButton(onClick = { controller.next() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = TextHi)
                }
            }

            Spacer(Modifier.height(20.dp))

            val haptics = LocalHapticFeedback.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedChip(speed = speed, onSelect = { controller.setSpeed(it) })
                ToggleControl(
                    icon = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    active = shuffle,
                    onClick = { controller.toggleShuffle() }
                )
                ToggleControl(
                    icon = if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    active = starred,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.toggleStarOnCurrent(NavitunesApp.container().playbackRepository) {}
                    }
                )
                ToggleControl(
                    icon = if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    active = repeat != Player.REPEAT_MODE_OFF,
                    onClick = { controller.cycleRepeat() }
                )
                SleepTimerChip(
                    sleepEndMs = sleepEndMs,
                    onStart = { controller.startSleepTimer(it) },
                    onCancel = { controller.cancelSleepTimer() }
                )
            }

            Spacer(Modifier.height(14.dp))

            LyricsCard(
                lyrics = lyrics,
                positionMs = position,
                expanded = showLyrics,
                onClick = {
                    showLyrics = !showLyrics
                    if (showLyrics) showQueue = false
                }
            )
        }
        }
    }
}

/** The circular play/pause button, shared by the normal and live control layouts. */
@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
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
}

/** A small pulsing "LIVE" badge shown in the radio player. */
@Composable
private fun LiveBadge() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(NavTheme.colors.danger)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelMedium,
            color = Text2,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Bottom lyrics card: shows the active synced line (or a prompt), and toggles the
 * expanded lyrics panel when tapped.
 */
@Composable
private fun LyricsCard(
    lyrics: LyricsData,
    positionMs: Long,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val preview = when (lyrics) {
        LyricsData.None -> "No lyrics for this track"
        LyricsData.Loading -> "Lyrics"
        is LyricsData.Plain -> "View lyrics"
        is LyricsData.Synced -> currentSyncedLine(lyrics, positionMs) ?: "♪"
    }
    val colors = NavTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (expanded) Accent.copy(alpha = 0.14f) else colors.surfaceElev)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Lyrics,
            contentDescription = "Lyrics",
            tint = if (expanded) Accent else Text2,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            preview,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (expanded) TextHi else Text2,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).basicMarquee()
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = Text2
        )
    }
}

/** Icon-only playback-speed control (matches the shuffle/favorite/repeat toggles). */
@Composable
private fun SpeedChip(speed: Float, onSelect: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    fun fmt(s: Float) = s.toString().trimEnd('0').trimEnd('.')
    Box {
        ToggleControl(
            icon = Icons.Outlined.Speed,
            contentDescription = "Playback speed",
            active = speed != 1f,
            onClick = { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { s ->
                DropdownMenuItem(
                    text = { Text("${fmt(s)}×", color = if (s == speed) Accent else Color.Unspecified) },
                    onClick = { onSelect(s); open = false }
                )
            }
        }
    }
}

/** Icon-only sleep-timer control. */
@Composable
private fun SleepTimerChip(sleepEndMs: Long?, onStart: (Int) -> Unit, onCancel: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val active = sleepEndMs != null
    Box {
        ToggleControl(
            icon = Icons.Outlined.Bedtime,
            contentDescription = "Sleep timer",
            active = active,
            onClick = { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(15, 30, 45, 60).forEach { min ->
                DropdownMenuItem(
                    text = { Text("$min minutes") },
                    onClick = { onStart(min); open = false }
                )
            }
            if (active) {
                DropdownMenuItem(
                    text = { Text("Turn off", color = Accent) },
                    onClick = { onCancel(); open = false }
                )
            }
        }
    }
}

/**
 * Inactive: bright neutral icon, no fill.
 * Active: filled accent pill — unambiguous to read at a glance.
 */
@Composable
private fun ToggleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) Accent.copy(alpha = 0.18f) else Color.Transparent
    val border = if (active) Accent else Color.Transparent
    val scale by animateFloatAsState(
        targetValue = if (active) 1.16f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "toggleScale"
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .border(width = if (active) 1.dp else 0.dp, color = border, shape = CircleShape)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) Accent else TextHi,
            modifier = Modifier.scale(scale)
        )
    }
}

/**
 * The slider's `onValueChange` fires on tap AND on drag.
 * We capture the value in local state and seek on release — but critically,
 * we use the captured `dragValue` directly inside `onValueChangeFinished`
 * (not a derived `pct` reference) so a quick tap registers as the value
 * the user actually pressed at, not a stale recompose snapshot.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeekRow(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val livePct = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val sliderValue = dragValue ?: livePct

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Slider(
            value = sliderValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                val captured = dragValue
                dragValue = null
                if (captured != null && duration > 0) {
                    onSeek((captured * duration).toLong())
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = TextHi,
                activeTrackColor = TextHi,
                inactiveTrackColor = Text2.copy(alpha = 0.25f)
            ),
            // Custom thin track (no stop-indicator dot) + a small thumb.
            track = { state ->
                // valueRange defaults to 0f..1f here, so value is already the fraction.
                val fraction = state.value.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Text2.copy(alpha = 0.22f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(TextHi)
                    )
                }
            },
            thumb = {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(TextHi)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        val displayPositionSec: Int = if (dragValue != null && duration > 0) {
            (sliderValue * duration / 1000f).toInt()
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
                color = Text2
            )
            Text(
                formatDuration((duration / 1000L).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = Text2
            )
        }
    }
}

@Composable
private fun QueueView(
    queue: List<ie.adrianszydlo.navitunes.data.api.Song>,
    currentIndex: Int,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    if (queue.isEmpty()) {
        Spacer(Modifier.height(40.dp))
        Text("Queue is empty", style = MaterialTheme.typography.bodyLarge, color = Text2)
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (currentIndex >= 0) listState.scrollToItem(currentIndex) }

    // Drag-to-reorder state: which row is picked up, and its running finger offset.
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Text(
        "Hold a track to reorder",
        style = MaterialTheme.typography.labelSmall,
        color = NavTheme.colors.text3,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        // Key by identity+index so a moved row keeps its composition during the drag.
        itemsIndexed(queue, key = { i, s -> "${s.id}-$i" }) { idx, song ->
            val dragging = idx == draggingIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            dragging -> NavTheme.colors.surfaceHi
                            idx == currentIndex -> Accent.copy(alpha = 0.12f)
                            else -> NavTheme.colors.surfaceElev
                        }
                    )
                    .clickable { onPlay(idx) }
                    .padding(vertical = 6.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle — press and drag (after a long-press) to reorder.
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Reorder",
                    tint = NavTheme.colors.text3,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(queue.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingIndex = idx; dragOffset = 0f },
                                onDragEnd = { draggingIndex = -1; dragOffset = 0f },
                                onDragCancel = { draggingIndex = -1; dragOffset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val from = draggingIndex
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    dragOffset += amount.y
                                    val rows = listState.layoutInfo.visibleItemsInfo
                                    val cur = rows.firstOrNull { it.index == from } ?: return@detectDragGesturesAfterLongPress
                                    val size = cur.size.toFloat().coerceAtLeast(1f)
                                    if (dragOffset > size / 2f && from < queue.lastIndex) {
                                        onMove(from, from + 1); draggingIndex = from + 1; dragOffset -= size
                                    } else if (dragOffset < -size / 2f && from > 0) {
                                        onMove(from, from - 1); draggingIndex = from - 1; dragOffset += size
                                    }
                                }
                            )
                        }
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                    if (idx == currentIndex) {
                        NowPlayingBars(color = Accent, modifier = Modifier.size(width = 16.dp, height = 14.dp))
                    } else {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelMedium, color = Text2)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (idx == currentIndex) Accent else TextHi,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (!song.artist.isNullOrBlank()) {
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Text2,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(formatDuration(song.duration), style = MaterialTheme.typography.labelMedium, color = Text2)
                IconButton(onClick = { onRemove(idx) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = NavTheme.colors.text3, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
