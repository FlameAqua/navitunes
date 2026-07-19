package ie.adrianszydlo.navitunes.data.discovery

import android.util.Base64
import android.util.Log
import ie.adrianszydlo.navitunes.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Which kind of Spotify entity to search for. */
enum class SpotifyType(val apiValue: String, val label: String) {
    TRACK("track", "Tracks"),
    ALBUM("album", "Albums"),
    ARTIST("artist", "Artists"),
    PLAYLIST("playlist", "Playlists")
}

/** A single discovery result (track, album, or artist), shaped for the UI. */
data class SpotifyResult(
    val id: String,
    val type: SpotifyType,
    val title: String,          // track/album/artist name
    val subtitle: String,       // artist line, or "Artist" for artist results
    val coverUrl: String?,
    val clipboardLine: String,  // what gets copied on tap
    val matchArtist: String     // raw artist text used for library dedup ("" for artists)
)

/**
 * Metadata-only Spotify Web API client (Client Credentials flow — no user
 * login). Surfaces "you don't have this; here's what it is" results. Never
 * fetches or plays audio; tapping a result copies text to the clipboard.
 */
class SpotifyClient(
    private val okHttp: OkHttpClient,
    private val preferences: AppPreferences
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0L

    // When Spotify 429s, we back off until this timestamp before trying again.
    @Volatile private var rateLimitedUntilMs: Long = 0L

    suspend fun isConfigured(): Boolean =
        !preferences.spotifyClientId.first().isNullOrBlank() &&
            !preferences.spotifyClientSecret.first().isNullOrBlank()

    /** Settings "Test" button — reports exactly what happened, no logcat needed. */
    suspend fun diagnose(): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext "Not configured — enter a Client ID and Secret."
        val id = preferences.spotifyClientId.first()?.trim().orEmpty()
        val secret = preferences.spotifyClientSecret.first()?.trim().orEmpty()
        val basic = Base64.encodeToString("$id:$secret".toByteArray(), Base64.NO_WRAP)
        val tokenReq = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", "Basic $basic")
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()
        val tok = try {
            okHttp.newCall(tokenReq).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    return@withContext "Auth failed (HTTP ${resp.code}). " +
                        if (body.contains("invalid_client")) "Client ID/Secret is wrong." else body.take(120)
                }
                json.decodeFromString(SpotifyTokenResponse.serializer(), body).access_token
            }
        } catch (t: Throwable) {
            return@withContext "Network error reaching Spotify: ${t.message ?: t.javaClass.simpleName}. " +
                "If you use Pi-hole, allowlist accounts.spotify.com and api.spotify.com."
        }
        if (tok.isNullOrBlank()) return@withContext "Auth returned no token."
        val results = search("test", SpotifyType.TRACK, limit = 1)
        "Connected to Spotify. Search returned ${results.size} result(s)."
    }

    /** Search a single entity type. Honors a 429 back-off and the dev-mode limit clamp. */
    suspend fun search(
        query: String,
        type: SpotifyType,
        limit: Int = DEFAULT_LIMIT
    ): List<SpotifyResult> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now < rateLimitedUntilMs) {
            Log.w(TAG, "skipping; rate-limited for ${(rateLimitedUntilMs - now) / 1000}s more")
            return@withContext emptyList()
        }
        val token = token() ?: run {
            Log.w(TAG, "search: no access token (check credentials / network)")
            return@withContext emptyList()
        }

        // Dev-mode apps reject larger limits with 400 "Invalid limit"; step down.
        val ladder = listOf(limit.coerceIn(1, MAX_LIMIT), 5, 1).distinct()
        for (attempt in ladder) {
            val url = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("type", type.apiValue)
                .addQueryParameter("limit", attempt.toString())
                .build()
            val request = Request.Builder()
                .url(url).header("Authorization", "Bearer $token").get().build()

            val result: List<SpotifyResult>? = try {
                okHttp.newCall(request).execute().use { resp ->
                    val body = resp.body.string()
                    when {
                        resp.isSuccessful -> {
                            val items = parse(type, body)
                            Log.i(TAG, "search ${type.apiValue} \"$query\" (limit=$attempt) -> ${items.size}")
                            items
                        }
                        resp.code == 429 -> {
                            val retry = resp.header("Retry-After")?.toLongOrNull() ?: 30L
                            rateLimitedUntilMs = System.currentTimeMillis() + retry * 1000L
                            Log.w(TAG, "429 rate-limited; backing off ${retry}s")
                            return@withContext emptyList()
                        }
                        resp.code == 400 && body.contains("limit", ignoreCase = true) -> {
                            Log.w(TAG, "limit=$attempt rejected; retrying smaller")
                            null
                        }
                        else -> {
                            Log.w(TAG, "search HTTP ${resp.code}: ${body.take(200)}")
                            return@withContext emptyList()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "search failed", t)
                return@withContext emptyList()
            }
            if (result != null) return@withContext result
        }
        emptyList()
    }

    /**
     * Expands an album / artist / playlist into its individual tracks (as TRACK
     * results), so the UI can offer per-track downloads instead of one giant
     * server-side fetch. A TRACK entity returns itself. Empty on any failure.
     */
    suspend fun entityTracks(entity: SpotifyResult): List<SpotifyResult> = withContext(Dispatchers.IO) {
        if (entity.type == SpotifyType.TRACK) return@withContext listOf(entity)
        val token = token() ?: return@withContext emptyList()

        fun track(id: String, name: String, artists: List<NameDto>, cover: String?): SpotifyResult {
            val artistLine = artists.joinToString(", ") { it.name }
            return SpotifyResult(
                id = id,
                type = SpotifyType.TRACK,
                title = name,
                subtitle = artistLine,
                coverUrl = cover,
                clipboardLine = if (artistLine.isBlank()) name else "$artistLine - $name",
                matchArtist = artistLine
            )
        }

        runCatching {
            when (entity.type) {
                SpotifyType.ALBUM -> {
                    val body = get("https://api.spotify.com/v1/albums/${entity.id}/tracks?limit=50", token)
                        ?: return@withContext emptyList()
                    json.decodeFromString(AlbumTracksResponse.serializer(), body).items
                        .filter { it.id.isNotBlank() }
                        .map { track(it.id, it.name, it.artists, entity.coverUrl) }
                }
                SpotifyType.PLAYLIST -> {
                    val body = get("https://api.spotify.com/v1/playlists/${entity.id}/tracks?limit=100", token)
                        ?: return@withContext emptyList()
                    json.decodeFromString(PlaylistTracksResponse.serializer(), body).items
                        .mapNotNull { it.track }
                        .filter { it.id.isNotBlank() }
                        .map { track(it.id, it.name, it.artists, pickImage(it.album?.images) ?: entity.coverUrl) }
                }
                SpotifyType.ARTIST -> {
                    val body = get("https://api.spotify.com/v1/artists/${entity.id}/top-tracks?market=US", token)
                        ?: return@withContext emptyList()
                    json.decodeFromString(ArtistTopTracksResponse.serializer(), body).tracks
                        .filter { it.id.isNotBlank() }
                        .map { track(it.id, it.name, it.artists, pickImage(it.album?.images)) }
                }
            }
        }.getOrElse {
            Log.e(TAG, "entityTracks failed for ${entity.type}:${entity.id}", it)
            emptyList()
        }
    }

    /** Authenticated GET returning the body string, or null on 429/error. */
    private fun get(url: String, token: String): String? {
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        return runCatching {
            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "GET $url -> HTTP ${resp.code}: ${body.take(160)}")
                    null
                } else body
            }
        }.getOrNull()
    }

    private fun parse(type: SpotifyType, body: String): List<SpotifyResult> {
        val resp = json.decodeFromString(SpotifySearchResponse.serializer(), body)
        return when (type) {
            SpotifyType.TRACK -> resp.tracks?.items.orEmpty().map { t ->
                val artistLine = t.artists.joinToString(", ") { it.name }
                SpotifyResult(
                    id = t.id,
                    type = type,
                    title = t.name,
                    subtitle = listOfNotNull(artistLine.ifBlank { null }, t.album?.name?.ifBlank { null })
                        .joinToString(" · "),
                    coverUrl = pickImage(t.album?.images),
                    clipboardLine = if (artistLine.isBlank()) t.name else "$artistLine - ${t.name}",
                    matchArtist = artistLine
                )
            }
            SpotifyType.ALBUM -> resp.albums?.items.orEmpty().map { a ->
                val artistLine = a.artists.joinToString(", ") { it.name }
                SpotifyResult(
                    id = a.id,
                    type = type,
                    title = a.name,
                    subtitle = artistLine,
                    coverUrl = pickImage(a.images),
                    clipboardLine = if (artistLine.isBlank()) a.name else "$artistLine - ${a.name}",
                    matchArtist = artistLine
                )
            }
            SpotifyType.ARTIST -> resp.artists?.items.orEmpty().map { ar ->
                SpotifyResult(
                    id = ar.id,
                    type = type,
                    title = ar.name,
                    subtitle = "Artist",
                    coverUrl = pickImage(ar.images),
                    clipboardLine = ar.name,
                    matchArtist = ""
                )
            }
            // Spotify's playlist search can include null entries (deleted playlists),
            // so filter them out.
            SpotifyType.PLAYLIST -> resp.playlists?.items.orEmpty().filterNotNull().map { p ->
                val owner = p.owner?.display_name.orEmpty()
                SpotifyResult(
                    id = p.id,
                    type = type,
                    title = p.name,
                    subtitle = if (owner.isBlank()) "Playlist" else "Playlist · $owner",
                    coverUrl = pickImage(p.images),
                    clipboardLine = p.name,
                    matchArtist = ""
                )
            }
        }
    }

    /** Images come largest-first; the middle one is a good thumbnail size. */
    private fun pickImage(images: List<ImageDto>?): String? =
        images?.let { it.getOrNull(1) ?: it.firstOrNull() }?.url

    private suspend fun token(): String? = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiryMs - 5_000) return it }

        val id = preferences.spotifyClientId.first()?.trim().orEmpty()
        val secret = preferences.spotifyClientSecret.first()?.trim().orEmpty()
        if (id.isBlank() || secret.isBlank()) return null

        val basic = Base64.encodeToString("$id:$secret".toByteArray(), Base64.NO_WRAP)
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", "Basic $basic")
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()

        try {
            okHttp.newCall(request).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "token HTTP ${resp.code}: ${body.take(200)}")
                    return null
                }
                val tok = json.decodeFromString(SpotifyTokenResponse.serializer(), body)
                cachedToken = tok.access_token
                tokenExpiryMs = now + (tok.expires_in ?: 3600) * 1000L
                cachedToken
            }
        } catch (t: Throwable) {
            Log.e(TAG, "token request failed", t)
            null
        }
    }

    private companion object {
        const val TAG = "Navitunes/Spotify"
        // Dev-mode Spotify apps reject larger search limits with 400 "Invalid limit".
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 50
    }

    @Serializable
    private data class SpotifyTokenResponse(
        val access_token: String? = null,
        val expires_in: Int? = 3600
    )

    @Serializable
    private data class SpotifySearchResponse(
        val tracks: TracksWrapper? = null,
        val albums: AlbumsWrapper? = null,
        val artists: ArtistsWrapper? = null,
        val playlists: PlaylistsWrapper? = null
    )

    @Serializable private data class TracksWrapper(val items: List<TrackDto> = emptyList())
    @Serializable private data class AlbumsWrapper(val items: List<AlbumDto> = emptyList())
    @Serializable private data class ArtistsWrapper(val items: List<ArtistDto> = emptyList())

    // Items can be null in playlist search responses.
    @Serializable private data class PlaylistsWrapper(val items: List<PlaylistDto?> = emptyList())

    @Serializable
    private data class PlaylistDto(
        val id: String = "",
        val name: String = "",
        val images: List<ImageDto> = emptyList(),
        val owner: OwnerDto? = null
    )

    @Serializable private data class OwnerDto(val display_name: String = "")

    @Serializable
    private data class TrackDto(
        val id: String = "",
        val name: String = "",
        val artists: List<NameDto> = emptyList(),
        val album: AlbumRefDto? = null
    )

    @Serializable
    private data class AlbumDto(
        val id: String = "",
        val name: String = "",
        val artists: List<NameDto> = emptyList(),
        val images: List<ImageDto> = emptyList()
    )

    @Serializable
    private data class ArtistDto(
        val id: String = "",
        val name: String = "",
        val images: List<ImageDto> = emptyList()
    )

    @Serializable
    private data class AlbumRefDto(
        val name: String = "",
        val images: List<ImageDto> = emptyList()
    )

    @Serializable private data class NameDto(val name: String = "")
    @Serializable private data class ImageDto(val url: String = "")

    // Track-listing responses (album/artist/playlist expansion).
    @Serializable
    private data class SimpleTrackDto(
        val id: String = "",
        val name: String = "",
        val artists: List<NameDto> = emptyList(),
        val album: AlbumRefDto? = null
    )

    @Serializable private data class AlbumTracksResponse(val items: List<SimpleTrackDto> = emptyList())
    @Serializable private data class ArtistTopTracksResponse(val tracks: List<SimpleTrackDto> = emptyList())
    @Serializable private data class PlaylistItemDto(val track: SimpleTrackDto? = null)
    @Serializable private data class PlaylistTracksResponse(val items: List<PlaylistItemDto> = emptyList())
}
