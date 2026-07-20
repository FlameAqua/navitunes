package ie.adrianszydlo.navitunes.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.repo.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Compose-friendly handle on the MediaSession. Lifecycle: created in the Activity,
 * `bind()` connects to the service; flows update state for the UI.
 */
class PlayerController(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null

    private val _currentItem = MutableStateFlow<Song?>(null)
    val currentItem: StateFlow<Song?> = _currentItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeat: StateFlow<Int> = _repeat.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _starred = MutableStateFlow(false)
    val starred: StateFlow<Boolean> = _starred.asStateFlow()

    /** True while a live internet-radio stream is loaded (vs. a normal library queue). */
    private val _isLiveStream = MutableStateFlow(false)
    val isLiveStream: StateFlow<Boolean> = _isLiveStream.asStateFlow()

    private data class StreamInfo(val id: String, val name: String, val url: String, val art: String?)
    private var currentStream: StreamInfo? = null

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    /** Epoch-ms at which the sleep timer will pause playback, or null if none is set. */
    private val _sleepEndMs = MutableStateFlow<Long?>(null)
    val sleepEndMs: StateFlow<Long?> = _sleepEndMs.asStateFlow()
    private var sleepJob: Job? = null

    fun bind() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlayerService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            controller = future.get()
            attachListener()
            startTicker()
        }, MoreExecutors.directExecutor())
    }

    fun unbind() {
        controller?.release()
        controller = null
        scope.cancel("unbind")
    }

    private fun attachListener() {
        val c = controller ?: return
        c.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateFromMediaItem(mediaItem)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffle.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeat.value = repeatMode
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _speed.value = playbackParameters.speed
            }
        })
        // Initial snapshot
        _isPlaying.value = c.isPlaying
        _shuffle.value = c.shuffleModeEnabled
        _repeat.value = c.repeatMode
        _speed.value = c.playbackParameters.speed
        updateFromMediaItem(c.currentMediaItem)
    }

    private fun updateFromMediaItem(mediaItem: MediaItem?) {
        val c = controller ?: return
        val songs = _queue.value
        val id = mediaItem?.mediaId
        val song = songs.firstOrNull { it.id == id }
        _currentItem.value = song
        _starred.value = !song?.starred.isNullOrBlank()
        _currentIndex.value = c.currentMediaItemIndex
        _duration.value = if (c.duration > 0) c.duration else 0L

        if (song != null) {
            val container = NavitunesApp.container()
            val profileId = container.profileStore.activeId.value ?: return
            scope.launch {
                runCatching { container.recentlyPlayedStore.push(profileId, song) }
            }
            // Refresh the authoritative favorite state from the server — the queued
            // Song's `starred` field can be stale (list endpoints omit it, or it was
            // favorited elsewhere), so the heart reflects reality on re-entry.
            val id = song.id
            scope.launch {
                val fresh = runCatching { container.libraryRepository.song(id)?.starred }.getOrNull()
                if (_currentItem.value?.id == id) _starred.value = !fresh.isNullOrBlank()
            }
        }
    }

    private fun startTicker() {
        scope.launch {
            while (isActive) {
                controller?.let {
                    _position.value = it.currentPosition
                    if (it.duration > 0) _duration.value = it.duration
                }
                delay(500.milliseconds)
            }
        }
    }

    fun play(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        val items = songs.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        _isLiveStream.value = false
        currentStream = null
        _queue.value = songs
        c.setMediaItems(items, safeIndex, 0L)
        c.prepare()
        c.play()
    }

    /**
     * Plays a raw stream URL (an internet radio station) that isn't a library song.
     * Models the station as a single-item queue so the mini/full player still show
     * a title and artwork. Live streams have no duration, so seeking is a no-op.
     */
    fun playStream(id: String, name: String, streamUrl: String, artworkUrl: String? = null) {
        val c = controller ?: return
        _isLiveStream.value = true
        currentStream = StreamInfo(id, name, streamUrl, artworkUrl)
        val station = Song(id = "radio:$id", title = name, artist = "Radio")
        _queue.value = listOf(station)
        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist("Radio")
            .apply { artworkUrl?.let { setArtworkUri(it.toUri()) } }
            .setExtras(Bundle().apply { putString("songId", station.id) })
            .build()
        val item = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(metadata)
            .build()
        c.setMediaItems(listOf(item), 0, 0L)
        c.prepare()
        c.play()
    }

    /** Insert [song] immediately after the currently playing track. */
    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(song), 0)
            return
        }
        val insertAt = c.currentMediaItemIndex + 1
        c.addMediaItem(insertAt, song.toMediaItem())
        _queue.value = _queue.value.toMutableList().also { it.add(insertAt, song) }
        if (!c.isPlaying) c.play()
    }

    /** Append [song] to the end of the queue. */
    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(song), 0)
            return
        }
        c.addMediaItem(song.toMediaItem())
        _queue.value = _queue.value + song
    }

    /** Insert several songs immediately after the current track. */
    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) { play(songs, 0); return }
        val insertAt = c.currentMediaItemIndex + 1
        c.addMediaItems(insertAt, songs.map { it.toMediaItem() })
        _queue.value = _queue.value.toMutableList().also { it.addAll(insertAt, songs) }
        if (!c.isPlaying) c.play()
    }

    /** Append several songs to the end of the queue. */
    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) { play(songs, 0); return }
        c.addMediaItems(songs.map { it.toMediaItem() })
        _queue.value = _queue.value + songs
    }

    /** Reorder the queue: move the entry at [from] to [to]. */
    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from == to) return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) return
        c.moveMediaItem(from, to)
        _queue.value = _queue.value.toMutableList().also {
            val moved = it.removeAt(from)
            it.add(to, moved)
        }
        _currentIndex.value = c.currentMediaItemIndex
    }

    /** Remove the queue entry at [index]. */
    fun removeFromQueueAt(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.removeMediaItem(index)
        _queue.value = _queue.value.toMutableList().also { if (index in it.indices) it.removeAt(index) }
        _currentIndex.value = c.currentMediaItemIndex
        if (c.mediaItemCount == 0) {
            _currentItem.value = null
            _currentIndex.value = -1
            _isPlaying.value = false
        }
    }

    /** Sets playback speed (1.0 = normal). Pitch is preserved by ExoPlayer. */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _speed.value = speed
    }

    /** Pause playback after [minutes] minutes. Replaces any running timer. */
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val durationMs = minutes * 60_000L
        _sleepEndMs.value = System.currentTimeMillis() + durationMs
        sleepJob = scope.launch {
            delay(durationMs.milliseconds)
            controller?.pause()
            _sleepEndMs.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepEndMs.value = null
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            return
        }
        // Resuming a paused live stream from its buffered position would replay stale
        // audio; re-open the stream so playback continues from the live edge instead.
        val stream = currentStream
        if (stream != null) {
            playStream(stream.id, stream.name, stream.url, stream.art)
        } else {
            c.play()
        }
    }

    /** Hard-stop: clears the queue and dismisses the mini-player. */
    fun stop() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        _isLiveStream.value = false
        currentStream = null
        _queue.value = emptyList()
        _currentItem.value = null
        _currentIndex.value = -1
        _isPlaying.value = false
    }

    /** Removes every queue entry matching [songId] (server-side delete cleanup). */
    fun removeFromQueueById(songId: String) {
        val c = controller ?: return
        // Walk backwards so removal-by-index stays stable.
        val toRemove = (0 until c.mediaItemCount).filter { c.getMediaItemAt(it).mediaId == songId }
        if (toRemove.isEmpty()) return
        for (idx in toRemove.reversed()) c.removeMediaItem(idx)
        _queue.value = _queue.value.filterNot { it.id == songId }
        if (c.mediaItemCount == 0) {
            _currentItem.value = null
            _currentIndex.value = -1
            _isPlaying.value = false
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun prev() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    fun seekRelative(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    /** Toggles the star state on the currently playing song. */
    fun toggleStarOnCurrent(repository: PlaybackRepository, onResult: (Boolean) -> Unit) {
        val song = _currentItem.value ?: return
        scope.launch {
            try {
                if (_starred.value) repository.unstar(song.id) else repository.star(song.id)
                _starred.value = !_starred.value
                onResult(_starred.value)
            } catch (_: Throwable) {
                onResult(_starred.value)
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val container = NavitunesApp.container()
        val streamUri = container.offlineResolver.uriFor(this)
            ?.toUri()
            ?: container.apiClient.streamUrl(id).toUri()
        val artworkUri = container.apiClient.coverUrl(coverArt, 512)
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply {
                if (artworkUri != null) {
                    setArtworkUri(artworkUri.toUri())
                }
            }
            .setExtras(Bundle().apply { putString("songId", id) })
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUri)
            .setMediaMetadata(metadata)
            .build()
    }

}
