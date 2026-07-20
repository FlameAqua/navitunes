package ie.adrianszydlo.navitunes.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("navitunes_prefs")

/** App-wide non-secret preferences. */
class AppPreferences(private val context: Context) {

    private val keyWifiOnly = booleanPreferencesKey("downloads_wifi_only")
    private val keyUploadEndpoint = stringPreferencesKey("upload_endpoint_url")
    private val keySpotifyId = stringPreferencesKey("spotify_client_id")
    private val keySpotifySecret = stringPreferencesKey("spotify_client_secret")
    private val keySkippedUpdate = stringPreferencesKey("update_skipped_version")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyRecentSearches = stringPreferencesKey("recent_searches")
    private val keySkipSilence = booleanPreferencesKey("skip_silence")
    private val keyVolume = floatPreferencesKey("player_volume")
    private val keyNpTitle = stringPreferencesKey("now_playing_title")
    private val keyNpArtist = stringPreferencesKey("now_playing_artist")
    private val keyNpPlaying = booleanPreferencesKey("now_playing_playing")

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[keyWifiOnly] ?: false }

    /** Theme preference as a raw token ("system" | "light" | "dark"); the UI maps it to ThemeMode. */
    val themeMode: Flow<String> = context.dataStore.data.map { it[keyThemeMode] ?: "system" }

    /** ExoPlayer skip-silence: trims silent stretches from playback. */
    val skipSilence: Flow<Boolean> = context.dataStore.data.map { it[keySkipSilence] ?: false }

    /** App-level output gain (0.0–1.0). Lets the user go below the Bluetooth absolute-volume floor. */
    val volume: Flow<Float> = context.dataStore.data.map { (it[keyVolume] ?: 1f).coerceIn(0f, 1f) }

    /** Most-recent search queries, newest first (max 8). */
    val recentSearches: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[keyRecentSearches]?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
    }

    // Current-track snapshot for the home-screen widget (written by the player service).
    val nowPlayingTitle: Flow<String> = context.dataStore.data.map { it[keyNpTitle] ?: "" }
    val nowPlayingArtist: Flow<String> = context.dataStore.data.map { it[keyNpArtist] ?: "" }
    val nowPlayingPlaying: Flow<Boolean> = context.dataStore.data.map { it[keyNpPlaying] ?: false }

    suspend fun setNowPlaying(title: String, artist: String, playing: Boolean) =
        context.dataStore.edit {
            it[keyNpTitle] = title
            it[keyNpArtist] = artist
            it[keyNpPlaying] = playing
        }
    val uploadEndpoint: Flow<String?> = context.dataStore.data.map { it[keyUploadEndpoint] }
    val spotifyClientId: Flow<String?> = context.dataStore.data.map { it[keySpotifyId] }
    val spotifyClientSecret: Flow<String?> = context.dataStore.data.map { it[keySpotifySecret] }

    /** Version the user chose to skip in the on-launch update prompt (null = none). */
    val skippedUpdateVersion: Flow<String?> = context.dataStore.data.map { it[keySkippedUpdate] }

    suspend fun setWifiOnly(v: Boolean) = context.dataStore.edit { it[keyWifiOnly] = v }

    suspend fun setThemeMode(token: String) = context.dataStore.edit { it[keyThemeMode] = token }

    suspend fun setSkipSilence(v: Boolean) = context.dataStore.edit { it[keySkipSilence] = v }

    suspend fun setVolume(v: Float) = context.dataStore.edit { it[keyVolume] = v.coerceIn(0f, 1f) }

    suspend fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[keyRecentSearches]?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(q) + existing.filterNot { it.equals(q, ignoreCase = true) }).take(8)
            prefs[keyRecentSearches] = updated.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() = context.dataStore.edit { it.remove(keyRecentSearches) }

    suspend fun setUploadEndpoint(url: String?) = context.dataStore.edit {
        if (url.isNullOrBlank()) it.remove(keyUploadEndpoint) else it[keyUploadEndpoint] = url
    }

    suspend fun setSpotifyCredentials(id: String?, secret: String?) = context.dataStore.edit {
        if (id.isNullOrBlank()) it.remove(keySpotifyId) else it[keySpotifyId] = id.trim()
        if (secret.isNullOrBlank()) it.remove(keySpotifySecret) else it[keySpotifySecret] = secret.trim()
    }

    suspend fun setSkippedUpdateVersion(version: String?) = context.dataStore.edit {
        if (version.isNullOrBlank()) it.remove(keySkippedUpdate) else it[keySkippedUpdate] = version
    }
}
