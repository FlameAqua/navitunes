package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Horizontal swipe gesture for the player surfaces.
 * Threshold is 64dp of accumulated horizontal travel; vertical wobble doesn't trigger.
 *
 * Wins over a single-tap [clickable] (those use a separate gesture detector),
 * but a fast tap will not trip the threshold.
 */
fun Modifier.swipeToSkip(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 64.dp.toPx() }

    pointerInput(Unit) {
        var totalDx = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDx = 0f },
            onDragEnd = {
                if (abs(totalDx) >= thresholdPx) {
                    if (totalDx < 0) onSwipeLeft() else onSwipeRight()
                }
                totalDx = 0f
            },
            onDragCancel = { totalDx = 0f },
            onHorizontalDrag = { _, dx -> totalDx += dx }
        )
    }
}

/**
 * Upward-swipe gesture — used on the mini-player to morph it into the floating bubble
 * (matching the long-press action). Fires once accumulated upward travel passes 48dp.
 * Coexists with [swipeToSkip]: horizontal and vertical detectors disambiguate by axis.
 */
fun Modifier.swipeUp(onSwipeUp: () -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 48.dp.toPx() }
    pointerInput(Unit) {
        var totalDy = 0f
        var fired = false
        detectVerticalDragGestures(
            onDragStart = { totalDy = 0f; fired = false },
            onDragEnd = { totalDy = 0f; fired = false },
            onDragCancel = { totalDy = 0f; fired = false },
            onVerticalDrag = { _, dy ->
                totalDy += dy
                if (!fired && totalDy <= -thresholdPx) {
                    fired = true
                    onSwipeUp()
                }
            }
        )
    }
}
