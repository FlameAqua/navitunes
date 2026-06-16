package ie.adrianszydlo.navitunes

import ie.adrianszydlo.navitunes.data.auth.SubsonicAuth
import ie.adrianszydlo.navitunes.data.auth.md5Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthTest {

    @Test fun paramsContainAllRequiredKeys() {
        val params = SubsonicAuth.params("adrian", "hunter2")
        for (key in listOf("u", "t", "s", "v", "c", "f")) {
            assertNotNull("missing $key", params[key])
        }
        assertEquals("adrian", params["u"])
        assertEquals(SubsonicAuth.PROTOCOL_VERSION, params["v"])
        assertEquals(SubsonicAuth.CLIENT_NAME, params["c"])
        assertEquals(SubsonicAuth.FORMAT, params["f"])
    }

    @Test fun tokenIsMd5OfPasswordPlusSalt() {
        val params = SubsonicAuth.params("adrian", "hunter2")
        assertEquals(md5Hex("hunter2" + params["s"]), params["t"])
    }

    @Test fun saltIsAtLeast8CharsAndAlnum() {
        val params = SubsonicAuth.params("u", "p")
        val s = params["s"]!!
        assertTrue("salt too short: $s", s.length >= 8)
        assertTrue("salt not alnum: $s", s.all { it in '0'..'9' || it in 'a'..'z' })
    }

    @Test fun saltsAreDistinctAcrossCalls() {
        val a = SubsonicAuth.params("u", "p")
        val b = SubsonicAuth.params("u", "p")
        // 36^10 collision space — practically zero chance of equality.
        assertNotEquals(a["s"], b["s"])
        assertNotEquals(a["t"], b["t"])
    }
}
