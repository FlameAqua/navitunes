package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

@Composable
fun ArtImage(
    coverId: String?,
    fallback: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    requestSize: Int = 300
) {
    val context = LocalContext.current
    val api = NavitunesApp.container().apiClient
    val url = remember(coverId, requestSize) { api.coverUrl(coverId, requestSize) }
    val letter = remember(fallback) {
        fallback.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(listOf(SurfaceElev, Surface))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            // Stable cache key derived from coverId+size — independent of the
            // salted query string Subsonic appends to every request. Without
            // this, each render emits a new URL and Coil treats every cover
            // as a fresh image, defeating its LRU.
            val stableKey = "cover:${coverId}:${requestSize}"
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
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
                error = { Fallback(letter) },
                loading = { Fallback(letter) }
            )
        } else {
            Fallback(letter)
        }
    }
}

@Composable
private fun Fallback(letter: String) {
    Text(
        text = letter,
        color = Text4,
        fontSize = 38.sp,
        style = MaterialTheme.typography.displayLarge
    )
}
