package ie.adrianszydlo.navitunes.data.lyrics

import android.util.Log
import ie.adrianszydlo.navitunes.data.api.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Lyrics resolved from lrclib.net: synced (timed) lines preferred, else plain text. */
data class LrcResult(val synced: List<LyricLine>?, val plain: String?) {
    val isEmpty get() = synced.isNullOrEmpty() && plain.isNullOrBlank()
}

/**
 * Free, keyless lyrics from **lrclib.net**. Used as a fallback when the Navidrome
 * server has no lyrics for a track. Tries the exact `/api/get` (by track/artist/
 * album/duration) first, then a fuzzy `/api/search`. Network-only, read-only, never
 * throws — returns an empty [LrcResult] on any miss or error.
 *
 * lrclib asks clients to identify themselves via User-Agent; we send the app name.
 */
class LrcLibService(private val okHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(
        title: String,
        artist: String?,
        album: String?,
        durationSec: Int?
    ): LrcResult = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext EMPTY
        // Exact match first (most reliable, returns synced when available).
        val exact = runCatching { getExact(title, artist, album, durationSec) }.getOrNull()
        if (exact != null && !exact.isEmpty) return@withContext exact
        // Fall back to search (handy when album/duration don't line up).
        runCatching { search(title, artist) }.getOrNull() ?: EMPTY
    }

    private fun getExact(title: String, artist: String?, album: String?, durationSec: Int?): LrcResult? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder().apply {
            addQueryParameter("track_name", title)
            if (!artist.isNullOrBlank()) addQueryParameter("artist_name", artist)
            if (!album.isNullOrBlank()) addQueryParameter("album_name", album)
            if (durationSec != null && durationSec > 0) addQueryParameter("duration", durationSec.toString())
        }.build()
        val body = execute(url.toString()) ?: return null
        val record = json.decodeFromString(LrcRecord.serializer(), body)
        return record.toResult()
    }

    private fun search(title: String, artist: String?): LrcResult {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("track_name", title)
            if (!artist.isNullOrBlank()) addQueryParameter("artist_name", artist)
        }.build()
        val body = execute(url.toString()) ?: return EMPTY
        val records = runCatching { json.decodeFromString(ListSerializer, body) }.getOrDefault(emptyList())
        // Prefer the first result that actually carries lyrics (synced ideally).
        val best = records.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
            ?: records.firstOrNull { !it.plainLyrics.isNullOrBlank() }
        return best?.toResult() ?: EMPTY
    }

    private fun execute(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return okHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code != 404) Log.d(TAG, "lrclib ${resp.code} for $url")
                return null
            }
            resp.body?.string()
        }
    }

    private fun LrcRecord.toResult(): LrcResult {
        val synced = syncedLyrics?.let { parseLrc(it) }?.takeIf { it.isNotEmpty() }
        val plain = plainLyrics?.takeIf { it.isNotBlank() }
        return LrcResult(synced, plain)
    }

    private companion object {
        const val TAG = "Navitunes/Lyrics"
        const val USER_AGENT = "Navitunes (https://github.com/FlameAqua/navitunes)"
        val EMPTY = LrcResult(null, null)
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(LrcRecord.serializer())
    }

    @Serializable
    private data class LrcRecord(
        val id: Long? = null,
        @SerialName("trackName") val trackName: String? = null,
        @SerialName("artistName") val artistName: String? = null,
        @SerialName("plainLyrics") val plainLyrics: String? = null,
        @SerialName("syncedLyrics") val syncedLyrics: String? = null
    )
}

/**
 * Parses an LRC string into timed [LyricLine]s. Handles multiple `[mm:ss.xx]` tags on
 * one text line (repeats the text per tag) and skips metadata tags like `[ar:...]`.
 * Output is sorted by start time.
 */
internal fun parseLrc(lrc: String): List<LyricLine> {
    val tag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    val out = ArrayList<LyricLine>()
    for (raw in lrc.lineSequence()) {
        val matches = tag.findAll(raw).toList()
        if (matches.isEmpty()) continue
        val text = raw.substring(matches.last().range.last + 1).trim()
        for (m in matches) {
            val min = m.groupValues[1].toLongOrNull() ?: 0
            val sec = m.groupValues[2].toLongOrNull() ?: 0
            val fracStr = m.groupValues[3]
            val frac = when {
                fracStr.isEmpty() -> 0L
                fracStr.length == 1 -> fracStr.toLong() * 100
                fracStr.length == 2 -> fracStr.toLong() * 10
                else -> fracStr.take(3).toLong()
            }
            val start = (min * 60 + sec) * 1000 + frac
            out.add(LyricLine(start = start, value = text))
        }
    }
    return out.sortedBy { it.start ?: 0 }
}
