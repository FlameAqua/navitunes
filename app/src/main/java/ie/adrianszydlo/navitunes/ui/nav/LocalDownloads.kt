package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.runtime.compositionLocalOf

/**
 * Set of song IDs that are fully downloaded under the active profile.
 * SongRow consults this to render the "available offline" badge without
 * having to query the DB per row.
 */
val LocalDownloadedIds = compositionLocalOf<Set<String>> { emptySet() }
