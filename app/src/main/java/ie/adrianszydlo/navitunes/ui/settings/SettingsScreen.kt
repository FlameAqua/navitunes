package ie.adrianszydlo.navitunes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Wifi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.auth.Profile
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.Danger
import ie.adrianszydlo.navitunes.ui.theme.Surface
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onAddProfile: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val container = NavitunesApp.container()
    val scope = rememberCoroutineScope()
    val profiles by container.profileStore.profiles.collectAsState()
    val activeId by container.profileStore.activeId.collectAsState()
    val wifiOnly by container.preferences.wifiOnly.collectAsState(initial = false)
    val uploadEndpoint by container.preferences.uploadEndpoint.collectAsState(initial = null)
    val ctx = LocalContext.current

    var pingMessage by remember { mutableStateOf<String?>(null) }
    var profileToRemove by remember { mutableStateOf<Profile?>(null) }
    var editingUploadUrl by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val endpoint = uploadEndpoint
        if (uri == null) return@rememberLauncherForActivityResult
        if (endpoint.isNullOrBlank()) {
            Toast.makeText(ctx, "Set an upload URL first.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        uploading = true
        scope.launch {
            val result = container.uploadService.upload(uri, endpoint)
            uploading = false
            val msg = when (result) {
                is ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Success -> result.message
                is ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Failure -> "Upload failed: ${result.message}"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTopBar(title = "Settings") }

        item {
            GroupHeader("Profiles")
            Group {
                profiles.forEach { p ->
                    ProfileSettingsRow(
                        profile = p,
                        active = p.id == activeId,
                        onSelect = {
                            container.profileStore.setActive(p.id)
                            container.onProfileSwitched()
                        },
                        onRemove = { profileToRemove = p }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddProfile)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Accent)
                    Spacer(Modifier.size(8.dp))
                    Text("Add profile", color = Accent)
                }
            }
        }

        item {
            GroupHeader("Downloads")
            Group {
                ToggleRow(
                    icon = Icons.Outlined.Wifi,
                    label = "Wi-Fi only",
                    description = "Only download when connected to Wi-Fi.",
                    value = wifiOnly,
                    onChange = { scope.launch { container.preferences.setWifiOnly(it) } }
                )
                ClickRow(
                    icon = Icons.Outlined.Download,
                    label = "Manage downloads",
                    onClick = onOpenDownloads
                )
            }
        }

        item {
            GroupHeader("Upload")
            Group {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Upload song to library",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Subsonic has no upload endpoint — you must run a small receiver " +
                            "on your server that drops the file into Navidrome's music folder. " +
                            "See the README.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Text3
                    )
                    Spacer(Modifier.height(12.dp))
                    if (editingUploadUrl) {
                        var draft by remember(uploadEndpoint) { mutableStateOf(uploadEndpoint.orEmpty()) }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text("https://music.example.com/upload.php") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    container.preferences.setUploadEndpoint(draft.takeIf { it.isNotBlank() })
                                }
                                editingUploadUrl = false
                            }) { Text("Save", color = Accent) }
                            TextButton(onClick = { editingUploadUrl = false }) { Text("Cancel") }
                        }
                    } else {
                        Text(
                            uploadEndpoint ?: "No upload URL set.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Text2
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editingUploadUrl = true }) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text(if (uploadEndpoint == null) "Set URL" else "Edit URL")
                            }
                            TextButton(
                                onClick = {
                                    if (uploadEndpoint.isNullOrBlank()) {
                                        Toast.makeText(ctx, "Set an upload URL first.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        pickAudio.launch("audio/*")
                                    }
                                },
                                enabled = !uploading
                            ) {
                                if (uploading) {
                                    CircularProgressIndicator(
                                        color = Accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text("Uploading…", color = Accent)
                                } else {
                                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = Accent)
                                    Spacer(Modifier.size(4.dp))
                                    Text("Choose file", color = Accent)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            GroupHeader("Connection")
            Group {
                Column(Modifier.padding(16.dp)) {
                    Text("Test connection", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pingMessage ?: "Confirms that the active profile reaches the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Text3
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            pingMessage = try {
                                val version = container.libraryRepository.ping()
                                "Connected · ${version ?: "OK"}"
                            } catch (t: Throwable) {
                                "Failed: ${t.message ?: "Unknown error"}"
                            }
                        }
                    }) { Text("Ping", color = Accent) }
                }
            }
        }

        item {
            GroupHeader("About")
            Group {
                StaticRow("Version", "0.2.0")
                StaticRow("Build", "Navitunes Android")
                StaticRow("Source", "github.com/adrianszydlo/navitunes")
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (profileToRemove != null) {
        val p = profileToRemove!!
        AlertDialog(
            onDismissRequest = { profileToRemove = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("This signs you out of ${p.serverUrl}. Offline downloads for this profile will also be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.downloadRepository.clearAllForActiveProfile()
                        container.profileStore.remove(p.id)
                        container.onProfileSwitched()
                    }
                    profileToRemove = null
                }) { Text("Remove", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { profileToRemove = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Text3,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface),
        content = content
    )
}

@Composable
private fun ProfileSettingsRow(
    profile: Profile,
    active: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                profile.name.firstOrNull()?.uppercase() ?: "?",
                color = AccentOn,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${profile.username} · ${displayHost(profile.serverUrl)}",
                style = MaterialTheme.typography.bodySmall,
                color = Text3
            )
        }
        if (active) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = "Active", tint = Accent)
        }
        TextButton(onClick = onRemove) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Remove", tint = Text3)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Text2)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Text3)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Text2)
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StaticRow(key: String, value: String) {
    Column(Modifier.padding(16.dp)) {
        Text(
            key.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Text3
        )
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun displayHost(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
