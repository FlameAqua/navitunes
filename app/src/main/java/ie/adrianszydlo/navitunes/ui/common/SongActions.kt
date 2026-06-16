package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.runtime.Immutable

/**
 * Per-song actions hooked up by each screen. Any callback left null hides
 * the corresponding menu item — keeps the dropdown context-sensitive.
 */
@Immutable
data class SongActions(
    val onPlay: (() -> Unit)? = null,
    val onPlayNext: (() -> Unit)? = null,
    val onAddToQueue: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null,
    val onDownload: (() -> Unit)? = null,
    val onFavorite: (() -> Unit)? = null,
    val onOpenAlbum: (() -> Unit)? = null,
    val onRemoveDownload: (() -> Unit)? = null
)
