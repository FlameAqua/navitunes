package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.ui.theme.Surface
import ie.adrianszydlo.navitunes.ui.theme.SurfaceElev
import ie.adrianszydlo.navitunes.ui.theme.Text4
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val MAX_ART_RETRIES = 2

@Composable
fun ArtImage(
    coverId: String?,
    fallback: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    requestSize: Int = 300,
    /** Used when [coverId] is missing or fails to load (e.g. an artist → an album cover). */
    fallbackCoverId: String? = null,
    /** If set, the placeholder shows this icon instead of a letter (e.g. a radio glyph for streams). */
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val context = LocalContext.current
    val api = NavitunesApp.container().apiClient
    val letter = remember(fallback) {
        fallback.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    // Retry a transient load failure a couple of times, then switch to the fallback
    // cover (if any) before giving up to the letter placeholder. State resets per cover.
    var attempt by remember(coverId, fallbackCoverId) { mutableIntStateOf(0) }
    var usingFallback by remember(coverId, fallbackCoverId) { mutableStateOf(false) }

    val primaryId = coverId?.takeIf { it.isNotBlank() }
    val fallbackId = fallbackCoverId?.takeIf { it.isNotBlank() }
    // Prefer the primary; if it's absent up front, start on the fallback.
    val effectiveId = if (usingFallback) fallbackId else (primaryId ?: fallbackId)
    val url = remember(effectiveId, requestSize) { api.coverUrl(effectiveId, requestSize) }

    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(listOf(SurfaceElev, Surface))),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            // Stable cache key derived from coverId+size — independent of the salted
            // query string Subsonic appends. `attempt` is only a request parameter (not
            // a cache key) so a retry re-executes but a success still caches normally.
            val stableKey = "cover:${effectiveId}:${requestSize}"
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .setParameter("nt_attempt", attempt, null)
                    .memoryCacheKey(stableKey)
                    .diskCacheKey(stableKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = fallback,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    LaunchedEffect(attempt, usingFallback) {
                        when {
                            attempt < MAX_ART_RETRIES -> { delay(1200.milliseconds); attempt++ }
                            !usingFallback && fallbackId != null && primaryId != null -> {
                                usingFallback = true; attempt = 0
                            }
                        }
                    }
                    Fallback(letter, fallbackIcon)
                },
                loading = { Fallback(letter, fallbackIcon) }
            )
        } else {
            Fallback(letter, fallbackIcon)
        }
    }
}

@Composable
private fun Fallback(letter: String, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    if (icon != null) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Text4,
            modifier = Modifier.fillMaxSize(0.42f)
        )
    } else {
        Text(
            text = letter,
            color = Text4,
            fontSize = 38.sp,
            style = MaterialTheme.typography.displayLarge
        )
    }
}
