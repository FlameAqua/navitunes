package ie.adrianszydlo.navitunes.data.repo

import ie.adrianszydlo.navitunes.data.api.ApiClient
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Artist
import ie.adrianszydlo.navitunes.data.api.Playlist
import ie.adrianszydlo.navitunes.data.api.SearchResult
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.api.Starred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        api.call("getPlaylist.view", mapOf("id" to id)).playlist
            ?: error("Empty playlist response")
    }

    suspend fun starred(): Starred = withContext(Dispatchers.IO) {
        api.call("getStarred2.view").starred2 ?: Starred()
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
}
