package ie.adrianszydlo.navitunes

import ie.adrianszydlo.navitunes.data.api.SubsonicEnvelope
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the JSON contract with Navidrome. A renamed or mistyped field here
 * silently breaks browsing for the whole app, so we pin representative
 * responses captured from a real server.
 */
class EnvelopeParsingTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun parse(raw: String) = json.decodeFromString(SubsonicEnvelope.serializer(), raw).response

    @Test fun pingOk() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.52.0"}}"""
        )
        assertEquals("ok", r.status)
        assertEquals("1.16.1", r.version)
        assertEquals("navidrome", r.type)
    }

    @Test fun failedResponseSurfacesError() {
        val r = parse(
            """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong username or password"}}}"""
        )
        assertEquals("failed", r.status)
        assertNotNull(r.error)
        assertEquals(40, r.error!!.code)
        assertEquals("Wrong username or password", r.error!!.message)
    }

    @Test fun albumListParses() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","albumList2":{"album":[
               {"id":"al-1","name":"Chronos","artist":"Cepheid","coverArt":"al-1","songCount":8,"duration":2400,"year":2021},
               {"id":"al-2","name":"Contrast","artist":"Sara Fadda","songCount":12,"duration":3000}
            ]}}}"""
        )
        val albums = r.albumList2?.album.orEmpty()
        assertEquals(2, albums.size)
        assertEquals("Chronos", albums[0].name)
        assertEquals("Cepheid", albums[0].artist)
        assertEquals(8, albums[0].songCount)
        assertEquals(2021, albums[0].year)
        // Missing optional field defaults cleanly.
        assertNull(albums[1].year)
    }

    @Test fun songWithUnicodeAndMissingFields() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","randomSongs":{"song":[
               {"id":"s-1","title":"ココロナシ","artist":"Sara Fadda","album":"Contrast","duration":215,"suffix":"mp3"}
            ]}}}"""
        )
        val songs = r.randomSongs?.song.orEmpty()
        assertEquals(1, songs.size)
        assertEquals("ココロナシ", songs[0].title)
        assertEquals(215, songs[0].duration)
        assertEquals("mp3", songs[0].suffix)
        assertTrue(songs[0].starred.isNullOrBlank())
    }

    @Test fun unknownFieldsAreIgnored() {
        // Navidrome adds fields we don't model — must not throw.
        val r = parse(
            """{"subsonic-response":{"status":"ok","futureField":123,"album":{
               "id":"al-9","name":"X","artist":"Y","song":[
                 {"id":"s-9","title":"T","duration":100,"bitRate":320,"somethingNew":"x"}
               ]}}}"""
        )
        val album = r.album
        assertNotNull(album)
        assertEquals(1, album!!.song.size)
        assertEquals("T", album.song[0].title)
    }

    @Test fun starredSplitsSongsAndAlbums() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","starred2":{
               "song":[{"id":"s-1","title":"A","duration":10}],
               "album":[{"id":"al-1","name":"B","artist":"C","songCount":1,"duration":10}]
            }}}"""
        )
        assertEquals(1, r.starred2?.song?.size)
        assertEquals(1, r.starred2?.album?.size)
        assertEquals("A", r.starred2!!.song[0].title)
    }
}
