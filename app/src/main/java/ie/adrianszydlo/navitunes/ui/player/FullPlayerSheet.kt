package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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
import ie.adrianszydlo.navitunes.R
import ie.adrianszydlo.navitunes.ui.common.NowPlayingBars
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
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
            // The queue is a full-screen takeover, so the arrow backs out of it first.
            // Lyrics is only a swap of the media area and has its own toggle in the controls
            // row, so the arrow dismisses the player straight away rather than closing lyrics
            // (which used to need two taps).
            IconButton(onClick = {
                when {
                    showQueue -> showQueue = false
                    else -> onClose()
                }
            }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.close), tint = TextHi)
            }
            Text(
                stringResource(
                    when {
                        isLive -> R.string.live_radio
                        showQueue -> R.string.queue
                        showLyrics -> R.string.lyrics
                        else -> R.string.now_playing
                    }
                ),
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
                        contentDescription = stringResource(R.string.queue),
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
                stringResource(R.string.nothing_playing),
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { showLyrics = false }
                ) {
                    LyricsPanel(
                        data = lyrics,
                        positionMs = position,
                        color = artAccent,
                        modifier = Modifier.fillMaxSize(),
                        onSeek = { controller.seekTo(it) }
                    )
                }
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
                            .clickable { if (!isLive) showLyrics = true }
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
                            requestSize = 600,
                            fallbackIcon = if (isLive) Icons.Filled.Radio else null
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
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous), tint = TextHi)
                }
                IconButton(onClick = { controller.seekRelative(-10_000L) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.back_10s), tint = TextHi)
                }
                PlayPauseButton(playing = playing, onClick = { controller.togglePlay() })
                IconButton(onClick = { controller.seekRelative(10_000L) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Forward10, contentDescription = stringResource(R.string.forward_10s), tint = TextHi)
                }
                IconButton(onClick = { controller.next() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next), tint = TextHi)
                }
            }

            Spacer(Modifier.height(20.dp))

            val haptics = LocalHapticFeedback.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigateChip(
                    song = song,
                    onGoToAlbum = { song.albumId?.takeIf { it.isNotBlank() }?.let { onOpenAlbum(it); onClose() } },
                    onGoToArtist = { song.artistId?.takeIf { it.isNotBlank() }?.let { onOpenArtist(it); onClose() } },
                    controller = controller
                )
                ToggleControl(
                    icon = Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    active = shuffle,
                    onClick = { controller.toggleShuffle() }
                )
                ToggleControl(
                    icon = if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite),
                    active = starred,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.toggleStarOnCurrent {}
                    }
                )
                ToggleControl(
                    icon = if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.repeat),
                    active = repeat != Player.REPEAT_MODE_OFF,
                    onClick = { controller.cycleRepeat() }
                )
                PlayerSettingsChip(
                    speed = speed,
                    onSpeed = { controller.setSpeed(it) },
                    sleepEndMs = sleepEndMs,
                    onSleepStart = { controller.startSleepTimer(it) },
                    onSleepCancel = { controller.cancelSleepTimer() },
                    controller = controller
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
            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
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
        LyricsData.None -> stringResource(R.string.lyrics_none_short)
        LyricsData.Loading -> stringResource(R.string.lyrics)
        is LyricsData.Plain -> stringResource(R.string.lyrics_view)
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
            contentDescription = stringResource(R.string.lyrics),
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

private fun formatSpeed(s: Float) = s.toString().trimEnd('0').trimEnd('.')

@Composable
private fun NavigateChip(
    song: ie.adrianszydlo.navitunes.data.api.Song,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    controller: PlayerController
) {
    var open by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val notifier = ie.adrianszydlo.navitunes.ui.common.LocalNotifier.current
    Box {
        ToggleControl(
            icon = Icons.Outlined.Explore,
            contentDescription = stringResource(R.string.navigate),
            active = false,
            onClick = { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!song.albumId.isNullOrBlank()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.go_to_album)) },
                    leadingIcon = { Icon(Icons.Outlined.Album, contentDescription = null) },
                    onClick = { open = false; onGoToAlbum() }
                )
            }
            if (!song.artistId.isNullOrBlank()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.go_to_artist)) },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    onClick = { open = false; onGoToArtist() }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.start_song_radio)) },
                leadingIcon = { Icon(Icons.Outlined.Podcasts, contentDescription = null) },
                onClick = {
                    open = false
                    scope.launch {
                        val q = ie.adrianszydlo.navitunes.ui.radio.buildSongRadioQueue(
                            NavitunesApp.container().libraryRepository, song
                        )
                        if (q.size <= 1) notifier.error(R.string.song_radio_failed)
                        else {
                            controller.playSongRadio(song, q)
                            notifier.info(R.string.starting_radio_named, song.title)
                        }
                    }
                }
            )
        }
    }
}

