package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.runtime.compositionLocalOf

/**
 * Set of song IDs that are fully downloaded under the active profile.
 * SongRow consults this to render the "available offline" badge without
 * having to query the DB per row.
 */
val LocalDownloadedIds = compositionLocalOf<Set<String>> { emptySet() }

/**
 * The currently-playing song id paired with whether it's actively playing (vs paused).
 * Collected once at the shell so every SongRow can show a now-playing indicator without
 * each row subscribing to the player flows. `id` is null when nothing is loaded.
 */
data class NowPlaying(val id: String?, val playing: Boolean)

val LocalNowPlaying = compositionLocalOf { NowPlaying(null, false) }
