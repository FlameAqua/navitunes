package ie.adrianszydlo.navitunes.data.auth

import java.security.MessageDigest

/**
 * Lower-case hex MD5 of a UTF-8 string.
 * Matches the PWA's `md5(pass + salt)` exactly.
 */
fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
        sb.append(HEX[b.toInt() and 0x0F])
    }
    return sb.toString()
}

private val HEX = "0123456789abcdef".toCharArray()
