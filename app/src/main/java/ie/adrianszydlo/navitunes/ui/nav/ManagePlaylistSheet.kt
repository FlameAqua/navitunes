package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PublicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.ui.common.ConfirmDialog
import ie.adrianszydlo.navitunes.ui.common.LocalNotifier
import ie.adrianszydlo.navitunes.ui.common.SheetDragHandle
import ie.adrianszydlo.navitunes.ui.common.rememberSheetDismiss
import ie.adrianszydlo.navitunes.ui.common.TextPromptDialog
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Bg
import ie.adrianszydlo.navitunes.ui.theme.Danger
import ie.adrianszydlo.navitunes.ui.theme.Text2
import ie.adrianszydlo.navitunes.ui.theme.Text3
import ie.adrianszydlo.navitunes.ui.theme.TextHi
import kotlinx.coroutines.launch

/**
 * Bottom sheet for managing a playlist: rename, edit description, toggle public, or
 * delete. Navidrome exposes no API for a custom cover image, so that isn't offered
 * (covers are derived from the member songs). Hosted by the shell.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ManagePlaylistSheet(request: ManagePlaylistRequest, onDismiss: () -> Unit) {
    val container = NavitunesApp.container()
    val repo = container.libraryRepository
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val playlist = request.playlist

    var renaming by remember { mutableStateOf(false) }
    var editingDesc by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val isPublic = playlist.public == true

    fun done(message: String) {
        container.librarySignals.notifyChanged()
        notifier.success(message)
        onDismiss()
    }

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        sheetGesturesEnabled = false
    ) {
        val dismiss = rememberSheetDismiss(onDismiss)
        // The card carries its own background so it slides as one with the drag.
        Column(
            Modifier
                .then(dismiss.contentModifier)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg)
        ) {
        SheetDragHandle(state = dismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            Text(playlist.name, style = MaterialTheme.typography.titleLarge, color = TextHi, maxLines = 2)
            Text(
                "${playlist.songCount} song${if (playlist.songCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = Text3
            )
            Spacer(Modifier.height(16.dp))

            SheetAction(
                icon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null, tint = Accent) },
                label = "Rename",
                onClick = { renaming = true }
            )
            SheetAction(
                icon = { Icon(Icons.Outlined.Notes, contentDescription = null, tint = Accent) },
                label = if (playlist.comment.isNullOrBlank()) "Add description" else "Edit description",
                onClick = { editingDesc = true }
            )
            SheetAction(
                icon = {
                    Icon(
                        if (isPublic) Icons.Outlined.PublicOff else Icons.Outlined.Public,
                        contentDescription = null,
                        tint = Accent
                    )
                },
                label = if (isPublic) "Make private" else "Make public",
                onClick = {
                    scope.launch {
                        runCatching { repo.updatePlaylistMeta(playlist.id, public = !isPublic) }
                            .onSuccess { done(if (isPublic) "Now private" else "Now public") }
                            .onFailure { notifier.error("Couldn't update: ${it.message}") }
                    }
                }
            )
            SheetAction(
                icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Danger) },
                label = "Delete playlist",
                tint = Danger,
                onClick = { confirmingDelete = true }
            )
        }
        }
    }

    if (renaming) {
        TextPromptDialog(
            title = "Rename playlist",
            initialValue = playlist.name,
            label = "Playlist name",
            confirmLabel = "Rename",
            onConfirm = { newName ->
                renaming = false
                scope.launch {
                    runCatching { repo.updatePlaylistMeta(playlist.id, name = newName) }
                        .onSuccess { done("Renamed") }
                        .onFailure { notifier.error("Couldn't rename: ${it.message}") }
                }
            },
            onDismiss = { renaming = false }
        )
    }

    if (editingDesc) {
        TextPromptDialog(
            title = "Description",
            initialValue = playlist.comment.orEmpty(),
            label = "Description",
            confirmLabel = "Save",
            allowEmpty = true,
            singleLine = false,
            onConfirm = { text ->
                editingDesc = false
                scope.launch {
                    runCatching { repo.updatePlaylistMeta(playlist.id, comment = text) }
                        .onSuccess { done("Description saved") }
                        .onFailure { notifier.error("Couldn't save: ${it.message}") }
                }
            },
            onDismiss = { editingDesc = false }
        )
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete \"${playlist.name}\"?",
            message = "This permanently deletes the playlist from your library. The individual songs stay.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                scope.launch {
                    runCatching { repo.deletePlaylist(playlist.id) }
                        .onSuccess {
                            container.librarySignals.notifyChanged()
                            notifier.success("Playlist deleted")
                            onDismiss()
                            request.onDeleted?.invoke()
                        }
                        .onFailure { notifier.error("Couldn't delete: ${it.message}") }
                }
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}

@Composable
private fun SheetAction(
    icon: @Composable () -> Unit,
    label: String,
    tint: Color = Text2,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tint)
    }
}
