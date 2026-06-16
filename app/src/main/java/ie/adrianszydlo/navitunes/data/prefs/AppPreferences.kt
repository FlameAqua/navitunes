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

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[keyWifiOnly] ?: false }
    val uploadEndpoint: Flow<String?> = context.dataStore.data.map { it[keyUploadEndpoint] }

    suspend fun setWifiOnly(v: Boolean) = context.dataStore.edit { it[keyWifiOnly] = v }
    
    suspend fun setUploadEndpoint(url: String?) = context.dataStore.edit {
        if (url.isNullOrBlank()) it.remove(keyUploadEndpoint) else it[keyUploadEndpoint] = url
    }
}
