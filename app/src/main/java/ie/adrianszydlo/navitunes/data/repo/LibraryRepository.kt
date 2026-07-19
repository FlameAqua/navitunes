package ie.adrianszydlo.navitunes.data.repo

import ie.adrianszydlo.navitunes.data.api.ApiClient
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.SearchResult
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.api.Starred
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PL_TAG = "Navitunes/PL"

/**
 * Read-only library operations. All methods run on IO.
 * Mirrors the call surface of the PWA exactly.
 */
class LibraryRepository(private val api: ApiClient) {

    fun invalidate() { /* room for an LRU later */ }

    suspend fun ping(): String? = withContext(Dispatchers.IO) {
        api.call("ping.view").version
    }

    suspend fun albumList(type: String, size: Int = 20): List<Album> = withContext(Dispatchers.IO) {
        api.call("getAlbumList2.view", mapOf("type" to type, "size" to size.toString()))
            .albumList2?.album.orEmpty()
    }

    suspend fun randomSongs(size: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        api.call("getRandomSongs.view", mapOf("size" to size.toString()))
            .randomSongs?.song.orEmpty()
    }

    suspend fun allArtists(): List<Artist> = withContext(Dispatchers.IO) {
        api.call("getArtists.view").artists?.index.orEmpty().flatMap { it.artist }
    }

    suspend fun artist(id: String): Artist = withContext(Dispatchers.IO) {
        api.call("getArtist.view", mapOf("id" to id)).artist
            ?: error("Empty artist response")
    }

    suspend fun album(id: String): Album = withContext(Dispatchers.IO) {
        api.call("getAlbum.view", mapOf("id" to id)).album
            ?: error("Empty album response")
    }

