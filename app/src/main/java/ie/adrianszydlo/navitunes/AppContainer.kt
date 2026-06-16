package ie.adrianszydlo.navitunes

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import ie.adrianszydlo.navitunes.data.api.ApiClient
import ie.adrianszydlo.navitunes.data.auth.ProfileStore
import ie.adrianszydlo.navitunes.data.offline.DownloadDb
import ie.adrianszydlo.navitunes.data.offline.DownloadRepository
import ie.adrianszydlo.navitunes.data.offline.OfflineResolver
import ie.adrianszydlo.navitunes.data.prefs.AppPreferences
import ie.adrianszydlo.navitunes.data.repo.LibraryRepository
import ie.adrianszydlo.navitunes.data.repo.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Single dependency container. Hand-wired — no DI framework.
 * Survives for the process lifetime; owned by [NavitunesApp].
 */
class AppContainer(private val appContext: Context) {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences: AppPreferences by lazy { AppPreferences(appContext) }
    val profileStore: ProfileStore by lazy { ProfileStore(appContext) }

    val apiClient: ApiClient by lazy { ApiClient(profileStore) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(apiClient) }
    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(apiClient, preferences) }

    val downloadDb: DownloadDb by lazy { DownloadDb.create(appContext) }
    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(appContext, downloadDb, apiClient, profileStore)
    }
    val offlineResolver: OfflineResolver by lazy { OfflineResolver(downloadRepository) }

    init {
        WorkManager.initialize(
            appContext,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
        )
    }

    /** Invoked when the active profile changes — flushes any per-profile cache. */
    fun onProfileSwitched() {
        libraryRepository.invalidate()
        apiClient.invalidate()
    }
}
