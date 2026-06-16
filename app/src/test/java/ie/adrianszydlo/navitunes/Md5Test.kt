package ie.adrianszydlo.navitunes

import ie.adrianszydlo.navitunes.data.auth.md5Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Md5Test {

    @Test fun emptyString() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
    }

    @Test fun knownVectors() {
        assertEquals("0cc175b9c0f1b6a831c399e269772661", md5Hex("a"))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
        assertEquals(
            "9e107d9d372bb6826bd81d3542a419d6",
            md5Hex("The quick brown fox jumps over the lazy dog")
        )
    }

    @Test fun subsonicSpecExample() {
        // Subsonic API spec sample:
        //   password = "sesame", salt = "c19b2d" -> token = "26719a1196d2a940705a59634eb18eab"
        assertEquals("26719a1196d2a940705a59634eb18eab", md5Hex("sesame" + "c19b2d"))
    }

    @Test fun isDeterministic() {
        val input = "navidrome/secret"
        assertEquals(md5Hex(input), md5Hex(input))
    }

    @Test fun differentInputsDiffer() {
        assertNotEquals(md5Hex("a"), md5Hex("b"))
        assertNotEquals(md5Hex("abc"), md5Hex("abd"))
    }

    @Test fun lengthAndAlphabet() {
        val hash = md5Hex("anything")
        assertEquals(32, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun utf8IsByteAccurate() {
        // 'ü' is 2 UTF-8 bytes (C3 BC). Different from ISO-8859-1's 1 byte (FC).
        // Distinct from the ASCII-coerced "umlaut".
        assertNotEquals(md5Hex("ümlaut"), md5Hex("umlaut"))
    }
}
