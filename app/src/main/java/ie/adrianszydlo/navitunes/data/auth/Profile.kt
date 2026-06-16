package ie.adrianszydlo.navitunes.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = createdAt
) {
    /** Server URL guaranteed to NOT have a trailing slash. */
    val normalizedServer: String get() = serverUrl.trimEnd('/')
}
