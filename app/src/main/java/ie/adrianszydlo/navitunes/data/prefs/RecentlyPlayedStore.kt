package ie.adrianszydlo.navitunes.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ie.adrianszydlo.navitunes.data.api.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
                runCatching { json.decodeFromString<List<Song>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun push(profileId: String, song: Song) {
        if (song.id.isBlank()) return
        context.recentDataStore.edit { prefs ->
            val current = prefs[key(profileId)]?.let { raw ->
                runCatching { json.decodeFromString<List<Song>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            val deduped = listOf(song) + current.filterNot { it.id == song.id }
            prefs[key(profileId)] = json.encodeToString(deduped.take(MAX))
        }
    }

    suspend fun clear(profileId: String) {
        context.recentDataStore.edit { it.remove(key(profileId)) }
    }

    companion object {
        const val MAX = 30
    }
}
