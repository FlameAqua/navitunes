package ie.adrianszydlo.navitunes.data.remote

import android.util.Log
import androidx.core.net.toUri
import ie.adrianszydlo.navitunes.data.auth.ProfileStore
import ie.adrianszydlo.navitunes.data.auth.SubsonicAuth
import ie.adrianszydlo.navitunes.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Manually triggers the server-side metadata-fix job (the same job a cron runs
 * on a schedule). POSTs to `{base}/fix`, authenticated with the active profile's
 * Subsonic token. The heavy lifting — MusicBrainz lookup + tag writing + rescan —
 * happens on the server; this just kicks it off and reports back.
 */
class MetadataFixService(
    private val okHttp: OkHttpClient,
    private val profileStore: ProfileStore,
    private val preferences: AppPreferences
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns a human-readable status for a Toast. Never throws. */
    suspend fun triggerFix(): String = withContext(Dispatchers.IO) {
        val profile = profileStore.active ?: return@withContext "No active profile."
        // Use the upload-URL override if set, otherwise the active profile's server.
        val base = (preferences.uploadEndpoint.first()?.trim()?.takeIf { it.isNotBlank() }
            ?: profile.normalizedServer)
            .trimEnd('/').removeSuffix("/upload").removeSuffix("/remove")

        val url = "$base/fix".toUri().buildUpon().apply {
            for ((k, v) in SubsonicAuth.params(profile.username, profile.password)) {
                appendQueryParameter(k, v)
            }
        }.build().toString()

        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            okHttp.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    Log.i(TAG, "fix triggered: ${body.take(200)}")
                    messageOf(body) ?: "Metadata fix started on the server."
                } else if (resp.code == 409) {
                    "A fix is already running — try again later."
                } else {
                    "Server returned HTTP ${resp.code}."
                }
            }
        }.getOrElse {
            Log.w(TAG, "fix trigger failed", it)
            "Couldn't reach the server: ${it.message ?: "unknown error"}"
        }
    }

    /** Pulls an optional `message`/`status` field out of a JSON response body. */
    private fun messageOf(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
            (obj["message"] as? JsonPrimitive)?.content
                ?: (obj["status"] as? JsonPrimitive)?.content
        }.getOrNull()
    }

    private companion object {
        const val TAG = "Navitunes/Fix"
    }
}
