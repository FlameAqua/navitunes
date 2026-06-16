package ie.adrianszydlo.navitunes.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.playback.PlayerController
import ie.adrianszydlo.navitunes.ui.detail.AlbumScreen
import ie.adrianszydlo.navitunes.ui.detail.ArtistScreen
import ie.adrianszydlo.navitunes.ui.detail.PlaylistScreen
import ie.adrianszydlo.navitunes.ui.downloads.DownloadsScreen
import ie.adrianszydlo.navitunes.ui.home.HomeScreen
import ie.adrianszydlo.navitunes.ui.library.LibraryScreen
import ie.adrianszydlo.navitunes.ui.login.LoginScreen
import ie.adrianszydlo.navitunes.ui.player.FullPlayerSheet
import ie.adrianszydlo.navitunes.ui.player.MiniPlayer
import ie.adrianszydlo.navitunes.ui.profile.ProfilePickerScreen
import ie.adrianszydlo.navitunes.ui.search.SearchScreen
import ie.adrianszydlo.navitunes.ui.settings.SettingsScreen
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.BorderCol
import ie.adrianszydlo.navitunes.ui.theme.Text3

/**
 * Top-level routing.
 *
 *   - No profiles                  → Login (initial setup)
 *   - Profiles exist but none active → ProfilePicker
 *   - Active profile               → Main shell (bottom nav)
 */
@Composable
fun RootNav() {
    val container = NavitunesApp.container()
    val profiles by container.profileStore.profiles.collectAsState()
    val activeId by container.profileStore.activeId.collectAsState()
    val context = LocalContext.current
    val controller = remember { PlayerController(context.applicationContext) }
    var addProfileMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { controller.bind() }
    DisposableEffect(Unit) {
        onDispose { controller.unbind() }
    }

    when {
        addProfileMode -> {
            val cancel: (() -> Unit)? = if (profiles.isNotEmpty()) {
                { addProfileMode = false }
            } else null
            LoginScreen(
                onLoggedIn = { addProfileMode = false },
                onCancel = cancel
            )
        }

        profiles.isEmpty() ->
            LoginScreen(onLoggedIn = { /* state reacts automatically */ })

        activeId == null ->
            ProfilePickerScreen(
                onProfileSelected = { /* state reacts */ },
                onAddProfile = { addProfileMode = true }
            )

        else -> MainShell(
            controller = controller,
            onAddProfile = { addProfileMode = true }
        )
    }
}

@Composable
private fun MainShell(controller: PlayerController, onAddProfile: () -> Unit) {
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route
    var showFullPlayer by remember { mutableStateOf(false) }
    val current by controller.currentItem.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomBar(
                currentRoute = currentRoute,
                onSelect = { route ->
                    if (currentRoute != route) {
                        nav.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onPlay = { songs, idx -> controller.play(songs, idx) }
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onArtist = { nav.navigate(Routes.artist(it)) },
                        onPlaylist = { nav.navigate(Routes.playlist(it)) },
                        onPlay = { songs, idx -> controller.play(songs, idx) }
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onAlbum = { nav.navigate(Routes.album(it)) },
                        onArtist = { nav.navigate(Routes.artist(it)) },
                        onPlay = { songs, idx -> controller.play(songs, idx) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onAddProfile = onAddProfile,
                        onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) }
                    )
                }
                composable(Routes.DOWNLOADS) { DownloadsScreen(onBack = { nav.popBackStack() }) }
                composable(
                    Routes.ALBUM,
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) {
                    val id = it.arguments?.getString("id").orEmpty()
                    AlbumScreen(
                        id = id,
                        onBack = { nav.popBackStack() },
                        onPlay = { songs, idx -> controller.play(songs, idx) }
                    )
                }
                composable(
                    Routes.ARTIST,
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) {
                    val id = it.arguments?.getString("id").orEmpty()
                    ArtistScreen(
                        id = id,
                        onBack = { nav.popBackStack() },
                        onAlbum = { aid -> nav.navigate(Routes.album(aid)) }
                    )
                }
                composable(
                    Routes.PLAYLIST,
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) {
                    val id = it.arguments?.getString("id").orEmpty()
                    PlaylistScreen(
                        id = id,
                        onBack = { nav.popBackStack() },
                        onPlay = { songs, idx -> controller.play(songs, idx) }
                    )
                }
            }

            // Mini-player floats above the bottom nav, anchored to the bottom of the scaffold content area.
            if (current != null) {
                MiniPlayer(
                    controller = controller,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    onExpand = { showFullPlayer = true }
                )
            }
        }
    }

    if (showFullPlayer && current != null) {
        FullPlayerSheet(controller = controller, onDismiss = { showFullPlayer = false })
    }
}

@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple(Routes.HOME, Icons.Outlined.Home, "Home"),
        Triple(Routes.LIBRARY, Icons.Outlined.LibraryMusic, "Library"),
        Triple(Routes.SEARCH, Icons.Outlined.Search, "Search"),
        Triple(Routes.SETTINGS, Icons.Outlined.Settings, "Settings")
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        items.forEach { (route, icon, label) ->
            val selected = currentRoute == route || (currentRoute == null && route == Routes.HOME)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    unselectedIconColor = Text3,
                    unselectedTextColor = Text3,
                    indicatorColor = BorderCol
                )
            )
        }
    }
}
