package ie.adrianszydlo.navitunes.data.auth

import java.security.SecureRandom

/**
 * Builds the Subsonic-style salted-token auth params, mirroring the PWA.
 *
 *     u  = username
 *     t  = md5(password + salt)
 *     s  = salt (random per request)
 *     v  = protocol version (1.16.1)
 *     c  = client name
 *     f  = response format
 */
object SubsonicAuth {
    const val PROTOCOL_VERSION = "1.16.1"
    const val CLIENT_NAME = "Navitunes"
    const val FORMAT = "json"

    private val rng = SecureRandom()
    private const val ALPHA = "abcdefghijklmnopqrstuvwxyz0123456789"

    private fun salt(length: Int = 10): String = buildString(length) {
        repeat(length) { append(ALPHA[rng.nextInt(ALPHA.length)]) }
    }

    /** Returns the six auth params to attach to every Subsonic request. */
    fun params(username: String, password: String): Map<String, String> {
        val s = salt()
        val t = md5Hex(password + s)
        return mapOf(
            "u" to username,
            "t" to t,
            "s" to s,
            "v" to PROTOCOL_VERSION,
            "c" to CLIENT_NAME,
            "f" to FORMAT
        )
    }
}
