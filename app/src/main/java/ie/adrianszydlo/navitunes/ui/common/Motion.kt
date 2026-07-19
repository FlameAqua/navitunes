package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/** Shared motion tokens so timing/feel is consistent app-wide. */
object Motion {
    const val Medium = 260

    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/**
 * Tap target that gently scales down while pressed — the tactile feel modern apps
 * use on cards and art tiles. Wraps `clickable` so callers get press feedback + the
 * scale from one modifier.
 */
fun Modifier.clickableScaled(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    indication: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = if (indication) LocalIndication.current else null,
            enabled = enabled,
            onClick = onClick
        )
}

/** Press-scale variant that also supports long-press (for row context menus). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableScaled(
    enabled: Boolean = true,
    pressedScale: Float = 0.98f,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScaleCombined"
    )
    this
        .scale(scale)
        .combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
}
