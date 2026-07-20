package ie.adrianszydlo.navitunes.ui.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.InternetRadioStation
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.common.ArtImage
import ie.adrianszydlo.navitunes.ui.common.EmptyState
import ie.adrianszydlo.navitunes.ui.common.LocalNotifier
import ie.adrianszydlo.navitunes.ui.common.ScreenTopBar
import ie.adrianszydlo.navitunes.ui.common.SectionCard
import ie.adrianszydlo.navitunes.ui.common.SectionHead
import ie.adrianszydlo.navitunes.ui.common.SongRowSkeleton
import ie.adrianszydlo.navitunes.ui.common.clickableScaled
import ie.adrianszydlo.navitunes.ui.nav.LocalPlayerController
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.AccentOn
import ie.adrianszydlo.navitunes.ui.theme.Surface
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface RadioState {
    data object Loading : RadioState
    data class Ready(
        val stations: List<InternetRadioStation>,
        val seeds: List<Song>
    ) : RadioState
}

/**
 * Radio tab. Two ways to listen:
 *   - **Stations** — internet radio stations configured on the Navidrome server.
 *   - **Song radio** — pick one of your recent/favourite tracks as a seed and the app
 *     builds an endless queue of similar songs (getSimilarSongs2, then top songs by the
 *     seed's artist, then random as a last resort).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(onPlay: (List<Song>, Int) -> Unit) {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    val controller = LocalPlayerController.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val activeId by container.profileStore.activeId.collectAsState()

    var state by remember { mutableStateOf<RadioState>(RadioState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    // Station CRUD dialogs: an "edit" holds the station being edited; a null station with
    // adding=true means "new".
    var editingStation by remember { mutableStateOf<InternetRadioStation?>(null) }
    var addingStation by remember { mutableStateOf(false) }
    var deletingStation by remember { mutableStateOf<InternetRadioStation?>(null) }

    suspend fun load() {
        val stations = runCatching { repo.internetRadioStations() }.getOrDefault(emptyList())
        val recent = activeId?.let { id ->
            runCatching { container.recentlyPlayedStore.observe(id).first() }.getOrDefault(emptyList())
        }.orEmpty()
        val favorites = runCatching { repo.starred().song }.getOrDefault(emptyList())
        val seeds = (recent + favorites).distinctBy { it.id }.take(12)
        state = RadioState.Ready(stations, seeds)
    }

    val signalTick by container.librarySignals.refresh.collectAsState()
    LaunchedEffect(activeId, signalTick) {
        if (state !is RadioState.Ready) state = RadioState.Loading
        load()
    }

    fun startSongRadio(seed: Song) {
        if (starting) return
        starting = true
        scope.launch {
            val similar = runCatching { repo.similarSongs(seed.id, 60) }.getOrDefault(emptyList())
            val extra = if (similar.size < 10 && !seed.artist.isNullOrBlank()) {
                runCatching { repo.topSongs(seed.artist!!, 40) }.getOrDefault(emptyList())
            } else emptyList()
            val filler = if (similar.isEmpty() && extra.isEmpty()) {
                runCatching { repo.randomSongs(40) }.getOrDefault(emptyList())
            } else emptyList()
            val queue = (listOf(seed) + similar + extra + filler).distinctBy { it.id }
            starting = false
            if (queue.size <= 1 && similar.isEmpty() && extra.isEmpty() && filler.isEmpty()) {
                notifier.error("Couldn't build a radio for this track")
            } else {
                notifier.info("Starting radio from \"${seed.title}\"")
                onPlay(queue, 0)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Radio")
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val s = state) {
                RadioState.Loading -> Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    repeat(6) { SongRowSkeleton() }
                }
                is RadioState.Ready -> LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 200.dp)
                ) {
                    // Song radio seeds.
                    item {
                        SectionCard(Modifier.padding(horizontal = 20.dp)) {
                            Column(Modifier.padding(vertical = 14.dp)) {
                                Box(Modifier.padding(horizontal = 16.dp)) {
                                    SectionHead("Song radio", "From your tracks")
                                }
                                Spacer(Modifier.height(10.dp))
                                if (s.seeds.isEmpty()) {
                                    Text(
                                        "Play a few songs first — your radio seeds appear here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Text3,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                } else {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        items(s.seeds, key = { it.id }) { seed ->
                                            SeedTile(seed = seed, onClick = { startSongRadio(seed) })
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    // Internet radio stations.
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.weight(1f)) { SectionHead("Stations") }
                            androidx.compose.material3.IconButton(onClick = { addingStation = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Add station", tint = Accent)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (s.stations.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No stations",
                                body = "Tap + to add an internet radio station."
                            )
                        }
                    } else {
                        items(s.stations, key = { it.id }) { station ->
                            StationRow(
                                station = station,
                                onPlay = {
                                    controller.playStream(station.id, station.name, station.streamUrl)
                                    notifier.info("Tuning in to ${station.name}")
                                },
                                onEdit = { editingStation = station },
                                onDelete = { deletingStation = station }
                            )
                        }
                    }
                }
            }
        }
    }

    if (addingStation || editingStation != null) {
        val editing = editingStation
        StationDialog(
            existing = editing,
            onDismiss = { addingStation = false; editingStation = null },
            onSave = { name, url, homepage ->
                addingStation = false; editingStation = null
                scope.launch {
                    runCatching {
                        if (editing == null) repo.createInternetRadioStation(name, url, homepage)
                        else repo.updateInternetRadioStation(editing.id, name, url, homepage)
                    }.onSuccess {
                        notifier.success(if (editing == null) "Station added" else "Station updated")
                        load()
                    }.onFailure { notifier.error("Couldn't save: ${it.message}") }
                }
            }
        )
    }

    deletingStation?.let { st ->
        ie.adrianszydlo.navitunes.ui.common.ConfirmDialog(
            title = "Delete \"${st.name}\"?",
            message = "This removes the station from your server.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                deletingStation = null
                scope.launch {
                    runCatching { repo.deleteInternetRadioStation(st.id) }
                        .onSuccess { notifier.success("Station deleted"); load() }
                        .onFailure { notifier.error("Couldn't delete: ${it.message}") }
                }
            },
            onDismiss = { deletingStation = null }
        )
    }
}

@Composable
private fun SeedTile(seed: Song, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 132.dp, height = 186.dp)
            .clickableScaled(onClick = onClick)
    ) {
        Box {
            ArtImage(
                coverId = seed.coverArt,
                fallback = seed.title,
                modifier = Modifier.size(132.dp),
                cornerRadius = 14.dp,
                requestSize = 300
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AccentOn, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            seed.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            seed.artist.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = Text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StationRow(
    station: InternetRadioStation,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Podcasts, contentDescription = null, tint = Accent)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                station.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!station.homepageUrl.isNullOrBlank()) {
                Text(
                    station.homepageUrl!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Station options", tint = Text3)
            }
            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Delete", color = ie.adrianszydlo.navitunes.ui.theme.Danger) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ie.adrianszydlo.navitunes.ui.theme.Danger) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

/** Add / edit dialog for an internet radio station. */
@Composable
private fun StationDialog(
    existing: InternetRadioStation?,
    onDismiss: () -> Unit,
    onSave: (name: String, streamUrl: String, homepageUrl: String?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.streamUrl.orEmpty()) }
    var homepage by remember { mutableStateOf(existing?.homepageUrl.orEmpty()) }
    val canSave = name.isNotBlank() && url.isNotBlank()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add station" else "Edit station") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true, placeholder = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    singleLine = true, placeholder = { Text("Stream URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = homepage, onValueChange = { homepage = it },
                    singleLine = true, placeholder = { Text("Homepage URL (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (canSave) onSave(name.trim(), url.trim(), homepage.trim().ifBlank { null }) },
                enabled = canSave
            ) { Text(if (existing == null) "Add" else "Save", color = Accent) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
