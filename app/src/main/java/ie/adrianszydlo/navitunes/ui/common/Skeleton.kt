package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.ui.theme.NavTheme

/**
 * A placeholder block with a sweeping highlight. Used for first-load states in place
 * of a bare spinner, so the layout the user is about to see is already implied.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp
) {
    val colors = NavTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = Motion.Standard),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    val base = colors.surfaceElev
    val highlight = if (colors.isDark) colors.surfaceHi else colors.surface
    Spacer(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(base)
            .drawWithContent {
                drawContent()
                val w = size.width
                val start = (progress * 2f - 0.5f) * w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base.copy(alpha = 0f), highlight.copy(alpha = 0.65f), base.copy(alpha = 0f)),
                        start = Offset(start, 0f),
                        end = Offset(start + w * 0.5f, size.height)
                    )
                )
            }
    )
}

/** A skeleton row that mirrors [SongRow]'s layout (art + two text lines). */
@Composable
fun SongRowSkeleton(showArt: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArt) {
            SkeletonBlock(Modifier.size(48.dp), corner = 10.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(15.dp), corner = 5.dp)
            Spacer(Modifier.height(7.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(11.dp), corner = 5.dp)
        }
    }
}

/** Home first-load skeleton: a section header + a few song rows + a card strip. */
@Composable
fun HomeSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(24.dp), corner = 6.dp)
        Spacer(Modifier.height(18.dp))
        repeat(4) {
            SongRowSkeleton()
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(24.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(20.dp), corner = 6.dp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) { SkeletonBlock(Modifier.size(150.dp), corner = 14.dp) }
        }
    }
}

/** Grid skeleton for Library / Search first load. */
@Composable
fun GridSkeleton(rows: Int = 3) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(2) {
                    Column(Modifier.weight(1f)) {
                        SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), corner = 14.dp)
                        Spacer(Modifier.height(8.dp))
                        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(13.dp), corner = 5.dp)
                        Spacer(Modifier.height(6.dp))
                        SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(11.dp), corner = 5.dp)
                    }
                }
            }
        }
    }
}
