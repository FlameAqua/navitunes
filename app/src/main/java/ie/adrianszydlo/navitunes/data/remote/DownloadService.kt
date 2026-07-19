package ie.adrianszydlo.navitunes.data.remote

import android.util.Log
import androidx.core.net.toUri
import ie.adrianszydlo.navitunes.data.auth.ProfileStore
import ie.adrianszydlo.navitunes.data.auth.SubsonicAuth
import ie.adrianszydlo.navitunes.data.discovery.SpotifyResult
import ie.adrianszydlo.navitunes.data.discovery.SpotifyType
import ie.adrianszydlo.navitunes.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to the homelab upload server's download channel.
 *
 *   - [isAlive]           GET  `{base}/health` — a fast liveness probe.
 *   - [requestDownload]   POST `{base}/download` — asks the server to fetch a
 *                         Spotify track/album/etc. Blocks until the server
 *                         finishes (spotdl runs synchronously server-side), so
 *                         it uses a generous timeout.
 *
 * The base URL is derived from the configured upload endpoint (falling back to
 * the active profile's server), so it always talks to the same box as uploads.
 */
class DownloadService(
    okHttp: OkHttpClient,
    private val profileStore: ProfileStore,
    private val preferences: AppPreferences
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // Short-fused client for the liveness probe — we want a quick yes/no.
    private val healthClient: OkHttpClient = okHttp.newBuilder()
        .connectTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    // Patient client for the actual download — spotdl can take a while.
    private val downloadClient: OkHttpClient = okHttp.newBuilder()
        .readTimeout(DOWNLOAD_TIMEOUT_MIN, TimeUnit.MINUTES)
        .writeTimeout(DOWNLOAD_TIMEOUT_MIN, TimeUnit.MINUTES)
        .callTimeout(DOWNLOAD_TIMEOUT_MIN, TimeUnit.MINUTES)
        .build()

    @Serializable
    private data class Payload(
        val message: String,
        val ts: Long = System.currentTimeMillis()
    )

    sealed interface Outcome {
        object Success : Outcome
        object Busy : Outcome                       // server already running a download (409)
        data class Failure(val message: String) : Outcome
    }

    /** Derives the server base URL, stripping any known endpoint suffix. */
    private suspend fun base(): String? {
        val profile = profileStore.active ?: return null
        return (preferences.uploadEndpoint.first()?.trim()?.takeIf { it.isNotBlank() }
            ?: profile.normalizedServer)
            .trimEnd('/')
            .removeSuffix("/upload")
            .removeSuffix("/remove")
            .removeSuffix("/download")
    }

    /**
     * Liveness probe against `{base}/health`. Judges *reachability*, not a specific
     * route: any timely response under HTTP 500 means the server answered and is up
     * — including a 404 if `/health` isn't deployed yet — so this doesn't hard-depend
     * on the health route existing. A 5xx (e.g. Caddy 502 when the upstream is down)
     * or a timeout / connection error counts as "not alive".
     */
    suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        val base = base() ?: return@withContext false
        runCatching {
            healthClient.newCall(Request.Builder().url("$base/health").get().build())
                .execute().use { it.code < 500 }
        }.getOrElse {
            Log.w(TAG, "health check failed: ${it.message}")
            false
        }
    }

    @Serializable
    private data class HealthResponse(val status: String = "ok", val downloading: Boolean = false)

    /**
     * Whether the server currently has a download running. Reads the `downloading`
     * flag from `/health` (added server-side); if the server doesn't report it, this
     * returns false so the caller degrades to "assume finished".
     */
    suspend fun isDownloading(): Boolean = withContext(Dispatchers.IO) {
        val base = base() ?: return@withContext false
        runCatching {
            healthClient.newCall(Request.Builder().url("$base/health").get().build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body.string()
                    runCatching { json.decodeFromString(HealthResponse.serializer(), body).downloading }
                        .getOrDefault(false)
                }
        }.getOrDefault(false)
    }

    /**
     * Asks the server to download [message] (e.g. `"track 29mlJg…"`). Blocks
     * until the server responds. Never throws — returns an [Outcome].
     */
    /**
     * Asks the server to cancel the currently-running download (kills the spotdl
     * process server-side). Best-effort; returns whether the server acknowledged.
     */
    suspend fun requestCancel(): Boolean = withContext(Dispatchers.IO) {
        val profile = profileStore.active ?: return@withContext false
        val base = base() ?: return@withContext false
        val url = "$base/cancel".toUri().buildUpon().apply {
            for ((k, v) in SubsonicAuth.params(profile.username, profile.password)) appendQueryParameter(k, v)
        }.build().toString()
        runCatching {
            healthClient.newCall(
                Request.Builder().url(url).post("".toRequestBody("application/json".toMediaType())).build()
            ).execute().use { it.isSuccessful }
        }.getOrElse {
            Log.w(TAG, "cancel failed: ${it.message}")
            false
        }
    }

    suspend fun requestDownload(message: String): Outcome = withContext(Dispatchers.IO) {
        val profile = profileStore.active
            ?: return@withContext Outcome.Failure("No active profile")
        val base = base() ?: return@withContext Outcome.Failure("No server configured")

        val url = "$base/download".toUri().buildUpon().apply {
            for ((k, v) in SubsonicAuth.params(profile.username, profile.password)) {
                appendQueryParameter(k, v)
            }
        }.build().toString()

        val body = json.encodeToString(Payload(message))
            .toRequestBody("application/json".toMediaType())

        runCatching {
            downloadClient.newCall(Request.Builder().url(url).post(body).build())
                .execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            Log.i(TAG, "download ok: \"$message\"")
                            Outcome.Success
                        }
                        resp.code == 409 -> Outcome.Busy
                        else -> Outcome.Failure("Server returned HTTP ${resp.code}")
                    }
                }
        }.getOrElse {
            Log.w(TAG, "download failed: ${it.message}")
            Outcome.Failure(it.message ?: "Network error")
        }
    }

    private companion object {
        const val TAG = "Navitunes/Download"
        const val HEALTH_TIMEOUT_MS = 4_000L
        // Generous ceiling — a single track finishes in seconds, but an album
        // download in one request can legitimately run many minutes.
        const val DOWNLOAD_TIMEOUT_MIN = 20L
    }
}
