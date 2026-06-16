package ie.adrianszydlo.navitunes.data.offline

import ie.adrianszydlo.navitunes.data.api.Song
import kotlinx.coroutines.runBlocking

/**
 * Routes a song to its local file:// URI if it's been downloaded for the active
 * profile, otherwise returns null so the caller falls back to the stream URL.
 */
class OfflineResolver(private val downloads: DownloadRepository) {

    fun uriFor(song: Song): String? {
        // Sync read of the cached state is fine — Room queries are fast and
        // this is only called from the player when building a MediaItem.
        return runBlocking { downloads.fileUriForSongOrNull(song.id) }
    }
}
