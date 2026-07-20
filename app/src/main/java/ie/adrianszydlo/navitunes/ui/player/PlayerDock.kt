package ie.adrianszydlo.navitunes.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ie.adrianszydlo.navitunes.R
import ie.adrianszydlo.navitunes.playback.PlayerController
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Danger
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The docked player: a normal mini-player, or — after a long-press — a draggable
 * circular "bubble" that snaps to the screen edges (Android-bubble style). Tap the
 * bubble to open the full player, long-press to restore the mini-player, or drag it
 * onto the trash target to dismiss playback entirely.
 */
@Composable
fun BoxScope.PlayerDock(controller: PlayerController, onExpand: () -> Unit) {
    val current by controller.currentItem.collectAsStateWithLifecycle()
    if (current == null) return

    var bubble by rememberSaveable { mutableStateOf(false) }

    if (!bubble) {
        MiniPlayer(
            controller = controller,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            onExpand = onExpand,
            onLongPress = { bubble = true },
            onSwipeUp = { bubble = true }
        )
    } else {
        BubblePlayer(
            controller = controller,
            onExpand = onExpand,
            onExitBubble = { bubble = false },
            onDismiss = { controller.stop(); bubble = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubblePlayer(
    controller: PlayerController,
    onExpand: () -> Unit,
    onExitBubble: () -> Unit,
    onDismiss: () -> Unit
) {
    val song by controller.currentItem.collectAsStateWithLifecycle()
    val s = song ?: return
    val playing by controller.isPlaying.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val bubbleDp = 62.dp
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val bubblePx = with(density) { bubbleDp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val trashCenter = Offset(wPx / 2f, hPx - with(density) { 60.dp.toPx() })
        val trashRadiusPx = with(density) { 72.dp.toPx() }

        // "Dock" = where the mini-player sits; the bubble flies out from here and
        // shrinks back into it, so the morph reads as a continuous motion.
        val dockPos = remember { Offset(wPx / 2f - bubblePx / 2f, hPx - bubblePx - marginPx) }
        val restPos = remember { Offset(wPx - bubblePx - marginPx, hPx * 0.42f) }

        val scope = rememberCoroutineScope()
        val offset = remember { Animatable(dockPos, Offset.VectorConverter) }
        val scale = remember { Animatable(0.3f) }
        var dragging by remember { mutableStateOf(false) }
        var overTrash by remember { mutableStateOf(false) }

        // Entry: fly from the dock out to the resting edge while scaling up.
        LaunchedEffect(Unit) {
            launch { offset.animateTo(restPos, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
            launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        }

        fun center() = Offset(offset.value.x + bubblePx / 2f, offset.value.y + bubblePx / 2f)

        fun exitToMini() = scope.launch {
            launch { offset.animateTo(dockPos, tween(280)) }
            scale.animateTo(0.2f, tween(280))
            onExitBubble()
        }
        fun dismiss() = scope.launch {
            scale.animateTo(0f, tween(200))
            onDismiss()
        }

        // Trash / dismiss target, shown while dragging.
        if (dragging) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .size(if (overTrash) 66.dp else 54.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (overTrash) Danger else Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.dismiss), tint = Color.White)
            }
        }

        Box(
            Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(bubbleDp)
                .scale(scale.value)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onExpand() },
                        onLongPress = { exitToMini() }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDrag = { change, delta ->
                            change.consume()
                            scope.launch {
                                offset.snapTo(
                                    Offset(
                                        (offset.value.x + delta.x).coerceIn(marginPx, wPx - bubblePx - marginPx),
                                        (offset.value.y + delta.y).coerceIn(marginPx, hPx - bubblePx - marginPx)
                                    )
                                )
                            }
                            overTrash = (center() - trashCenter).getDistance() < trashRadiusPx
                        },
                        onDragEnd = {
                            dragging = false
                            if (overTrash) {
                                dismiss()
                            } else {
                                val targetX = if (center().x < wPx / 2f) marginPx else wPx - bubblePx - marginPx
                                scope.launch { offset.animateTo(Offset(targetX, offset.value.y), tween(240)) }
                            }
                        }
                    )
                }
        ) {
            ArtImage(
                coverId = s.coverArt,
                fallback = s.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = bubbleDp / 2,
                requestSize = 200,
                fallbackIcon = if (s.id.startsWith("radio:")) Icons.Filled.Radio else null
            )
            if (!playing) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null, tint = Accent)
                }
            }
        }
    }
}
