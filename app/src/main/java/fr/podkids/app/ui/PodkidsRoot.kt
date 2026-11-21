package fr.podkids.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.podkids.app.AppContainer
import fr.podkids.app.BuildConfig
import fr.podkids.app.R
import fr.podkids.app.data.db.DownloadState
import fr.podkids.app.data.settings.EpisodeOrder
import fr.podkids.app.data.settings.UiSettings
import fr.podkids.app.playback.PlayerUiState
import fr.podkids.app.ui.catalog.CatalogScreen
import fr.podkids.app.ui.catalog.CatalogViewModel
import fr.podkids.app.ui.components.MiniPlayer
import fr.podkids.app.ui.components.UnlockSettingsDialog
import fr.podkids.app.ui.home.HomeScreen
import fr.podkids.app.ui.player.PlayerScreen
import fr.podkids.app.ui.podcast.PodcastEvent
import fr.podkids.app.ui.podcast.PodcastScreen
import fr.podkids.app.ui.podcast.PodcastViewModel
import fr.podkids.app.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val PODCAST = "podcast/{podcastId}"
    fun podcast(id: Long) = "podcast/$id"
}

/**
 * Racine de l'interface : navigation entre les écrans et mini-lecteur persistant en bas.
 * @param openPlayerRequests émet à chaque demande d'ouverture du lecteur (appui sur la notification).
 */
@Composable
fun PodkidsRoot(container: AppContainer, settings: UiSettings, openPlayerRequests: Flow<Unit>) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val player = container.player

    LaunchedEffect(openPlayerRequests) {
        openPlayerRequests.collect { navController.openPlayer() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val rootScope = rememberCoroutineScope()
    // Réglages à jour au moment d'un appui, même si l'écran affiché n'a pas encore été recomposé.
    val latestSettings by rememberUpdatedState(settings)

    fun setChildMode(enabled: Boolean) {
        rootScope.launch {
            container.settings.setChildMode(enabled)
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                context.getString(if (enabled) R.string.child_mode_on else R.string.adult_mode_on),
            )
        }
    }

    // En mode enfant, le mot de passe (s'il existe) est demandé pour ouvrir les réglages ou passer
    // en mode adulte.
    var unlockTarget by rememberSaveable { mutableStateOf<UnlockTarget?>(null) }
    unlockTarget?.let { target ->
        UnlockSettingsDialog(
            explanation = stringResource(
                if (target == UnlockTarget.SETTINGS) R.string.unlock_explanation else R.string.unlock_adult_mode_explanation,
            ),
            checkPassword = container.settings::checkPassword,
            onUnlocked = {
                unlockTarget = null
                when (target) {
                    UnlockTarget.SETTINGS -> navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    UnlockTarget.ADULT_MODE -> setChildMode(false)
                }
            },
            onDismiss = { unlockTarget = null },
        )
    }
    LaunchedEffect(player) {
        player.errors.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            launch { snackbarHostState.showSnackbar(context.getString(R.string.playback_error)) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = playerState.current != null && currentRoute != Routes.PLAYER,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                MiniPlayer(
                    state = playerState,
                    progress = player.progress,
                    onOpenPlayer = { navController.openPlayer() },
                    onTogglePlay = player::togglePlay,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                val library by remember(settings.onlyDownloaded) { container.repository.observeLibrary(settings.onlyDownloaded) }
                    .collectAsStateWithLifecycle(initialValue = null)
                HomeScreen(
                    library = library,
                    settings = settings,
                    onOpenSettings = {
                        if (latestSettings.passwordRequired) {
                            unlockTarget = UnlockTarget.SETTINGS
                        } else {
                            navController.navigate(Routes.SETTINGS)
                        }
                    },
                    onToggleMode = {
                        when {
                            !latestSettings.childMode -> setChildMode(true)
                            // Quitter le mode enfant demande le mot de passe, s'il y en a un.
                            latestSettings.settingsLocked -> unlockTarget = UnlockTarget.ADULT_MODE
                            else -> setChildMode(false)
                        }
                    },
                    onOpenCatalog = { navController.navigate(Routes.CATALOG) },
                    onOpenPodcast = { navController.navigate(Routes.podcast(it)) },
                )
            }

            composable(Routes.CATALOG) {
                val viewModel: CatalogViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { CatalogViewModel(container.repository, container.catalog, container.settings) }
                    },
                )
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(viewModel) {
                    viewModel.openPodcast.collect { navController.navigate(Routes.podcast(it)) }
                }
                CatalogScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onQueryChange = viewModel::onQueryChange,
                    onToggleAge = viewModel::toggleAge,
                    onOpen = viewModel::open,
                    onAddByRssUrl = viewModel::addByRssUrl,
                    onDismissOpenError = viewModel::dismissOpenError,
                )
            }

            composable(
                route = Routes.PODCAST,
                arguments = listOf(navArgument("podcastId") { type = NavType.LongType }),
            ) { entry ->
                val podcastId = entry.arguments?.getLong("podcastId") ?: return@composable
                PodcastRoute(
                    podcastId = podcastId,
                    container = container,
                    playerState = playerState,
                    defaultEpisodeOrder = settings.episodeOrder,
                    downloadedOnly = settings.onlyDownloaded,
                    swipeEnabled = settings.swipeEnabled,
                    showPodcastSettings = !settings.childMode,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.PLAYER) {
                PlayerScreen(
                    state = playerState,
                    progress = player.progress,
                    onBack = { navController.popBackStack() },
                    // Pas de raccourci vers le catalogue quand il est réservé aux réglages.
                    onOpenCatalog = if (settings.catalogHidden) {
                        null
                    } else {
                        { navController.navigate(Routes.CATALOG) { popUpTo(Routes.HOME) } }
                    },
                    onTogglePlay = player::togglePlay,
                    onSeekTo = player::seekTo,
                    onPlayUpcoming = player::playUpcoming,
                    onMoveUpcomingUp = player::moveUpcomingUp,
                    onRemoveUpcoming = player::removeUpcoming,
                    onClearUpcoming = player::clearUpcoming,
                )
            }

            composable(Routes.SETTINGS) {
                val scope = rememberCoroutineScope()
                val repository = container.settings
                SettingsScreen(
                    settings = settings,
                    versionName = BuildConfig.VERSION_NAME,
                    onBack = { navController.popBackStack() },
                    onDarkModeChange = { scope.launch { repository.setDarkMode(it) } },
                    onEpisodeOrderChange = { scope.launch { repository.setEpisodeOrder(it) } },
                    onHideCatalogOnHomeChange = { scope.launch { repository.setHideCatalogOnHome(it) } },
                    onDownloadedOnlyChange = { scope.launch { repository.setDownloadedOnly(it) } },
                    onSwipeToMarkPlayedChange = { scope.launch { repository.setSwipeToMarkPlayed(it) } },
                    onOpenCatalog = { navController.navigate(Routes.CATALOG) },
                    onSetPassword = { scope.launch { repository.setPassword(it) } },
                    onClearPassword = { scope.launch { repository.clearPassword() } },
                )
            }
        }
    }
}

