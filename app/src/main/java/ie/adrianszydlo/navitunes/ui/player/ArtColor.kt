package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.theme.NavTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts an ambient accent colour from the current cover art and animates to it,
 * so the full player's backdrop shifts with whatever is playing. Falls back to the
 * theme accent when there's no art or extraction fails.
 *
 * The bitmap is fetched through the same Coil pipeline (and cache keys) as [ArtImage],
 * so this rarely hits the network.
 */
@Composable
fun rememberArtAccent(coverId: String?, requestSize: Int = 200): Color {
    val context = LocalContext.current
    val api = NavitunesApp.container().apiClient
    val fallback = NavTheme.colors.accent

    var target by remember { mutableStateOf(fallback) }

    LaunchedEffect(coverId, fallback) {
        val url = api.coverUrl(coverId, requestSize)
        if (url == null) {
            target = fallback
            return@LaunchedEffect
        }
        val stableKey = "cover:${coverId}:${requestSize}"
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(stableKey)
                    .diskCacheKey(stableKey)
                    .allowHardware(false) // Palette needs a readable (software) bitmap.
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@runCatching null
                val palette = Palette.from(bitmap).clearFilters().generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.dominantSwatch
                swatch?.rgb?.let { Color(it) }
            }.getOrNull()
        }
        target = extracted ?: fallback
    }

    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(700),
        label = "artAccent"
    )
    return animated
}
