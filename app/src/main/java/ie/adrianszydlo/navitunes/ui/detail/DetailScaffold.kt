package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun DetailActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentOn),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Play", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Shuffle, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Shuffle")
        }
        if (onDownload != null) {
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Outlined.Download, contentDescription = "Download")
            }
        }
    }
}
