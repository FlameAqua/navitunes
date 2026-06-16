package ie.adrianszydlo.navitunes.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("navitunes_prefs")

/** App-wide non-secret preferences. */
class AppPreferences(private val context: Context) {

    private val keyWifiOnly = booleanPreferencesKey("downloads_wifi_only")
    private val keyMaxCacheMb = intPreferencesKey("max_stream_cache_mb")
    private val keyShuffle = booleanPreferencesKey("queue_shuffle")
    private val keyRepeat = stringPreferencesKey("queue_repeat")
    private val keyQueueJson = stringPreferencesKey("queue_json")
    private val keyQueueIndex = intPreferencesKey("queue_index")
    private val keyQueuePosition = longPreferencesKey("queue_position_ms")

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[keyWifiOnly] ?: false }
    val maxStreamCacheMb: Flow<Int> = context.dataStore.data.map { it[keyMaxCacheMb] ?: DEFAULT_CACHE_MB }
    val shuffle: Flow<Boolean> = context.dataStore.data.map { it[keyShuffle] ?: false }
    val repeat: Flow<String> = context.dataStore.data.map { it[keyRepeat] ?: "off" }
    val queueJson: Flow<String?> = context.dataStore.data.map { it[keyQueueJson] }
    val queueIndex: Flow<Int> = context.dataStore.data.map { it[keyQueueIndex] ?: -1 }
    val queuePosition: Flow<Long> = context.dataStore.data.map { it[keyQueuePosition] ?: 0L }

    suspend fun setWifiOnly(v: Boolean) = context.dataStore.edit { it[keyWifiOnly] = v }
    suspend fun setMaxStreamCacheMb(v: Int) = context.dataStore.edit { it[keyMaxCacheMb] = v }
    suspend fun setShuffle(v: Boolean) = context.dataStore.edit { it[keyShuffle] = v }
    suspend fun setRepeat(mode: String) = context.dataStore.edit { it[keyRepeat] = mode }
    suspend fun setQueue(json: String, index: Int, positionMs: Long) = context.dataStore.edit {
        it[keyQueueJson] = json
        it[keyQueueIndex] = index
        it[keyQueuePosition] = positionMs
    }

    companion object {
        const val DEFAULT_CACHE_MB = 500
    }
}
