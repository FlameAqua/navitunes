package ie.adrianszydlo.navitunes.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import ie.adrianszydlo.navitunes.AppContainer
import ie.adrianszydlo.navitunes.MainActivity
import ie.adrianszydlo.navitunes.NavitunesApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MediaSessionService — owns the single ExoPlayer instance.
 * Hosts the system media notification, lockscreen artwork, BT keys, audio focus.
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var listener: ExoPlayerListener? = null

    override fun onCreate() {
        super.onCreate()
        val container = NavitunesApp.container()

        val httpDataSource = OkHttpDataSource.Factory(container.apiClient.okHttp)
            .setUserAgent("Navitunes/1.0")

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSource)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()

        val sessionActivityPi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPi)
            .build()

        listener = ExoPlayerListener(container).also { player.addListener(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        listener?.let { mediaSession?.player?.removeListener(it) }
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}

private class ExoPlayerListener(
    private val container: AppContainer
) : Player.Listener {
    private var lastReportedSongId: String? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val id = mediaItem?.mediaId ?: return
        if (id == lastReportedSongId) return
        lastReportedSongId = id
        container.appScope.launch(Dispatchers.IO) {
            runCatching { container.playbackRepository.scrobble(id, submission = true) }
        }
    }
}
