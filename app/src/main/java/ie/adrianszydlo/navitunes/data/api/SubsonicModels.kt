package ie.adrianszydlo.navitunes.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse
)

@Serializable
data class SubsonicResponse(
    val status: String = "ok",
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val error: SubsonicError? = null,

    // Endpoint payloads (only one set per response).
    val albumList2: AlbumList? = null,
    val randomSongs: RandomSongs? = null,
    val artists: ArtistsIndex? = null,
    val artist: Artist? = null,
    val album: Album? = null,
    val playlists: PlaylistsWrapper? = null,
    val playlist: Playlist? = null,
    val starred2: Starred? = null,
    val searchResult3: SearchResult? = null
)

@Serializable
data class SubsonicError(val code: Int = 0, val message: String? = null)

@Serializable
data class AlbumList(val album: List<Album> = emptyList())

@Serializable
data class RandomSongs(val song: List<Song> = emptyList())

@Serializable
data class ArtistsIndex(val index: List<ArtistGroup> = emptyList())

@Serializable
data class ArtistGroup(val name: String = "", val artist: List<Artist> = emptyList())

@Serializable
data class PlaylistsWrapper(val playlist: List<Playlist> = emptyList())

@Serializable
data class Starred(
    val song: List<Song> = emptyList(),
    val album: List<Album> = emptyList(),
    val artist: List<Artist> = emptyList()
)

@Serializable
data class SearchResult(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList()
)

@Serializable
data class Album(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val song: List<Song> = emptyList()
)

@Serializable
data class Artist(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val album: List<Album> = emptyList()
)

@Serializable
data class Playlist(
    val id: String = "",
    val name: String = "",
    val comment: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val owner: String? = null,
    val public: Boolean? = null,
    val entry: List<Song> = emptyList()
)

@Serializable
data class Song(
    val id: String = "",
    val title: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val bitRate: Int? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val starred: String? = null,
    val isVideo: Boolean? = null,
    val size: Long? = null
)
