package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.LyricLine
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.theme.NavTheme

/** Resolved lyrics for the current song, shared by the player's card + expanded panel. */
sealed interface LyricsData {
    data object Loading : LyricsData
    data class Synced(val lines: List<LyricLine>) : LyricsData
    data class Plain(val text: String) : LyricsData
    data object None : LyricsData
}

/**
 * Fetches lyrics for [song] once (synced preferred, then plain, then none). Called
 * unconditionally with a nullable song so it obeys the rules of hooks; returns
 * [LyricsData.None] when there's nothing playing.
 */
@Composable
fun rememberSongLyrics(song: Song?): LyricsData {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    var state by remember(song?.id) { mutableStateOf<LyricsData>(LyricsData.Loading) }
    LaunchedEffect(song?.id) {
        if (song == null) { state = LyricsData.None; return@LaunchedEffect }
        state = LyricsData.Loading
        // 1) Server-side synced/plain lyrics (OpenSubsonic).
        val structured = repo.lyricsBySongId(song.id)
        val best = structured.firstOrNull { it.synced && it.line.isNotEmpty() }
            ?: structured.firstOrNull { it.line.isNotEmpty() }
        val serverResult = when {
            best != null && best.synced -> LyricsData.Synced(best.line)
            best != null -> LyricsData.Plain(best.line.joinToString("\n") { it.value })
            else -> repo.plainLyrics(song.artist, song.title)?.let { LyricsData.Plain(it) }
        }
        if (serverResult != null) { state = serverResult; return@LaunchedEffect }

        // 2) Fall back to lrclib.net (free online source, synced preferred) by name.
        val lrc = container.lrcLibService.fetch(
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationSec = song.duration.takeIf { it > 0 }
        )
        state = when {
            !lrc.synced.isNullOrEmpty() -> LyricsData.Synced(lrc.synced)
            !lrc.plain.isNullOrBlank() -> LyricsData.Plain(lrc.plain)
            else -> LyricsData.None
        }
    }
    return state
}

/**
 * Fades the top and bottom [fade] region of a scrolling area to transparent, so lyric
 * lines dissolve at the edges instead of being cut off hard. Uses an offscreen layer +
 * a DstIn alpha mask, so it works over any background (the art gradient included).
 */
fun Modifier.verticalFadingEdges(fade: Dp = 56.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (fade.toPx()).coerceAtMost(size.height / 2f)
        if (f <= 0f) return@drawWithContent
        val top = f / size.height
        val bottom = 1f - top
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                top to Color.Black,
                bottom to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

/** The active synced line for [positionMs], or null if not synced / unavailable. */
fun currentSyncedLine(data: LyricsData, positionMs: Long): String? {
    if (data !is LyricsData.Synced) return null
    val idx = data.lines.indexOfLast { (it.start ?: 0) <= positionMs }
    return data.lines.getOrNull(idx)?.value?.takeIf { it.isNotBlank() }
}

/** Full lyrics view — synced (highlighted + auto-scrolling) or plain, using pre-fetched [data]. */
@Composable
fun LyricsPanel(data: LyricsData, positionMs: Long, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (data) {
            LyricsData.Loading -> CircularProgressIndicator(color = color, strokeWidth = 2.dp)
            LyricsData.None -> Text(
                "No lyrics found for this track.",
                style = MaterialTheme.typography.bodyMedium,
                color = NavTheme.colors.text3,
                textAlign = TextAlign.Center
            )
            is LyricsData.Plain -> Text(
                data.text,
                style = MaterialTheme.typography.bodyLarge,
                color = NavTheme.colors.text2,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.titleMedium.fontSize.times(1.6f),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalFadingEdges()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            )
            is LyricsData.Synced -> SyncedLyrics(data.lines, positionMs, color)
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, positionMs: Long, color: Color) {
    val currentIndex = remember(lines, positionMs) {
        lines.indexOfLast { (it.start ?: 0) <= positionMs }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem(currentIndex.coerceAtLeast(0), scrollOffset = -220)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().verticalFadingEdges(72.dp),
        contentPadding = PaddingValues(vertical = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(lines) { i, line ->
            val active = i == currentIndex
            val lineColor by animateColorAsState(
                targetValue = if (active) color else NavTheme.colors.text3,
                animationSpec = tween(250),
                label = "lyricLine"
            )
            Text(
                line.value.ifBlank { "♪" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = lineColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
