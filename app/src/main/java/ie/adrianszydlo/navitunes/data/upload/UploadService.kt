package ie.adrianszydlo.navitunes.data.upload

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.OpenableColumns
import ie.adrianszydlo.navitunes.data.api.ApiClient
import ie.adrianszydlo.navitunes.data.auth.SubsonicAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

/**
 * Posts the given local audio URI to the user's configured upload endpoint
 * as `multipart/form-data` with the Subsonic-style auth params attached as
 * query string — so the server-side receiver can re-hash and verify the
 * caller without having the plaintext password.
 *
 * Subsonic / Navidrome have no upload endpoint of their own — the receiver
 * is something the user runs (a small PHP/Python handler on their Apache
 * wrapper). See the README for an example.
 */
class UploadService(
    private val context: Context,
    private val api: ApiClient
) {

    /** A small set of formats the user's library should accept. */
    private val allowedMimePrefixes = setOf("audio/")
    private val allowedExtensions = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")

    /** Cap individual uploads. Anything bigger should go over scp/SMB. */
    private val maxSizeBytes = 200L * 1024 * 1024 // 200 MB

    @Serializable
    data class RemoveCandidate(val relative: String, val sizeBytes: Long = 0L)

    sealed interface Result {
        data class Success(val message: String) : Result
        data class Failure(val message: String) : Result

        /** Server found multiple files matching the song; user must pick one. */
        data class Ambiguous(
            val songId: String,
            val candidates: List<RemoveCandidate>
        ) : Result
    }

    private val responseJson = Json { ignoreUnknownKeys = true }

    /**
     * Derives sibling endpoint URLs from whatever the user typed:
     *   "https://music.example.com/upload" → base "https://music.example.com"
     *   "https://music.example.com"        → base "https://music.example.com"
     * so we can build /upload and /remove off the same setting.
     */
    private fun base(endpoint: String): String =
        endpoint.trimEnd('/').removeSuffix("/upload").removeSuffix("/remove")

    /** Override endpoint if non-blank, otherwise the active profile's server. */
    private fun effectiveEndpoint(endpoint: String): String? =
        endpoint.takeIf { it.isNotBlank() } ?: api.activeProfile()?.normalizedServer

    private fun uploadUrl(endpoint: String) = "${base(endpoint)}/upload"
    private fun removeUrl(endpoint: String) = "${base(endpoint)}/remove"

    /**
     * Asks the user's upload server to delete a song.
     * If [explicitPath] is non-null it's sent as `?path=` to disambiguate
     * between multiple disk files matching the same songId.
     */
    suspend fun removeFromLibrary(
        songId: String,
        endpoint: String,
        explicitPath: String? = null
    ): Result = withContext(Dispatchers.IO) {
        val profile = api.activeProfile()
            ?: return@withContext Result.Failure("No active profile")
        val resolved = effectiveEndpoint(endpoint)
            ?: return@withContext Result.Failure("No server configured")

        val authedUrl = removeUrl(resolved).toUri().buildUpon().apply {
            for ((k, v) in SubsonicAuth.params(profile.username, profile.password)) {
                appendQueryParameter(k, v)
            }
            appendQueryParameter("songId", songId)
            if (!explicitPath.isNullOrBlank()) appendQueryParameter("path", explicitPath)
        }.build().toString()

        val request = Request.Builder()
            .url(authedUrl)
            .method("DELETE", null)
            .build()

        return@withContext try {
            api.okHttp.newCall(request).execute().use { resp ->
                val body = runCatching { resp.body.string() }.getOrDefault("")
                when {
                    resp.isSuccessful -> {
                        runCatching { api.call("startScan.view") }
                        Result.Success("Removed from library. Rescan triggered.")
                    }
                    resp.code == 300 -> parseAmbiguous(songId, body)
                        ?: Result.Failure("Server returned 300 but no candidates")
                    resp.code == 401 -> Result.Failure("Server rejected auth — check ALLOWED_USERS on the receiver.")
                    resp.code == 403 -> Result.Failure("Server refused to delete: ${shortErr(body) ?: "path outside music root"}.")
                    resp.code == 404 -> Result.Failure("Server couldn't find the file: ${shortErr(body) ?: "song not found"}.")
                    else -> Result.Failure("Server returned HTTP ${resp.code}${shortErr(body)?.let { " — $it" } ?: ""}")
                }
            }
        } catch (t: Throwable) {
            Result.Failure(t.message ?: t.javaClass.simpleName)
        }
    }

    /** Parses a `300 Multiple Choices` response into the Ambiguous result. */
    private fun parseAmbiguous(songId: String, body: String): Result.Ambiguous? {
        if (body.isBlank()) return null
        return runCatching {
            val root = responseJson.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
            val arr = root["candidates"] as? kotlinx.serialization.json.JsonArray ?: return@runCatching null
            val list = arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val rel = (obj["relative"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val size = (obj["sizeBytes"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                RemoveCandidate(relative = rel, sizeBytes = size)
            }
            if (list.isEmpty()) null else Result.Ambiguous(songId = songId, candidates = list)
        }.getOrNull()
    }

    /** Best-effort extraction of the `error` field from a JSON error body. */
    private fun shortErr(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            responseJson.parseToJsonElement(body)
                .let { it as? JsonObject }
                ?.get("error")
                ?.let { it as? JsonPrimitive }
                ?.content
        }.getOrNull()
    }

    suspend fun upload(uri: Uri, endpoint: String): Result = withContext(Dispatchers.IO) {
        val profile = api.activeProfile()
            ?: return@withContext Result.Failure("No active profile")

        val resolver = context.contentResolver
        val (displayName, sizeBytes, mime) = inspect(resolver, uri)

        if (sizeBytes != null && sizeBytes > maxSizeBytes) {
            return@withContext Result.Failure("File is ${sizeBytes / 1024 / 1024} MB; limit is 200 MB.")
        }

        val ext = displayName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        val mimeOk = mime?.let { m -> allowedMimePrefixes.any { m.startsWith(it) } } == true
        val extOk = ext?.let { it in allowedExtensions } == true
        if (!mimeOk && !extOk) {
            return@withContext Result.Failure("Unsupported audio format ($mime / .$ext).")
        }

        val mediaType = (mime ?: "audio/mpeg").toMediaTypeOrNull()
        val streamingBody = object : RequestBody() {
            override fun contentType() = mediaType
            override fun contentLength(): Long = sizeBytes ?: -1L
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.use { input ->
                    input.source().use { sink.writeAll(it) }
                } ?: throw IOException("Could not open $uri")
            }
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = displayName ?: "track.mp3",
                body = streamingBody
            )
            .addFormDataPart("filename", displayName ?: "track.mp3")
            .build()

        // Append Subsonic auth params so the receiver can verify.
        val resolved = effectiveEndpoint(endpoint)
            ?: return@withContext Result.Failure("No server configured")
        val url = buildAuthedUrl(uploadUrl(resolved), profile.username, profile.password)
        val request = Request.Builder().url(url).post(multipart).build()

        return@withContext try {
            api.okHttp.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    // Best-effort: ask Navidrome to rescan so the new track appears soon.
                    runCatching { api.call("startScan.view") }
                    Result.Success("Uploaded \"${displayName ?: "track"}\". Rescan triggered.")
                } else {
                    Result.Failure("Server returned HTTP ${resp.code}")
                }
            }
        } catch (t: Throwable) {
            Result.Failure(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun buildAuthedUrl(endpoint: String, username: String, password: String): String {
        val base = endpoint.toUri().buildUpon()
        for ((k, v) in SubsonicAuth.params(username, password)) {
            base.appendQueryParameter(k, v)
        }
        return base.build().toString()
    }

    private data class FileInfo(val name: String?, val size: Long?, val mime: String?)

    private fun inspect(resolver: ContentResolver, uri: Uri): FileInfo {
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) name = c.getString(nameIdx)
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        return FileInfo(name, size, resolver.getType(uri))
    }
}