/** Rightmost control: opens a small panel for playback speed, sleep timer, skip-silence, volume. */
@Composable
private fun PlayerSettingsChip(
    speed: Float,
    onSpeed: (Float) -> Unit,
    sleepEndMs: Long?,
    onSleepStart: (Int) -> Unit,
    onSleepCancel: () -> Unit,
    controller: PlayerController
) {
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(sleepEndMs) {
        if (sleepEndMs != null) {
            while (true) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000.milliseconds)
            }
        }
    }

    // Box with fixed size matching other controls (44.dp) keeps the icon aligned in the Row.
    // The timer text is positioned with an offset so it doesn't expand the container.
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        ToggleControl(
            icon = Icons.Outlined.Tune,
            contentDescription = stringResource(R.string.player_settings),
            active = speed != 1f,
            onClick = { open = true }
        )
        if (sleepEndMs != null) {
            val remainingMs = sleepEndMs - now
            val remainingMin = (remainingMs + 59_999) / 60_000 // Round up to nearest minute
            Text(
                text = "${remainingMin}m",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 12.dp) // Hangs below the 44dp box
            )
        }
    }

    if (open) {
        PlayerSettingsDialog(
            speed = speed, onSpeed = onSpeed,
            sleepEndMs = sleepEndMs, onSleepStart = onSleepStart, onSleepCancel = onSleepCancel,
            controller = controller,
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun PlayerSettingsDialog(
    speed: Float,
    onSpeed: (Float) -> Unit,
    sleepEndMs: Long?,
    onSleepStart: (Int) -> Unit,
    onSleepCancel: () -> Unit,
    controller: PlayerController,
    onDismiss: () -> Unit
) {
    val container = NavitunesApp.container()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val skipSilence by container.preferences.skipSilence.collectAsStateWithLifecycle(initialValue = false)
    val volume by controller.volume.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndMs) {
        if (sleepEndMs != null) {
            while (true) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000.milliseconds)
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(NavTheme.colors.surfaceElev)
                .padding(20.dp)
        ) {
            Text(stringResource(R.string.player_settings), style = MaterialTheme.typography.titleMedium, color = TextHi)
            Spacer(Modifier.height(16.dp))

            // Volume
            SettingLabel(stringResource(R.string.volume), "${(volume * 100).toInt()}%")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                val interactionSource = remember { MutableInteractionSource() }
                val isDragging by interactionSource.collectIsDraggedAsState()
                var sliderVolume by remember { mutableFloatStateOf(volume) }

                LaunchedEffect(volume) {
                    if (!isDragging) sliderVolume = volume
                }

                Slider(
                    value = sliderVolume,
                    onValueChange = {
                        sliderVolume = it
                        controller.setVolume(it)
                    },
                    interactionSource = interactionSource,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            // Playback speed
            SettingLabel(stringResource(R.string.playback_speed), "${formatSpeed(speed)}×")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = Text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = speed,
                    onValueChange = { onSpeed(it) },
                    valueRange = 0.5f..2f,
                    steps = 5, // 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            // Sleep timer
            val remainingMs = sleepEndMs?.let { it - now }?.coerceAtLeast(0)
            val remainingLabel = remainingMs?.let {
                val totalSec = it / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                " (%d:%02d left)".format(min, sec)
            } ?: ""
            SettingLabel(stringResource(R.string.sleep_timer), if (sleepEndMs != null) "Active$remainingLabel" else stringResource(R.string.sleep_timer_off))
            Spacer(Modifier.height(4.dp))
            var sliderSleepValue by remember(sleepEndMs) {
                mutableFloatStateOf(if (sleepEndMs == null) 0f else ((sleepEndMs - now) / 60_000f).coerceIn(0f, 120f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = Text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = sliderSleepValue,
                    onValueChange = { sliderSleepValue = it },
                    onValueChangeFinished = {
                        val min = sliderSleepValue.toInt()
                        if (min == 0) onSleepCancel() else onSleepStart(min)
                    },
                    valueRange = 0f..120f,
                    steps = 7, // 0, 15, 30, 45, 60, 75, 90, 105, 120
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            // Skip silence
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.skip_silence), style = MaterialTheme.typography.bodyLarge, color = TextHi, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(
                    checked = skipSilence,
                    onCheckedChange = { scope.launch { container.preferences.setSkipSilence(it) } }
                )
            }
        }
    }
}

@Composable
private fun SettingLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextHi, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.labelMedium, color = Accent)
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

/**
 * One queue slot with a reorder-stable identity. The uid is minted per slot (not per song)
 * because the same song can appear in the queue more than once.
 */
private data class QueueRow(
    val uid: String,
    val song: ie.adrianszydlo.navitunes.data.api.Song
)

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
        Text(stringResource(R.string.queue_empty), style = MaterialTheme.typography.bodyLarge, color = Text2)
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (currentIndex >= 0) listState.scrollToItem(currentIndex) }

    // Every row needs a key that survives reordering, otherwise Compose tears the row down
    // and rebuilds it on each move and the drag reads as a snap. A queue can legitimately
    // hold the same song twice, so the id alone isn't unique — we mint a uid per slot when
    // the player's queue changes and then carry it through any local reordering.
    val baseRows = remember(queue) { queue.mapIndexed { i, s -> QueueRow("${s.id}#$i", s) } }

    // While a drag is in flight we reorder this preview copy and leave the real player queue
    // alone; the move is committed once, on drag end. Mutating the player mid-drag was the
    // other half of the snapping bug. Resets to null when the new queue arrives from the
    // player, which is the handoff back to the authoritative order.
    var preview by remember(baseRows) { mutableStateOf<List<QueueRow>?>(null) }
    val rows = preview ?: baseRows

    var draggingUid by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragCenterY by remember { mutableFloatStateOf(Float.NaN) }

    // Re-derive the preview ordering from the current finger offset. Called from the drag
    // callback and again from the auto-scroll loop, where no drag events fire because the
    // finger is parked at the edge and holding still.
    fun settle() {
        val uid = draggingUid ?: return
        val list = preview ?: baseRows
        val from = list.indexOfFirst { it.uid == uid }
        if (from < 0) return
        val cur = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from } ?: return
        val size = cur.size.toFloat().coerceAtLeast(1f)
        // Walk a slot at a time so one fast flick can cross several rows.
        var moved = from
        var off = dragOffset
        while (off > size / 2f && moved < list.lastIndex) { moved++; off -= size }
        while (off < -size / 2f && moved > 0) { moved--; off += size }
        if (moved != from) {
            preview = list.toMutableList().apply { add(moved, removeAt(from)) }
            dragOffset = off
        }
        dragCenterY = cur.offset + size / 2f + dragOffset
    }

    // Holding the row near either edge scrolls the list, so a track can be dragged to any
    // position in a long queue rather than only as far as the viewport reaches.
    LaunchedEffect(draggingUid) {
        if (draggingUid == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val viewport = listState.layoutInfo.viewportSize.height.toFloat()
            val y = dragCenterY
            if (viewport > 0f && !y.isNaN()) {
                val zone = viewport * 0.15f
                val speed = when {
                    y < zone -> -(zone - y) / zone
                    y > viewport - zone -> (y - (viewport - zone)) / zone
                    else -> 0f
                }
                if (speed != 0f) {
                    // Scrolling shifts the row's laid-out position, so fold what was actually
                    // consumed back into the offset to keep the row under the finger.
                    dragOffset += listState.scrollBy(speed.coerceIn(-1f, 1f) * 22f)
                    settle()
                }
            }
        }
    }

    Text(
        stringResource(R.string.queue_reorder_hint),
        style = MaterialTheme.typography.labelSmall,
        color = NavTheme.colors.text3,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(rows, key = { _, r -> r.uid }) { idx, row ->
            val song = row.song
            val dragging = row.uid == draggingUid
            // Indices into the player's queue, which is unchanged while a preview is showing.
            val playerIdx = remember(row.uid, baseRows) { baseRows.indexOfFirst { it.uid == row.uid } }
            val isCurrent = playerIdx == currentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // The dragged row is positioned by hand; everything else animates into
                    // its new slot, which is what makes the reorder read as smooth.
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            dragging -> NavTheme.colors.surfaceHi
                            isCurrent -> Accent.copy(alpha = 0.12f)
                            else -> NavTheme.colors.surfaceElev
                        }
                    )
                    .clickable { if (playerIdx >= 0) onPlay(playerIdx) }
                    .padding(vertical = 6.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle — press and drag (after a long-press) to reorder.
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.queue_reorder),
                    tint = NavTheme.colors.text3,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(row.uid) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingUid = row.uid
                                    dragOrigin = (preview ?: baseRows).indexOfFirst { it.uid == row.uid }
                                    dragOffset = 0f
                                    dragCenterY = Float.NaN
                                },
                                onDragEnd = {
                                    val to = (preview ?: baseRows).indexOfFirst { it.uid == row.uid }
                                    val from = dragOrigin
                                    draggingUid = null
                                    dragOffset = 0f
                                    dragOrigin = -1
                                    dragCenterY = Float.NaN
                                    // Commit exactly one net move; the preview clears when the
                                    // reordered queue comes back from the player.
                                    if (from >= 0 && to >= 0 && from != to) onMove(from, to)
                                    else preview = null
                                },
                                onDragCancel = {
                                    draggingUid = null
                                    dragOffset = 0f
                                    dragOrigin = -1
                                    dragCenterY = Float.NaN
                                    preview = null
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    settle()
                                }
                            )
                        }
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                    if (isCurrent) {
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
                        color = if (isCurrent) Accent else TextHi,
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
                IconButton(onClick = { if (playerIdx >= 0) onRemove(playerIdx) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.remove), tint = NavTheme.colors.text3, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
