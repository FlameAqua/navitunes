package ie.adrianszydlo.navitunes.data.api

import android.net.Uri
import ie.adrianszydlo.navitunes.data.auth.Profile
import ie.adrianszydlo.navitunes.data.auth.ProfileStore
import ie.adrianszydlo.navitunes.data.auth.SubsonicAuth
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around OkHttp that:
 *   * resolves the active profile's server URL per call,
 *   * regenerates Subsonic auth params (with a fresh salt) per call,
 *   * parses the `subsonic-response` envelope and surfaces errors as exceptions.
 *
 * Cover-art and stream URLs are produced by the same auth path so they
 * embed the credentials in the query string — usable directly as Coil
 * image sources and ExoPlayer media URIs.
 */
class ApiClient(private val profileStore: ProfileStore) {

    val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun activeProfile(): Profile? = profileStore.active

    fun invalidate() {
        // Reserved for future per-profile caches.
    }

    /** Build a fully-authenticated URL for an endpoint. */
    fun urlFor(endpoint: String, params: Map<String, String> = emptyMap()): String {
        val profile = requireProfile()
        val base = "${profile.normalizedServer}/rest/${endpoint.removePrefix("/")}"
        val builder = Uri.parse(base).buildUpon()
        for ((k, v) in SubsonicAuth.params(profile.username, profile.password)) {
            builder.appendQueryParameter(k, v)
        }
        for ((k, v) in params) builder.appendQueryParameter(k, v)
        return builder.build().toString()
    }

    fun coverUrl(coverId: String?, size: Int = 300): String? {
        if (coverId.isNullOrBlank()) return null
        return urlFor("getCoverArt.view", mapOf("id" to coverId, "size" to size.toString()))
    }

    fun streamUrl(songId: String): String =
        urlFor("stream.view", mapOf("id" to songId))

    fun downloadUrl(songId: String): String =
        urlFor("download.view", mapOf("id" to songId))

    suspend fun call(endpoint: String, params: Map<String, String> = emptyMap()): SubsonicResponse {
        val profile = requireProfile()
        return callWith(profile.normalizedServer, profile.username, profile.password, endpoint, params)
    }

    /**
     * One-shot call against arbitrary credentials — used by the login flow
     * to verify a server + user + password tuple before persisting it.
     */
    suspend fun callWith(
        server: String,
        username: String,
        password: String,
        endpoint: String,
        params: Map<String, String> = emptyMap()
    ): SubsonicResponse {
        val base = "${server.trimEnd('/')}/rest/${endpoint.removePrefix("/")}"
        val builder = Uri.parse(base).buildUpon()
        for ((k, v) in SubsonicAuth.params(username, password)) builder.appendQueryParameter(k, v)
        for ((k, v) in params) builder.appendQueryParameter(k, v)
        val url = builder.build().toString().toHttpUrl()
        val request = Request.Builder().url(url).get().build()
        val raw = okHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Empty body")
        }
        val envelope = try {
            json.decodeFromString(SubsonicEnvelope.serializer(), raw)
        } catch (t: Throwable) {
            throw IOException("Bad response: ${t.message}", t)
        }
        val r = envelope.response
        if (r.status == "failed") {
            throw SubsonicException(r.error?.code ?: -1, r.error?.message ?: "API error")
        }
        return r
    }

    private fun requireProfile(): Profile =
        profileStore.active ?: throw IllegalStateException("No active profile")
}

class SubsonicException(val code: Int, message: String) : IOException(message) {
    val isAuthError get() = code == 40 || code == 41
}
