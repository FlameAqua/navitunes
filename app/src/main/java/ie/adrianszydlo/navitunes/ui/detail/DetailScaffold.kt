package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.NavTheme
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3

@Composable
fun DetailHeader(
    tag: String,
    title: String,
    subtitle: String?,
    meta: String,
    coverArt: String?,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    fallbackCoverArt: String? = null
) {
    val bg = NavTheme.colors.bg
    Box(Modifier.fillMaxWidth()) {
        // Blurred cover art backdrop that fades into the page background.
        ArtImage(
            coverId = coverArt,
            fallback = title,
            fallbackCoverId = fallbackCoverArt,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .blur(36.dp),
            cornerRadius = 0.dp,
            requestSize = 400
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to bg.copy(alpha = 0.30f),
                        0.55f to bg.copy(alpha = 0.75f),
                        1f to bg
                    )
                )
        )

        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = NavTheme.colors.textHi)
                }
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    Modifier
                        .size(140.dp)
                        .shadow(16.dp, RoundedCornerShape(14.dp), clip = false)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    ArtImage(
                        coverId = coverArt,
                        fallback = title,
                        fallbackCoverId = fallbackCoverArt,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 14.dp,
                        requestSize = 400
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tag.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Accent
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = NavTheme.colors.textHi,
                        maxLines = 3
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Text2)
                    }
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = Text3,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * The centred action row for album/playlist detail: Play (emphasised) · Shuffle · Download ·
 * Search, all as clean icon buttons. The search glyph toggles the in-list search bar
 * ([onToggleSearch]); it's only shown when that callback is provided.
 */
@Composable
fun DetailActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onToggleSearch: (() -> Unit)? = null,
    searchActive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            18.dp, Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailActionButton(
            icon = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            onClick = onPlay,
            filled = true
        )
        DetailActionButton(
            icon = Icons.Filled.Shuffle,
            contentDescription = "Shuffle",
            onClick = onShuffle
        )
        if (onDownload != null) {
            DetailActionButton(
                icon = Icons.Outlined.Download,
                contentDescription = "Download",
                onClick = onDownload
            )
        }
        if (onToggleSearch != null) {
            DetailActionButton(
                icon = Icons.Outlined.Search,
                contentDescription = "Search",
                onClick = onToggleSearch,
                active = searchActive
            )
        }
    }
}

/**
 * One circular action button. [filled] = the emphasised accent Play button; [active] = a
 * toggled state (accent tint), used by the search button when the search bar is open.
 */
@Composable
private fun DetailActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    active: Boolean = false
) {
    val size = if (filled) 56.dp else 50.dp
    val bg = when {
        filled -> Accent
        active -> Accent.copy(alpha = 0.18f)
        else -> NavTheme.colors.surfaceElev
    }
    val tint = when {
        filled -> AccentOn
        active -> Accent
        else -> NavTheme.colors.textHi
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(
                if (!filled) Modifier.border(
                    1.dp,
                    if (active) Accent else NavTheme.colors.border,
                    RoundedCornerShape(50)
                ) else Modifier
            )
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