    suspend fun allPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        api.call("getPlaylists.view").playlists?.playlist.orEmpty()
    }

    suspend fun playlist(id: String): Playlist = withContext(Dispatchers.IO) {
        val p = api.call("getPlaylist.view", mapOf("id" to id)).playlist
            ?: error("Empty playlist response")
        Log.d(PL_TAG, "getPlaylist $id -> songCount=${p.songCount} entries=${p.entry.size} ids=${p.entry.map { it.id }}")
        p
    }

    suspend fun starred(): Starred = withContext(Dispatchers.IO) {
        api.call("getStarred2.view").starred2 ?: Starred()
    }

    /** All genres in the library, most-used first. */
    suspend fun genres(): List<ie.adrianszydlo.navitunes.data.api.GenreEntry> = withContext(Dispatchers.IO) {
        api.call("getGenres.view").genres?.genre.orEmpty()
            .filter { it.value.isNotBlank() }
            .sortedByDescending { it.songCount }
    }

    /** Songs tagged with [genre]. */
    suspend fun songsByGenre(genre: String, count: Int = 300): List<Song> = withContext(Dispatchers.IO) {
        api.call("getSongsByGenre.view", mapOf("genre" to genre, "count" to count.toString()))
            .songsByGenre?.song.orEmpty()
    }

    /** Full metadata for one song (getSong.view returns more fields than list endpoints). */
    suspend fun song(id: String): Song? = withContext(Dispatchers.IO) {
        runCatching { api.call("getSong.view", mapOf("id" to id)).song }.getOrNull()
    }

    /**
     * Synced/plain lyrics for a song via the OpenSubsonic getLyricsBySongId endpoint.
     * Returns an empty list if the server doesn't support it or has no lyrics.
     */
    suspend fun lyricsBySongId(id: String): List<ie.adrianszydlo.navitunes.data.api.StructuredLyrics> =
        withContext(Dispatchers.IO) {
            runCatching {
                api.call("getLyricsBySongId.view", mapOf("id" to id)).lyricsList?.structuredLyrics
            }.getOrNull().orEmpty()
        }

    /** Plain lyrics via the classic getLyrics endpoint (artist + title lookup). */
    suspend fun plainLyrics(artist: String?, title: String?): String? = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildMap {
                if (!artist.isNullOrBlank()) put("artist", artist)
                if (!title.isNullOrBlank()) put("title", title)
            }
            api.call("getLyrics.view", params).lyrics?.value?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        api.call(
            "search3.view",
            mapOf(
                "query" to query,
                "artistCount" to "10",
                "albumCount" to "20",
                "songCount" to "30"
            )
        ).searchResult3 ?: SearchResult()
    }

    /** Creates a new (empty) playlist on the server. */
    suspend fun createPlaylist(name: String): Playlist = withContext(Dispatchers.IO) {
        api.call("createPlaylist.view", mapOf("name" to name)).playlist
            ?: api.call("getPlaylists.view").playlists?.playlist?.firstOrNull { it.name == name }
            ?: error("Failed to create playlist")
    }

    /** Appends [songIds] to an existing playlist. */
    suspend fun addToPlaylist(playlistId: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        // Subsonic's updatePlaylist.view repeats songIdToAdd for each id — we pass
        // a comma-joined string and the server splits it via the standard query
        // parser. To stay portable we instead chain individual calls.
        songIds.forEach { id ->
            api.call(
                "updatePlaylist.view",
                mapOf("playlistId" to playlistId, "songIdToAdd" to id)
            )
        }
    }

    /**
     * Overwrites a playlist's contents with exactly [songIds], in order.
     *
     * Used for removal and repair: index-based removal (`songIndexToRemove`) is
     * unreliable once a playlist contains entries whose songs were deleted from
     * the library — the server still counts those "orphans" but omits them from
     * `getPlaylist`, so the client's indices no longer line up with the server's.
     * Rewriting from the known-good entry ids removes the target *and* drops the
     * orphans in one call, re-syncing `songCount` with reality.
     */
    suspend fun setPlaylistSongs(playlistId: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        // createPlaylist with an empty song set is a no-op on Navidrome, so emptying
        // a playlist must go through updatePlaylist/songIndexToRemove instead.
        if (songIds.isEmpty()) {
            error("setPlaylistSongs requires at least one song; use clearPlaylist to empty")
        }
        val params = buildList {
            add("playlistId" to playlistId)
            songIds.forEach { add("songId" to it) }
        }
        Log.d(PL_TAG, "setPlaylistSongs $playlistId -> ${songIds.size} ids=$songIds")
        api.call("createPlaylist.view", params)
        Unit
    }

    /**
     * Empties a playlist by removing every entry by index. Used when a removal (or
     * cleanup) would leave zero songs — the one case [setPlaylistSongs] can't cover.
     * Removes [totalCount] indices (the server's full count, orphans included) in
     * descending order so it's correct whether the server treats the indices as a
     * set or applies them sequentially.
     */
    suspend fun clearPlaylist(playlistId: String, totalCount: Int) = withContext(Dispatchers.IO) {
        if (totalCount <= 0) return@withContext
        val params = buildList {
            add("playlistId" to playlistId)
            for (i in (totalCount - 1) downTo 0) add("songIndexToRemove" to i.toString())
        }
        Log.d(PL_TAG, "clearPlaylist $playlistId -> removing $totalCount indices")
        api.call("updatePlaylist.view", params)
        Unit
    }

    /** Deletes the playlist with the given id. */
    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        api.call("deletePlaylist.view", mapOf("id" to playlistId))
        Unit
    }

    /**
     * Removes [songId] from every playlist that contains it. **Must be called while
     * the song still exists in the library** (i.e. *before* deleting it), because
     * Navidrome retains a hidden playlist membership once the media is gone —
     * `getPlaylist` then omits the song entirely, so it can't be found or removed
     * afterwards (and re-downloading the song revives it). Removing it up front,
     * while it's still a visible entry, deletes the membership for good.
     *
     * Best-effort per playlist; never throws.
     */
    suspend fun removeSongFromAllPlaylists(songId: String) = withContext(Dispatchers.IO) {
        val playlists = runCatching { allPlaylists() }.getOrDefault(emptyList())
        for (pl in playlists) {
            val full = runCatching { playlist(pl.id) }.getOrNull() ?: continue
            val ids = full.entry.map { it.id }
            if (songId !in ids) continue
            val keep = ids.filterNot { it == songId }
            Log.d(PL_TAG, "removeFromAll ${pl.id} name='${pl.name}': dropping $songId, keep=${keep.size}")
            runCatching {
                if (keep.isEmpty()) clearPlaylist(pl.id, full.songCount)
                else setPlaylistSongs(pl.id, keep)
            }
        }
    }
}
