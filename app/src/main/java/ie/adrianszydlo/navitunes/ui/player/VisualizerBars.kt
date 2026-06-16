package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Decorative animated bars flanking the album art on the full player.
 * Not connected to the actual audio stream (that requires RECORD_AUDIO + the
 * Visualizer API, which is overkill for ambient decoration). Pure motion,
 * scaled by play/pause state.
 *
 * Set [mirror] = true on the right side so the pattern reads symmetrically.
 */
@Composable
fun VisualizerBars(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 7,
    mirror: Boolean = false
) {
    val phases = remember(barCount) { (0 until barCount).map { it * 0.45f } }
    val transition = rememberInfiniteTransition(label = "viz")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "viz-phase"
    )
    val intensity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.18f,
        animationSpec = tween(durationMillis = 450),
        label = "viz-intensity"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val gap = w / (barCount * 2f - 1f)
        val barWidth = gap

        for (i in 0 until barCount) {
            val raw =
                sin(t + phases[i]) * 0.55f +
                sin(t * 1.7f + phases[i] * 1.3f) * 0.30f +
                sin(t * 0.6f + phases[i] * 0.7f) * 0.15f
            val normalized = (abs(raw) * 0.85f + 0.15f).coerceIn(0.15f, 1f) * intensity
            val barHeight = (h * normalized).coerceAtLeast(h * 0.06f)
            val cy = h / 2f
            val idx = if (mirror) barCount - 1 - i else i
            val x = idx * gap * 2f + barWidth / 2f

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.15f), color, color.copy(alpha = 0.15f)),
                    startY = cy - barHeight / 2f,
                    endY = cy + barHeight / 2f
                ),
                start = Offset(x, cy - barHeight / 2f),
                end = Offset(x, cy + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
