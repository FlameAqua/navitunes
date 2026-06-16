package ie.adrianszydlo.navitunes.ui.nav

object Routes {
    const val LOGIN = "login"
    const val PROFILES = "profiles"

    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"

    // Detail
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
    fun playlist(id: String) = "playlist/$id"
}