@Composable
private fun PodcastRoute(
    podcastId: Long,
    container: AppContainer,
    playerState: PlayerUiState,
    defaultEpisodeOrder: EpisodeOrder,
    downloadedOnly: Boolean,
    swipeEnabled: Boolean,
    showPodcastSettings: Boolean,
    onBack: () -> Unit,
) {
    val viewModel: PodcastViewModel = viewModel(
        key = "podcast-$podcastId",
        factory = viewModelFactory {
            initializer { PodcastViewModel(podcastId, container.repository, container.player, defaultEpisodeOrder) }
        },
    )
    val podcast by viewModel.podcast.collectAsStateWithLifecycle()
    val allEpisodes by viewModel.episodes.collectAsStateWithLifecycle()
    val episodes = remember(allEpisodes, downloadedOnly) {
        if (downloadedOnly) allEpisodes.filter { it.downloadState == DownloadState.DOWNLOADED } else allEpisodes
    }
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val order by viewModel.order.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                PodcastEvent.REMOVED -> {
                    onBack()
                    return@collect
                }
                PodcastEvent.ADDED_TO_QUEUE -> R.string.added_to_queue
                PodcastEvent.ALREADY_IN_QUEUE -> R.string.already_in_queue
                PodcastEvent.REMOVED_FROM_QUEUE -> R.string.removed_from_queue
                PodcastEvent.REFRESH_FAILED -> R.string.refresh_failed
            }
            // Un nouveau message remplace le précédent plutôt que d'attendre sa disparition.
            snackbarHostState.currentSnackbarData?.dismiss()
            launch { snackbarHostState.showSnackbar(context.getString(message)) }
        }
    }

    PodcastScreen(
        podcast = podcast,
        episodes = episodes,
        downloadedOnly = downloadedOnly,
        swipeEnabled = swipeEnabled,
        showPodcastSettings = showPodcastSettings,
        onSaveSkips = viewModel::setSkips,
        onTogglePlayed = viewModel::togglePlayed,
        refreshing = refreshing,
        order = order,
        onToggleOrder = viewModel::toggleOrder,
        currentEpisodeId = playerState.current?.episodeId,
        isPlaying = playerState.isPlaying,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = { viewModel.refresh() },
        onSubscribe = viewModel::subscribe,
        onRemove = viewModel::remove,
        onPlay = viewModel::play,
        onTogglePlay = container.player::togglePlay,
        playbackProgress = container.player.progress,
        onToggleQueue = viewModel::toggleQueue,
        queuedEpisodeIds = remember(playerState.upcoming) { playerState.upcoming.map { it.episodeId }.toSet() },
        onDownload = viewModel::download,
        onRemoveDownload = viewModel::removeDownload,
        onDownloadAll = viewModel::downloadAll,
    )
}

/** Action protégée par le mot de passe des réglages. */
private enum class UnlockTarget { SETTINGS, ADULT_MODE }

private fun NavHostController.openPlayer() {
    navigate(Routes.PLAYER) { launchSingleTop = true }
}
