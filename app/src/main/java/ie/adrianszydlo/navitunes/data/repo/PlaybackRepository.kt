package ie.adrianszydlo.navitunes.data.repo

import ie.adrianszydlo.navitunes.data.api.ApiClient
import ie.adrianszydlo.navitunes.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Subsonic write/side-effect endpoints: scrobble + star/unstar. */
class PlaybackRepository(
    private val api: ApiClient,
    @Suppress("unused") private val preferences: AppPreferences
) {

    suspend fun scrobble(songId: String, submission: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            api.call("scrobble.view", mapOf("id" to songId, "submission" to submission.toString()))
        }
    }

    suspend fun star(songId: String) = withContext(Dispatchers.IO) {
        api.call("star.view", mapOf("id" to songId))
    }

    suspend fun unstar(songId: String) = withContext(Dispatchers.IO) {
        api.call("unstar.view", mapOf("id" to songId))
    }
}
