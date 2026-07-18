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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.playback.PlayerController
import ie.adrianszydlo.navitunes.ui.detail.AlbumScreen
import ie.adrianszydlo.navitunes.ui.detail.ArtistScreen
import ie.adrianszydlo.navitunes.ui.detail.PlaylistScreen
import ie.adrianszydlo.navitunes.ui.downloads.DownloadManagerScreen
import ie.adrianszydlo.navitunes.ui.downloads.DownloadsScreen
import ie.adrianszydlo.navitunes.ui.home.HomeScreen
import ie.adrianszydlo.navitunes.ui.library.LibraryScreen
import ie.adrianszydlo.navitunes.ui.login.LoginScreen
import ie.adrianszydlo.navitunes.ui.player.FullPlayerSheet
import ie.adrianszydlo.navitunes.ui.player.MiniPlayer
import ie.adrianszydlo.navitunes.ui.profile.ProfilePickerScreen
import ie.adrianszydlo.navitunes.ui.search.SearchScreen
import ie.adrianszydlo.navitunes.ui.settings.SettingsScreen
import ie.adrianszydlo.navitunes.data.update.UpdateStatus
import ie.adrianszydlo.navitunes.ui.update.UpdateAvailableDialog
import ie.adrianszydlo.navitunes.ui.theme.Accent
import kotlinx.coroutines.flow.first
import ie.adrianszydlo.navitunes.ui.theme.BorderCol
import ie.adrianszydlo.navitunes.ui.theme.Text3

