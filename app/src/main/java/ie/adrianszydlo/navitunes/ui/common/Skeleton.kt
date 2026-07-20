package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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

/**
 * Home first-load skeleton. Mirrors the real Home layout so the placeholder reads as
 * the page that's about to appear: a Recently-Played card (art strip), a Playlists
 * card (taller art strip), then a Recently-Added song card. The greeting hero is
 * rendered separately by [HomeScreen] above this, so it's not repeated here.
 */
@Composable
fun HomeSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        // Recently Played — header + a strip of square art tiles.
        SkeletonCard {
            SkeletonSectionHead()
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(4) {
                    Column {
                        SkeletonBlock(Modifier.size(140.dp), corner = 14.dp)
                        Spacer(Modifier.height(10.dp))
                        SkeletonBlock(Modifier.width(96.dp).height(13.dp), corner = 5.dp)
                        Spacer(Modifier.height(6.dp))
                        SkeletonBlock(Modifier.width(60.dp).height(11.dp), corner = 5.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // My Playlists — header + a strip of taller playlist cards.
        SkeletonCard {
            SkeletonSectionHead()
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(3) {
                    Column {
                        SkeletonBlock(Modifier.size(width = 152.dp, height = 152.dp), corner = 14.dp)
                        Spacer(Modifier.height(10.dp))
                        SkeletonBlock(Modifier.width(104.dp).height(13.dp), corner = 5.dp)
                        Spacer(Modifier.height(6.dp))
                        SkeletonBlock(Modifier.width(56.dp).height(11.dp), corner = 5.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Recently Added — header + song rows.
        SkeletonCard {
            SkeletonSectionHead()
            Spacer(Modifier.height(6.dp))
            Column(Modifier.padding(horizontal = 8.dp)) {
                repeat(4) { SongRowSkeleton() }
            }
        }
    }
}

/** A section card wrapper mirroring [SectionCard]'s padding, for skeletons. */
@Composable
private fun SkeletonCard(content: @Composable ColumnScope.() -> Unit) {
    SectionCard(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(vertical = 14.dp), content = content)
    }
}

/** A skeleton stand-in for a [SectionHead] (title + subtitle lines). */
@Composable
private fun ColumnScope.SkeletonSectionHead() {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SkeletonBlock(Modifier.width(150.dp).height(20.dp), corner = 6.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(Modifier.width(96.dp).height(12.dp), corner = 5.dp)
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
