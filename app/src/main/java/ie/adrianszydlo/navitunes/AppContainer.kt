package ie.adrianszydlo.navitunes

import android.content.Context
import ie.adrianszydlo.navitunes.data.LibrarySignals
import ie.adrianszydlo.navitunes.data.api.ApiClient
import ie.adrianszydlo.navitunes.data.auth.ProfileStore
import ie.adrianszydlo.navitunes.data.discovery.SpotifyClient
import ie.adrianszydlo.navitunes.data.offline.DownloadDb
import ie.adrianszydlo.navitunes.data.offline.DownloadRepository
import ie.adrianszydlo.navitunes.data.offline.OfflineResolver
import ie.adrianszydlo.navitunes.data.prefs.AppPreferences
import ie.adrianszydlo.navitunes.data.prefs.RecentlyPlayedStore
import ie.adrianszydlo.navitunes.data.remote.DownloadManager
import ie.adrianszydlo.navitunes.data.remote.DownloadService
import ie.adrianszydlo.navitunes.data.remote.MetadataFixService
import ie.adrianszydlo.navitunes.data.update.ApkInstaller
import ie.adrianszydlo.navitunes.data.update.UpdateService
import ie.adrianszydlo.navitunes.data.upload.UploadService
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
    val recentlyPlayedStore: RecentlyPlayedStore by lazy { RecentlyPlayedStore(appContext) }
    val profileStore: ProfileStore by lazy { ProfileStore(appContext) }
    val librarySignals: LibrarySignals = LibrarySignals()

    val apiClient: ApiClient by lazy { ApiClient(profileStore) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(apiClient) }
    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(apiClient) }

    val downloadDb: DownloadDb by lazy { DownloadDb.create(appContext) }
    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(appContext, downloadDb, apiClient, profileStore)
    }
    val offlineResolver: OfflineResolver by lazy { OfflineResolver(downloadRepository) }

    val uploadService: UploadService by lazy { UploadService(appContext, apiClient) }

    val spotifyClient: SpotifyClient by lazy { SpotifyClient(apiClient.okHttp, preferences) }

    val downloadService: DownloadService by lazy {
        DownloadService(apiClient.okHttp, profileStore, preferences)
    }
    val downloadManager: DownloadManager by lazy {
        DownloadManager(appScope, downloadService, librarySignals)
    }

    val metadataFixService: MetadataFixService by lazy {
        MetadataFixService(apiClient.okHttp, profileStore, preferences)
    }

    val updateService: UpdateService by lazy { UpdateService(apiClient.okHttp) }
    val apkInstaller: ApkInstaller by lazy { ApkInstaller(apiClient.okHttp) }

    /** Invoked when the active profile changes — flushes any per-profile cache. */
    fun onProfileSwitched() {
        libraryRepository.invalidate()
        apiClient.invalidate()
    }
}
