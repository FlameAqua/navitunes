package ie.adrianszydlo.navitunes.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ie.adrianszydlo.navitunes.data.api.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

private val Context.recentDataStore by preferencesDataStore("navitunes_recent")

/**
 * Per-profile MRU list of songs the user has listened to. Updated whenever the
 * MediaSession transitions to a new track. Capped at [MAX] entries; older
 * entries are dropped. Subsonic has no equivalent endpoint, so this lives
 * client-side.
 */
class RecentlyPlayedStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun key(profileId: String) = stringPreferencesKey("recent_$profileId")

    fun observe(profileId: String): Flow<List<Song>> =
        context.recentDataStore.data.map { prefs ->
            prefs[key(profileId)]?.let { raw ->
                runCatching { json.decodeFromString<List<Song>>(raw) }
                    .getOrDefault(emptyList())
                    .deduplicate()
            } ?: emptyList()
        }

    suspend fun push(profileId: String, song: Song) {
        if (song.id.isBlank()) return
        context.recentDataStore.edit { prefs ->
            val current = prefs[key(profileId)]?.let { raw ->
                runCatching { json.decodeFromString<List<Song>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            // Navidrome IDs are database IDs, not permanent music identifiers. A beets move
            // followed by a full scan can replace an ID while the same track remains in this
            // local history. Keep the fresh server representation and remove the stale entry.
            prefs[key(profileId)] = json.encodeToString((listOf(song) + current).deduplicate().take(MAX))
        }
    }

    /** Drop any cached entries matching [songId] — used after server-side deletion. */
    suspend fun purge(profileId: String, songId: String) {
        context.recentDataStore.edit { prefs ->
            val current = prefs[key(profileId)]?.let { raw ->
                runCatching { json.decodeFromString<List<Song>>(raw) }.getOrDefault(emptyList())
            } ?: return@edit
            val filtered = current.filterNot { it.id == songId }
            if (filtered.size != current.size) {
                prefs[key(profileId)] = json.encodeToString(filtered)
            }
        }
    }

    companion object {
        const val MAX = 30

        /**
         * Stable across Navidrome rescans and harmless formatting changes such as
         * "Ely Oaks/LAVINIA" becoming "Ely Oaks · LAVINIA" after tag normalisation.
         */
        internal fun Song.recentIdentity(): String {
            val artistParts = artist.orEmpty()
                .split(ARTIST_SEPARATORS)
                .map(::normalisePart)
                .filter(String::isNotBlank)
                .sorted()
            return "${normalisePart(title)}|${artistParts.joinToString("|")}"
        }

        internal fun List<Song>.deduplicate(): List<Song> =
            distinctBy { it.recentIdentity() }

        private val ARTIST_SEPARATORS = Regex("[/·•,;]|\\s-\\s")

        private fun normalisePart(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
    }
}
