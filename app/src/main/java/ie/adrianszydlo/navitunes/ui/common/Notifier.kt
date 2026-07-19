package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.ui.theme.NavTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class NoticeKind { Info, Success, Error }

private class NavSnackbarVisuals(
    override val message: String,
    val kind: NoticeKind
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/**
 * Lightweight in-app notifier: a single styled snackbar surface used for user-initiated
 * feedback ("Added to playlist", "Queued for download", …) in place of system Toasts,
 * so confirmations match the app's look and appear in-context above the mini-player.
 */
class Notifier(
    private val host: SnackbarHostState,
    private val scope: CoroutineScope
) {
    fun info(message: String) = post(message, NoticeKind.Info)
    fun success(message: String) = post(message, NoticeKind.Success)
    fun error(message: String) = post(message, NoticeKind.Error)

    private fun post(message: String, kind: NoticeKind) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(NavSnackbarVisuals(message, kind))
        }
    }
}

val LocalNotifier = staticCompositionLocalOf<Notifier> {
    error("No Notifier provided")
}

/** Renders the active notice as a rounded, theme-aware card with a leading status icon. */
@Composable
fun NavSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier = modifier) { data ->
        val colors = NavTheme.colors
        val kind = (data.visuals as? NavSnackbarVisuals)?.kind ?: NoticeKind.Info
        val (icon: ImageVector, tint) = when (kind) {
            NoticeKind.Success -> Icons.Outlined.CheckCircle to colors.success
            NoticeKind.Error -> Icons.Outlined.ErrorOutline to colors.danger
            NoticeKind.Info -> Icons.Outlined.Info to colors.accent
        }
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, shape, clip = false)
                    .clip(shape)
                    .background(colors.surfaceHi)
                    .border(1.dp, colors.border, shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    data.visuals.message,
                    color = colors.textHi,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
