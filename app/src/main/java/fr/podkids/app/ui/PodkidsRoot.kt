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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import fr.podkids.app.data.settings.UiSettings
import fr.podkids.app.playback.PlayerUiState
import fr.podkids.app.ui.catalog.CatalogScreen
import fr.podkids.app.ui.catalog.CatalogViewModel
import fr.podkids.app.ui.components.MiniPlayer
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
                    onSkipNext = player::skipToNext,
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
                val library by remember { container.repository.observeLibrary() }
                    .collectAsStateWithLifecycle(initialValue = null)
                HomeScreen(
                    library = library,
                    settings = settings,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onQueryChange = viewModel::onQueryChange,
                    onToggleAge = viewModel::toggleAge,
                    onSelectCategory = viewModel::selectCategory,
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
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.PLAYER) {
                PlayerScreen(
                    state = playerState,
                    progress = player.progress,
                    onBack = { navController.popBackStack() },
                    onOpenCatalog = {
                        navController.navigate(Routes.CATALOG) { popUpTo(Routes.HOME) }
                    },
                    onTogglePlay = player::togglePlay,
                    onSeekBack = player::seekBack,
                    onSeekForward = player::seekForward,
                    onSeekTo = player::seekTo,
                    onSkipNext = player::skipToNext,
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
                    onThemeColorChange = { scope.launch { repository.setThemeColor(it) } },
                    onDarkModeChange = { scope.launch { repository.setDarkMode(it) } },
                    onTextSizeChange = { scope.launch { repository.setTextSize(it) } },
                    onTileSizeChange = { scope.launch { repository.setTileSize(it) } },
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
    onBack: () -> Unit,
) {
    val viewModel: PodcastViewModel = viewModel(
        key = "podcast-$podcastId",
        factory = viewModelFactory {
            initializer { PodcastViewModel(podcastId, container.repository, container.player) }
        },
    )
    val podcast by viewModel.podcast.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
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
        refreshing = refreshing,
        currentEpisodeId = playerState.current?.episodeId,
        isPlaying = playerState.isPlaying,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = { viewModel.refresh() },
        onSubscribe = viewModel::subscribe,
        onRemove = viewModel::remove,
        onPlay = viewModel::play,
        onAddToQueue = viewModel::addToQueue,
        onDownload = viewModel::download,
        onRemoveDownload = viewModel::removeDownload,
        onDownloadAll = viewModel::downloadAll,
    )
}

private fun NavHostController.openPlayer() {
    navigate(Routes.PLAYER) { launchSingleTop = true }
}
