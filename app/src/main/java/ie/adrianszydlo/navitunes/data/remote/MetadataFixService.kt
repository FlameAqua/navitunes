package ie.adrianszydlo.navitunes.data.remote

import android.util.Log
import androidx.core.net.toUri
import ie.adrianszydlo.navitunes.data.auth.ProfileStore
import ie.adrianszydlo.navitunes.data.auth.SubsonicAuth
import ie.adrianszydlo.navitunes.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Live state of the server-side metadata-fix job. The server owns the truth, so this
 * survives leaving the screen — or restarting the app — and is just re-read.
 */
data class FixStatus(
    val running: Boolean = false,
    val step: Int = 0,
    val steps: Int = 6,
    val stage: String? = null,
    val ok: Boolean? = null,
    val message: String? = null,
    /** Set when the server couldn't be reached at all. */
    val unreachable: Boolean = false
) {
    /** One-line label for the UI. */
    val label: String
        get() = when {
            unreachable -> "Server unreachable"
            running -> {
                val s = stage?.replaceFirstChar { it.uppercase() } ?: "Working"
                if (step in 1..steps) "$s… ($step/$steps)" else "$s…"
            }
            ok == true -> message ?: "Done"
            ok == false -> message ?: "Failed"
            else -> "Idle"
        }
}

/**
 * Drives the server-side metadata-fix job (`/fix`) and reads its live progress
 * (`/fix/status`). The heavy lifting — beets import, MusicBrainz sync, genres, tag
 * writing, playlist repointing and a full Navidrome rescan — happens on the server.
 */
class MetadataFixService(
    private val okHttp: OkHttpClient,
    private val profileStore: ProfileStore,
    private val preferences: AppPreferences
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Kick off a fix. Returns the server's status (409 → already running). */
    suspend fun start(): FixStatus = call("/fix", post = true)

    /** Poll current progress. */
    suspend fun status(): FixStatus = call("/fix/status", post = false)

    private suspend fun call(path: String, post: Boolean): FixStatus = withContext(Dispatchers.IO) {
        val profile = profileStore.active
            ?: return@withContext FixStatus(unreachable = true, message = "No active profile.")
        val base = (preferences.uploadEndpoint.first()?.trim()?.takeIf { it.isNotBlank() }
            ?: profile.normalizedServer)
            .trimEnd('/').removeSuffix("/upload").removeSuffix("/remove")

        val url = "$base$path".toUri().buildUpon().apply {
            for ((k, v) in SubsonicAuth.params(profile.username, profile.password)) {
                appendQueryParameter(k, v)
            }
        }.build().toString()

        val builder = Request.Builder().url(url)
        if (post) builder.post("{}".toRequestBody("application/json".toMediaType())) else builder.get()

        runCatching {
            okHttp.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                // 409 = already running; the body still carries the live status.
                if (resp.isSuccessful || resp.code == 409) {
                    parse(body)
                } else {
                    FixStatus(unreachable = true, message = "Server returned HTTP ${resp.code}.")
                }
            }
        }.getOrElse {
            Log.w(TAG, "fix $path failed", it)
            FixStatus(unreachable = true, message = it.message ?: "Couldn't reach the server")
        }
    }

    private fun parse(body: String): FixStatus {
        if (body.isBlank()) return FixStatus()
        return runCatching {
            val d = json.decodeFromString(FixDto.serializer(), body)
            FixStatus(
                running = d.running,
                step = d.step,
                steps = if (d.steps > 0) d.steps else 6,
                stage = d.stage,
                ok = d.ok,
                message = d.message
            )
        }.getOrElse { FixStatus() }
    }

    @Serializable
    private data class FixDto(
        val running: Boolean = false,
        val step: Int = 0,
        val steps: Int = 6,
        val stage: String? = null,
        val ok: Boolean? = null,
        val message: String? = null
    )

    private companion object {
        const val TAG = "Navitunes/Fix"
    }
}
