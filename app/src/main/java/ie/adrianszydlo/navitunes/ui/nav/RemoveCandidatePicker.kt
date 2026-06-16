package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.data.upload.UploadService
import ie.adrianszydlo.navitunes.ui.common.formatBytes
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3

/**
 * Shown when the server reports multiple disk files matching the same songId.
 * The user taps the file they actually want deleted; we pass its relative
 * path back through [onPick].
 */
@Composable
fun RemoveCandidatePicker(
    songTitle: String,
    candidates: List<UploadService.RemoveCandidate>,
    onPick: (UploadService.RemoveCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Multiple files match", style = MaterialTheme.typography.titleMedium)
                Text(
                    songTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Text2,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        text = {
            Column {
                Text(
                    "The server found ${candidates.size} possible files for this song. " +
                        "Pick the one you want to delete:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Text3
                )
                Spacer(Modifier.padding(top = 8.dp))
                LazyColumn {
                    items(candidates, key = { it.relative }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(c) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                c.relative,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f)
                            )
                            if (c.sizeBytes > 0) {
                                Spacer(Modifier.padding(start = 8.dp))
                                Text(
                                    formatBytes(c.sizeBytes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Text3
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
