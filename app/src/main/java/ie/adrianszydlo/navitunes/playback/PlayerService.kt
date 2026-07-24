package ie.adrianszydlo.navitunes.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import ie.adrianszydlo.navitunes.AppContainer
import ie.adrianszydlo.navitunes.MainActivity
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Custom session command backing the favourite button in the notification / output switcher. */
const val ACTION_TOGGLE_FAVORITE = "ie.adrianszydlo.navitunes.TOGGLE_FAVORITE"

/**
 * ±10s exposed as **custom** session commands (not player commands). One UI's media player /
 * output switcher renders custom actions but ignores the standard SEEK_BACK/SEEK_FORWARD
 * player commands, so binding them this way is what makes the ±10s buttons actually appear.
 */
const val ACTION_SEEK_BACK = "ie.adrianszydlo.navitunes.SEEK_BACK"
const val ACTION_SEEK_FORWARD = "ie.adrianszydlo.navitunes.SEEK_FORWARD"

/** Session-extras key the service publishes so the in-app UI stays in sync with the notification heart. */
const val EXTRA_IS_FAVORITE = "ie.adrianszydlo.navitunes.IS_FAVORITE"

/** ±10s matches the in-app player's Replay10 / Forward10 controls. */
private const val SEEK_STEP_MS = 10_000L

/**
 * MediaSessionService — owns the single ExoPlayer instance.
 * Hosts the system media notification, lockscreen artwork, BT keys, audio focus.
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    @OptIn(UnstableApi::class)
    private var listener: ExoPlayerListener? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Favourite state of the current track, mirrored so the heart icon can render filled. */
    private var isFavorite = false

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
            // Drives COMMAND_SEEK_BACK / COMMAND_SEEK_FORWARD, which the ±10s notification
            // buttons below are bound to.
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
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
            .setMediaButtonPreferences(buttons())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // The default session commands don't include our custom one, so a
                    // controller (notification, output switcher, Auto) couldn't invoke it.
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .setMediaButtonPreferences(buttons())
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_TOGGLE_FAVORITE -> toggleFavorite()
                        ACTION_SEEK_BACK -> session.player.seekBack()
                        ACTION_SEEK_FORWARD -> session.player.seekForward()
                        else -> return Futures.immediateFuture(
                            SessionResult(SessionError.ERROR_NOT_SUPPORTED, Bundle.EMPTY)
                        )
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle.EMPTY))
                }
            })
            .build()

        listener = ExoPlayerListener(container).also { player.addListener(it) }

        // Re-read the authoritative favourite state per track, the same way PlayerController
        // does — a queued Song's `starred` field is often stale or absent.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshFavorite(mediaItem?.mediaId)
            }
        })
        refreshFavorite(player.currentMediaItem?.mediaId)

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

    /** A live internet-radio stream is loaded (mediaId is `radio:<id>`), not a library song. */
    private fun isLiveNow(): Boolean =
        mediaSession?.player?.currentMediaItem?.mediaId?.startsWith("radio:") == true

    /**
     * The notification / output-switcher button row. Prev/Next go in the **primary** back/forward
     * slots so they always render (even in the compact 3-button strip); ±10s take the secondary
     * slots; favourite is pinned rightmost via overflow. For live radio none of these apply, so we
     * emit no custom buttons and let the system show just play/pause.
     * Rebuilt whenever the favourite state (or radio vs song) changes.
     */
    @OptIn(UnstableApi::class)
    private fun buttons(): ImmutableList<CommandButton> {
        if (isLiveNow()) return ImmutableList.of()
        // ±10s and favourite are **custom** actions (One UI ignores the standard seek player-commands
        // in its media player). Custom actions render left of the transport row in list order, so we
        // order them back10 · fwd10 · favourite to keep the heart at the trailing edge. Prev/Next stay
        // primary so they always survive the compact strip.
        return ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setDisplayName(getString(R.string.previous))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setDisplayName(getString(R.string.next))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                .setSessionCommand(SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY))
                .setDisplayName(getString(R.string.skip_back_10))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_BACK_SECONDARY)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                .setSessionCommand(SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY))
                .setDisplayName(getString(R.string.skip_forward_10))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            CommandButton.Builder(
                if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
            )
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .setDisplayName(getString(if (isFavorite) R.string.unfavorite else R.string.favorite))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
        )
    }

    /** Push a rebuilt button row + the favourite state (session extras) to every connected controller. */
    @OptIn(UnstableApi::class)
    private fun publishButtons() {
        mediaSession?.let { session ->
            session.setMediaButtonPreferences(buttons())
            // The in-app UI reads this so its heart matches the notification's, both directions.
            session.setSessionExtras(Bundle().apply { putBoolean(EXTRA_IS_FAVORITE, isFavorite) })
        }
    }

    private fun refreshFavorite(songId: String?) {
        // Radio streams aren't favouritable; skip the server round-trip.
        if (songId == null || songId.startsWith("radio:")) {
            isFavorite = false
            publishButtons()
            return
        }
        serviceScope.launch {
            val starred = runCatching {
                NavitunesApp.container().libraryRepository.song(songId)?.starred
            }.getOrNull()
            // Guard against a late response for a track we've already skipped past.
            if (mediaSession?.player?.currentMediaItem?.mediaId != songId) return@launch
            isFavorite = !starred.isNullOrBlank()
            publishButtons()
        }
    }

    private fun toggleFavorite() {
        val songId = mediaSession?.player?.currentMediaItem?.mediaId ?: return
        val target = !isFavorite
        // Optimistic: flip the icon straight away, roll back if the server rejects it.
        isFavorite = target
        publishButtons()
        serviceScope.launch {
            val repo = NavitunesApp.container().playbackRepository
            val ok = runCatching {
                if (target) repo.star(songId) else repo.unstar(songId)
            }.isSuccess
            if (!ok && mediaSession?.player?.currentMediaItem?.mediaId == songId) {
                isFavorite = !target
                publishButtons()
            }
        }
    }

    @OptIn(UnstableApi::class)
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
