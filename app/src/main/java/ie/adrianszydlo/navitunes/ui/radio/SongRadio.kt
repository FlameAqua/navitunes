package ie.adrianszydlo.navitunes.ui.radio

import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.repo.LibraryRepository

/**
 * Builds an endless "song radio" queue seeded from [seed]. Correlated, not random:
 * getSimilarSongs2 (Last.fm-backed) first, then the seed artist's top songs if that comes
 * back thin, and only random songs as a last resort when the seed has no recommendation
 * data at all. The seed itself is always position 0.
 *
 * Shared by the Radio tab and the per-song "Start Song Radio" menu action so the two behave
 * identically.
 */
suspend fun buildSongRadioQueue(repo: LibraryRepository, seed: Song): List<Song> {
    val similar = runCatching { repo.similarSongs(seed.id, 60) }.getOrDefault(emptyList())
    val extra = if (similar.size < 10 && !seed.artist.isNullOrBlank()) {
        runCatching { repo.topSongs(seed.artist, 40) }.getOrDefault(emptyList())
    } else emptyList()
    val filler = if (similar.isEmpty() && extra.isEmpty()) {
        runCatching { repo.randomSongs(40) }.getOrDefault(emptyList())
    } else emptyList()
    return (listOf(seed) + similar + extra + filler).distinctBy { it.id }
}
