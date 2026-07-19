package ie.adrianszydlo.navitunes.ui.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider as DayNight
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.playback.PlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val WidgetBg = DayNight(Color(0xFF15100D), Color(0xFF15100D))
private val WidgetText = DayNight(Color(0xFFF5EDE3), Color(0xFFF5EDE3))
private val WidgetSub = DayNight(Color(0xFFC9BCB0), Color(0xFFC9BCB0))
private val WidgetAccent = DayNight(Color(0xFFD97757), Color(0xFFD97757))

/** Home-screen widget: current track + transport controls wired to the Media3 session. */
class NavitunesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = NavitunesApp.container().preferences
        val title = prefs.nowPlayingTitle.first()
        val artist = prefs.nowPlayingArtist.first()
        val playing = prefs.nowPlayingPlaying.first()
        provideContent { WidgetUi(title, artist, playing) }
    }
}

class NavitunesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NavitunesWidget()
}

@Composable
private fun WidgetUi(title: String, artist: String, playing: Boolean) {
    Column(
        GlanceModifier.fillMaxSize().background(WidgetBg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (title.isBlank()) "Navitunes" else title,
            style = TextStyle(color = WidgetText, fontWeight = FontWeight.Bold, fontSize = 15.sp),
            maxLines = 1
        )
        Text(
            if (artist.isBlank()) "Nothing playing" else artist,
            style = TextStyle(color = WidgetSub, fontSize = 12.sp),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(10.dp))
        Row(
            GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Ctrl("⏮", actionRunCallback<PrevAction>())
            Spacer(GlanceModifier.width(16.dp))
            Ctrl(if (playing) "⏸" else "▶", actionRunCallback<PlayPauseAction>(), accent = true)
            Spacer(GlanceModifier.width(16.dp))
            Ctrl("⏭", actionRunCallback<NextAction>())
        }
    }
}

@Composable
private fun Ctrl(symbol: String, onClick: Action, accent: Boolean = false) {
    Text(
        symbol,
        style = TextStyle(color = if (accent) WidgetAccent else WidgetText, fontSize = 22.sp),
        modifier = GlanceModifier.clickable(onClick).padding(8.dp)
    )
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        withController(context) { c -> if (c.isPlaying) c.pause() else c.play() }
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        withController(context) { it.seekToNextMediaItem() }
    }
}

class PrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        withController(context) { it.seekToPreviousMediaItem() }
    }
}

/** Builds a short-lived MediaController on the main thread, runs [block], releases it. */
private suspend fun withController(context: Context, block: (MediaController) -> Unit) {
    withContext(Dispatchers.Main) {
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().awaitCompat()
        try { block(controller) } finally { controller.release() }
    }
}

private suspend fun <T> ListenableFuture<T>.awaitCompat(): T = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }, MoreExecutors.directExecutor())
    }
}
