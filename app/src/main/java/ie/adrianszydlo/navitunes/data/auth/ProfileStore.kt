package ie.adrianszydlo.navitunes.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Persists Navidrome profiles in EncryptedSharedPreferences.
 * Credentials are AES-GCM encrypted with a master key in the Android Keystore.
 */
class ProfileStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _profiles = MutableStateFlow(loadAll())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    val active: Profile? get() = _profiles.value.firstOrNull { it.id == _activeId.value }

    fun add(name: String, server: String, user: String, pass: String): Profile {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { user },
            serverUrl = server,
            username = user,
            password = pass
        )
        val updated = _profiles.value + profile
        _profiles.value = updated
        persist(updated)
        setActive(profile.id)
        return profile
    }

    fun update(profile: Profile) {
        val updated = _profiles.value.map { if (it.id == profile.id) profile else it }
        _profiles.value = updated
        persist(updated)
    }

    fun remove(id: String) {
        val updated = _profiles.value.filterNot { it.id == id }
        _profiles.value = updated
        persist(updated)
        if (_activeId.value == id) {
            setActive(updated.firstOrNull()?.id)
        }
    }

    fun setActive(id: String?) {
        _activeId.value = id
        prefs.edit().run {
            if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id)
            apply()
        }
        if (id != null) {
            update(active!!.copy(lastUsedAt = System.currentTimeMillis()))
        }
    }

    private fun loadAll(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun persist(profiles: List<Profile>) {
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(profiles)).apply()
    }

    companion object {
        private const val FILE_NAME = "navitunes_profiles"
        private const val KEY_PROFILES = "profiles_v1"
        private const val KEY_ACTIVE = "active_profile_id"
    }
}
