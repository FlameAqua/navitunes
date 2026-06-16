package ie.adrianszydlo.navitunes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NavitunesApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    companion object {
        const val CHANNEL_PLAYBACK = "navitunes_playback"
        const val CHANNEL_DOWNLOADS = "navitunes_downloads"

        private lateinit var instance: NavitunesApp
        fun container(): AppContainer = instance.container
    }
}
