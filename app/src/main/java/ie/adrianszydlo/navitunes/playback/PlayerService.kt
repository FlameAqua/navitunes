package ie.adrianszydlo.navitunes.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.glance.appwidget.updateAll
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import ie.adrianszydlo.navitunes.AppContainer
import ie.adrianszydlo.navitunes.MainActivity
import ie.adrianszydlo.navitunes.NavitunesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * MediaSessionService — owns the single ExoPlayer instance.
 * Hosts the system media notification, lockscreen artwork, BT keys, audio focus.
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var listener: ExoPlayerListener? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val container = NavitunesApp.container()

        val httpDataSource = OkHttpDataSource.Factory(container.apiClient.okHttp)
            .setUserAgent("Navitunes/1.0")

        // DefaultDataSource fans out per-scheme: HTTP/S goes to OkHttp,
        // file:// goes to FileDataSource, content:// goes to ContentDataSource.
        // Without this, offline file URIs can't be opened and tracks read 0 bytes.
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSource)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

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

        // Keep the home-screen widget in sync with playback.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = pushWidget(player)
            override fun onIsPlayingChanged(isPlaying: Boolean) = pushWidget(player)
        })

        // Apply the skip-silence preference live (trims silent gaps between/within tracks).
        serviceScope.launch {
            container.preferences.skipSilence.collect { enabled ->
                player.skipSilenceEnabled = enabled
            }
        }
    }

    private fun pushWidget(player: Player) {
        val md = player.currentMediaItem?.mediaMetadata
        val title = md?.title?.toString().orEmpty()
        val artist = md?.artist?.toString().orEmpty()
        val playing = player.isPlaying
        serviceScope.launch {
            NavitunesApp.container().preferences.setNowPlaying(title, artist, playing)
            runCatching {
                ie.adrianszydlo.navitunes.ui.widget.NavitunesWidget().updateAll(applicationContext)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
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
