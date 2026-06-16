package ie.adrianszydlo.navitunes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class NavitunesApp : Application(), Configuration.Provider, ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()

    /**
     * Single global ImageLoader. Sized generously for cover art — covers are
     * small (~50KB each) but we'd rather burn 96MB of RAM and 256MB of disk
     * than re-fetch them every time the user switches tabs.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // up to 25% of app memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024) // 256 MB
                    .build()
            }
            .respectCacheHeaders(false) // ignore Subsonic's "no-cache" — we own the eviction policy
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLAYBACK,
                getString(R.string.notif_channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_playback)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                getString(R.string.notif_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_downloads)
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_PLAYBACK = "navitunes_playback"
        const val CHANNEL_DOWNLOADS = "navitunes_downloads"

        private lateinit var instance: NavitunesApp
        fun container(): AppContainer = instance.container
    }
}
