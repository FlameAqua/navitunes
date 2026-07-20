package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.ui.theme.Text3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Drag-to-dismiss controller for a bottom sheet, driven by the top handle only. The
 * sheet body follows the finger via [contentModifier]; releasing past the threshold
 * animates it the rest of the way out, otherwise it springs back up. Pair with
 * `sheetGesturesEnabled = false` so body scrolls never dismiss.
 */
class SheetDismiss internal constructor(
    internal val offset: Animatable<Float, AnimationVector1D>,
    internal val scope: CoroutineScope,
    internal val onDismiss: () -> Unit
) {
    /** Apply to the sheet's root content so it tracks the drag. */
    val contentModifier: Modifier
        get() = Modifier.offset { IntOffset(0, offset.value.roundToInt()) }

    /** Animate the sheet fully out, then notify the host to drop it. */
    fun close() {
        scope.launch {
            offset.animateTo(2500f, tween(260, easing = FastOutLinearInEasing))
            onDismiss()
        }
    }

    internal fun settle(threshold: Float) {
        if (offset.value > threshold) close()
        else scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    internal fun onDrag(dy: Float) {
        scope.launch { offset.snapTo((offset.value + dy).coerceAtLeast(0f)) }
    }
}

@Composable
fun rememberSheetDismiss(onDismiss: () -> Unit): SheetDismiss {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    return remember(onDismiss) { SheetDismiss(offset, scope, onDismiss) }
}

/**
 * The grabber pill at the top of a sheet. Drag it down to move the sheet with your
 * finger; release past ~140dp to dismiss, otherwise it springs back.
 */
@Composable
fun SheetDragHandle(state: SheetDismiss, modifier: Modifier = Modifier) {
    val threshold = with(LocalDensity.current) { 140.dp.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { state.settle(threshold) },
                    onDragCancel = { state.settle(threshold) },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        state.onDrag(dy)
                    }
                )
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Text3.copy(alpha = 0.5f))
        )
    }
}
