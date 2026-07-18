package ie.adrianszydlo.navitunes.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[keyWifiOnly] ?: false }
    val uploadEndpoint: Flow<String?> = context.dataStore.data.map { it[keyUploadEndpoint] }
    val spotifyClientId: Flow<String?> = context.dataStore.data.map { it[keySpotifyId] }
    val spotifyClientSecret: Flow<String?> = context.dataStore.data.map { it[keySpotifySecret] }

    /** Version the user chose to skip in the on-launch update prompt (null = none). */
    val skippedUpdateVersion: Flow<String?> = context.dataStore.data.map { it[keySkippedUpdate] }

    suspend fun setWifiOnly(v: Boolean) = context.dataStore.edit { it[keyWifiOnly] = v }

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
