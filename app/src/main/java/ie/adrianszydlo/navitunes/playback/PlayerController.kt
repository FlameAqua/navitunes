package ie.adrianszydlo.navitunes.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        })
        // Initial snapshot
        _isPlaying.value = c.isPlaying
        _shuffle.value = c.shuffleModeEnabled
        _repeat.value = c.repeatMode
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
    }

    private fun startTicker() {
        scope.launch {
            while (isActive) {
                controller?.let {
                    _position.value = it.currentPosition
                    if (it.duration > 0) _duration.value = it.duration
                }
                delay(500)
            }
        }
    }

    fun play(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        val items = songs.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        _queue.value = songs
        c.setMediaItems(items, safeIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
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
            ?: container.apiClient.streamUrl(id)
        val artworkUri = container.apiClient.coverUrl(coverArt, 512)
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply {
                if (artworkUri != null) {
                    setArtworkUri(android.net.Uri.parse(artworkUri))
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
