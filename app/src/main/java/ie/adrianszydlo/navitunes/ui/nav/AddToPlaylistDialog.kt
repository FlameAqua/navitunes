package ie.adrianszydlo.navitunes.ui.nav

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3
import kotlinx.coroutines.launch

/**
 * Dialog shown when the user picks "Add to playlist…" on a song.
 * Lists the user's playlists; tapping one appends the song. An inline
 * "New playlist" row lets the user create one on the fly without leaving
 * the dialog.
 */
@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val container = NavitunesApp.container()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    suspend fun refresh() {
        playlists = runCatching { container.libraryRepository.allPlaylists() }.getOrDefault(emptyList())
    }

    LaunchedEffect(song.id) { refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Add to playlist", style = MaterialTheme.typography.titleMedium)
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Text2,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        text = {
            val list = playlists
            if (list == null) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            } else {
                Column {
                    if (creating) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                singleLine = true,
                                placeholder = { Text("Playlist name") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.size(8.dp))
                            TextButton(
                                onClick = {
                                    val name = newName.trim()
                                    if (name.isNotEmpty()) {
                                        scope.launch {
                                            runCatching {
                                                val created = container.libraryRepository.createPlaylist(name)
                                                container.libraryRepository.addToPlaylist(created.id, listOf(song.id))
                                            }.onSuccess {
                                                container.librarySignals.notifyChanged()
                                                Toast.makeText(ctx, "Added to \"$name\"", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }.onFailure {
                                                Toast.makeText(ctx, "Couldn't add", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = newName.isNotBlank()
                            ) { Text("Create", color = Accent) }
                        }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { creating = true }
                                .padding(vertical = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null, tint = Accent)
                            Spacer(Modifier.size(12.dp))
                            Text("New playlist…", color = Accent, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (list.isEmpty() && !creating) {
                        Text(
                            "No playlists yet — tap above to create one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Text3
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(list, key = { it.id }) { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                runCatching {
                                                    container.libraryRepository.addToPlaylist(p.id, listOf(song.id))
                                                }.onSuccess {
                                                    container.librarySignals.notifyChanged()
                                                    Toast.makeText(ctx, "Added to \"${p.name}\"", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                }.onFailure {
                                                    Toast.makeText(ctx, "Couldn't add", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${p.songCount} song${if (p.songCount == 1) "" else "s"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Text3
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