/** How often to silently refresh the visible screen while foregrounded. */
private const val AUTO_REFRESH_INTERVAL_MS = 10_000L

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
    val container = NavitunesApp.container()
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForRemoval by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var ambiguousRemoval by remember {
        mutableStateOf<Pair<Song, ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Ambiguous>?>(null)
    }
    var updateAvailable by remember { mutableStateOf<UpdateStatus.Available?>(null) }

    val openPicker: (Song) -> Unit = remember { { song -> songForPlaylist = song } }
    val openRemove: (Song) -> Unit = remember { { song -> songForRemoval = song } }
    val openInfo: (Song) -> Unit = remember { { song -> songForInfo = song } }

    val downloadedIds by container.downloadRepository
        .observeDownloadedIdsForActiveProfile()
        .collectAsState(initial = emptySet())
    val uploadEndpoint by container.preferences.uploadEndpoint.collectAsState(initial = null)
    val activeProfileId by container.profileStore.activeId.collectAsState()

    // Rebuild the offline index from disk on launch / profile change, so
    // downloads in Music/Navitunes survive an app wipe or reinstall.
    LaunchedEffect(activeProfileId) {
        container.downloadRepository.reconcile()
        container.librarySignals.notifyChanged()
    }

    // Silent auto-refresh: while the app is in the foreground, tick the
    // library-changed signal periodically so screens pick up server-side changes
    // (e.g. a just-finished download) without a manual refresh. Screens swap the
    // new data in silently — no spinner, no visible reload. Paused when the app
    // is backgrounded (repeatOnLifecycle), so it never polls needlessly.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                container.librarySignals.notifyChanged()
            }
        }
    }

    // Check GitHub for a newer release once per launch. Respects a version the
    // user chose to skip; the manual check in Settings ignores that skip.
    LaunchedEffect(Unit) {
        val skipped = container.preferences.skippedUpdateVersion.first()
        val status = container.updateService.check()
        if (status is UpdateStatus.Available && status.versionName != skipped) {
            updateAvailable = status
        }
    }

    CompositionLocalProvider(
        LocalPlayerController provides controller,
        LocalAddToPlaylistRequest provides openPicker,
        LocalRemoveSongRequest provides openRemove,
        LocalSongInfoRequest provides openInfo,
        LocalDownloadedIds provides downloadedIds
    ) {
        MainShellInner(controller = controller, onAddProfile = onAddProfile)

        songForPlaylist?.let { song ->
            AddToPlaylistDialog(song = song, onDismiss = { songForPlaylist = null })
        }

        songForInfo?.let { song ->
            SongInfoDialog(song = song, onDismiss = { songForInfo = null })
        }

        songForRemoval?.let { song ->
            ie.adrianszydlo.navitunes.ui.common.ConfirmDialog(
                title = "Remove \"${song.title}\" from library?",
                message = "This permanently deletes the audio file from your server. " +
                    "Make sure you actually want it gone.",
                confirmLabel = "Remove",
                destructive = true,
                onConfirm = {
                    songForRemoval = null
                    scope.launch {
                        // Drop the song from any playlist *before* deleting it — once the
                        // media is gone Navidrome keeps a hidden playlist membership that
                        // can't be removed afterwards (and re-downloading revives it).
                        runCatching { container.libraryRepository.removeSongFromAllPlaylists(song.id) }
                        // Blank endpoint → UploadService falls back to the profile's server.
                        val result = container.uploadService.removeFromLibrary(song.id, uploadEndpoint.orEmpty())
                        when (result) {
                            is ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Success -> {
                                onSongRemoved(container, controller, song)
                                android.widget.Toast.makeText(ctx, result.message, android.widget.Toast.LENGTH_LONG).show()
                            }
                            is ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Failure ->
                                android.widget.Toast.makeText(ctx, result.message, android.widget.Toast.LENGTH_LONG).show()
                            is ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Ambiguous ->
                                ambiguousRemoval = song to result
                        }
                    }
                },
                onDismiss = { songForRemoval = null }
            )
        }

        ambiguousRemoval?.let { (song, ambig) ->
            RemoveCandidatePicker(
                songTitle = song.title,
                candidates = ambig.candidates,
                onPick = { picked ->
                    scope.launch {
                        val result = container.uploadService.removeFromLibrary(
                            songId = song.id,
                            endpoint = uploadEndpoint.orEmpty(),
                            explicitPath = picked.relative
                        )
                        when (result) {
                            is ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Success -> {
                                onSongRemoved(container, controller, song)
                                android.widget.Toast.makeText(ctx, result.message, android.widget.Toast.LENGTH_LONG).show()
                            }
                            is ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Failure ->
                                android.widget.Toast.makeText(ctx, result.message, android.widget.Toast.LENGTH_LONG).show()
                            is ie.adrianszydlo.navitunes.data.upload.UploadService.Result.Ambiguous ->
                                android.widget.Toast.makeText(ctx, "Still ambiguous — try again.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    ambiguousRemoval = null
                },
                onDismiss = { ambiguousRemoval = null }
            )
        }

        updateAvailable?.let { avail ->
            UpdateAvailableDialog(
                available = avail,
                onDismiss = { updateAvailable = null },
                onSkip = {
                    container.appScope.launch {
                        container.preferences.setSkippedUpdateVersion(avail.versionName)
                    }
                    updateAvailable = null
                }
            )
        }
    }
}

/**
 * Cleanup after the server confirms a song was deleted:
 *   - drop it from the active queue (and stop if it was the current track),
 *   - purge it from Recently Played,
 *   - tick the library-changed signal so screens reload.
 */
private fun onSongRemoved(
    container: ie.adrianszydlo.navitunes.AppContainer,
    controller: PlayerController,
    song: Song
) {
    val wasCurrent = controller.currentItem.value?.id == song.id
    controller.removeFromQueueById(song.id)
    if (wasCurrent) controller.stop()

    container.appScope.launch {
        container.profileStore.activeId.value?.let { profileId ->
            container.recentlyPlayedStore.purge(profileId, song.id)
        }
    }
    container.librarySignals.notifyChanged()
}

@Composable
private fun MainShellInner(controller: PlayerController, onAddProfile: () -> Unit) {
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
                    nav.navigate(route) {
                        // Pop everything back down to (but not including) the start
                        // destination so detail screens (album/artist/playlist/
                        // downloads) are dropped when a tab is tapped.
                        popUpTo(nav.graph.findStartDestination().id) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
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
                        onPlay = { songs, idx -> controller.play(songs, idx) },
                        onPlaylist = { nav.navigate(Routes.playlist(it)) }
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
                        onPlaylist = { nav.navigate(Routes.playlist(it)) },
                        onPlay = { songs, idx -> controller.play(songs, idx) },
                        onOpenDownloadManager = { nav.navigate(Routes.DOWNLOAD_MANAGER) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onAddProfile = onAddProfile,
                        onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) }
                    )
                }
                composable(Routes.DOWNLOADS) {
                    DownloadsScreen(
                        onBack = { nav.popBackStack() },
                        onPlay = { songs, idx -> controller.play(songs, idx) }
                    )
                }
                composable(Routes.DOWNLOAD_MANAGER) {
                    DownloadManagerScreen(onBack = { nav.popBackStack() })
                }
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
                        onPlay = { songs, idx -> controller.play(songs, idx) },
                        onGoHome = {
                            nav.navigate(Routes.HOME) {
                                popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
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
