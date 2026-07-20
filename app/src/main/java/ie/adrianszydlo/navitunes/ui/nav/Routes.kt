package ie.adrianszydlo.navitunes.ui.nav

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val RADIO = "radio"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val DOWNLOAD_MANAGER = "download_manager"

    // Detail
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
    fun playlist(id: String) = "playlist/$id"
}
