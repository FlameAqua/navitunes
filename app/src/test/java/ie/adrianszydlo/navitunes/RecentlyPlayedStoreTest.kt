package ie.adrianszydlo.navitunes

import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.prefs.RecentlyPlayedStore.Companion.deduplicate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentlyPlayedStoreTest {

    @Test
    fun `deduplicate keeps the newest entry after a Navidrome rescan changes song id and artist separators`() {
        val fresh = Song(id = "new-id", title = "Borderline", artist = "Ely Oaks · LAVINIA")
        val stale = Song(id = "old-id", title = "Borderline", artist = "Ely Oaks/LAVINIA")

        val result = listOf(fresh, stale).deduplicate()

        assertEquals(listOf(fresh), result)
    }

    @Test
    fun `deduplicate keeps distinct songs with the same title by different artists`() {
        val first = Song(id = "1", title = "Ghost", artist = "Justin Bieber")
        val second = Song(id = "2", title = "Ghost", artist = "The Weeknd")

        assertEquals(listOf(first, second), listOf(first, second).deduplicate())
    }
}
