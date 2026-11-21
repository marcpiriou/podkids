package fr.podkids.app.ui.podcast

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import fr.podkids.app.data.db.DownloadState
import fr.podkids.app.data.db.EpisodeEntity
import fr.podkids.app.data.db.PodcastEntity
import fr.podkids.app.data.settings.EpisodeOrder
import fr.podkids.app.ui.components.Artwork
import fr.podkids.app.ui.components.formatDate
import fr.podkids.app.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(
    podcast: PodcastEntity?,
    episodes: List<EpisodeEntity>,
    /**
     * Mode « téléchargés uniquement » : la liste ne contient que les épisodes téléchargés et les
     * actions de gestion (ajout, retrait, téléchargements) sont masquées.
     */
    downloadedOnly: Boolean,
    /** Glisser un épisode le marque lu / non lu. */
    swipeEnabled: Boolean,
    /** Icône des réglages propres à ce podcast (mode adulte uniquement). */
    showPodcastSettings: Boolean,
    onSaveSkips: (introSec: Int, outroSec: Int) -> Unit,
    onTogglePlayed: (episodeId: Long, played: Boolean) -> Unit,
    refreshing: Boolean,
    order: EpisodeOrder,
    onToggleOrder: () -> Unit,
    currentEpisodeId: Long?,
    isPlaying: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSubscribe: () -> Unit,
    onRemove: () -> Unit,
    onPlay: (Long) -> Unit,
    onAddToQueue: (Long) -> Unit,
    onDownload: (Long) -> Unit,
    onRemoveDownload: (Long) -> Unit,
    onDownloadAll: () -> Unit,
) {
    var confirmDownloadAll by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteDownload by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = podcast?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (showPodcastSettings && podcast != null) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.podcast_settings_title))
                        }
                    }
                    // Icône de tri : bascule entre l'ordre chronologique et l'ordre inverse.
                    IconButton(onClick = onToggleOrder) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = stringResource(
                                if (order == EpisodeOrder.OLDEST_FIRST) R.string.sort_oldest_first_action else R.string.sort_newest_first_action,
                            ),
                            // Retournée verticalement en ordre chronologique : la barre la plus courte en haut.
                            modifier = Modifier.graphicsLayer { scaleY = if (order == EpisodeOrder.OLDEST_FIRST) -1f else 1f },
                        )
                    }
                    if (refreshing) {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp),
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (podcast == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        // Après un changement d'ordre, on revient en haut de la liste (sans effet à l'ouverture).
        val listState = rememberLazyListState()
        var shownOrder by rememberSaveable { mutableStateOf(order) }
        LaunchedEffect(order) {
            if (order != shownOrder) {
                shownOrder = order
                listState.scrollToItem(0)
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "header") {
                PodcastHeader(
                    showActions = !downloadedOnly,
                    order = order,
                    podcast = podcast,
                    episodeCount = episodes.size,
                    onSubscribe = onSubscribe,
                    onRemove = { confirmRemove = true },
                    onDownloadAll = { confirmDownloadAll = true },
                )
            }
            if (downloadedOnly && episodes.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.no_downloaded_episode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }
            // Épisodes à écouter, puis section « Épisodes lus » en bas de la liste.
            val (played, toListen) = episodes.partition { it.completed }
            val episodeItem: @Composable LazyItemScope.(EpisodeEntity) -> Unit = { episode ->
                Column(modifier = Modifier.animateItem()) {
                    SwipeToTogglePlayed(
                        enabled = swipeEnabled,
                        played = episode.completed,
                        onToggle = { onTogglePlayed(episode.id, !episode.completed) },
                    ) {
                        EpisodeRow(
                            episode = episode,
                            isCurrent = episode.id == currentEpisodeId,
                            isPlaying = isPlaying && episode.id == currentEpisodeId,
                            onPlay = { onPlay(episode.id) },
                            onAddToQueue = { onAddToQueue(episode.id) },
                            onDownload = { onDownload(episode.id) },
                            onCancelDownload = { onRemoveDownload(episode.id) },
                            onDeleteDownload = { confirmDeleteDownload = episode.id },
                            showDownloadButton = !downloadedOnly,
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(toListen, key = { it.id }) { episodeItem(it) }
            if (played.isNotEmpty()) {
                item(key = "played-header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.played_episodes),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = played.size.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(played, key = { it.id }) { episodeItem(it) }
            }
        }
    }

    if (confirmDownloadAll) {
        val remaining = episodes.count {
            it.downloadState == DownloadState.NONE || it.downloadState == DownloadState.FAILED
        }
        ConfirmDialog(
            title = stringResource(R.string.download_all),
            text = if (remaining == 0) {
                stringResource(R.string.download_all_nothing)
            } else {
                pluralStringResource(R.plurals.download_all_confirm, remaining, remaining)
            },
            confirmLabel = stringResource(if (remaining == 0) R.string.ok else R.string.download),
            showCancel = remaining > 0,
            onConfirm = {
                confirmDownloadAll = false
                if (remaining > 0) onDownloadAll()
            },
            onDismiss = { confirmDownloadAll = false },
        )
    }

    if (showSettings && showPodcastSettings && podcast != null) {
        PodcastSettingsDialog(
            podcastTitle = podcast.title,
            skipIntroSec = podcast.skipIntroSec,
            skipOutroSec = podcast.skipOutroSec,
            onSave = { intro, outro ->
                showSettings = false
                onSaveSkips(intro, outro)
            },
            onDismiss = { showSettings = false },
        )
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.remove_podcast),
            text = stringResource(R.string.remove_podcast_confirm),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }

    confirmDeleteDownload?.let { episodeId ->
        ConfirmDialog(
            title = stringResource(R.string.delete_download),
            text = stringResource(R.string.delete_download_confirm),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDeleteDownload = null
                onRemoveDownload(episodeId)
            },
            onDismiss = { confirmDeleteDownload = null },
        )
    }
}

@Composable
private fun PodcastHeader(
    showActions: Boolean,
    order: EpisodeOrder,
    podcast: PodcastEntity,
    episodeCount: Int,
    onSubscribe: () -> Unit,
    onRemove: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(url = podcast.imageUrl, modifier = Modifier.size(120.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (podcast.author.isNotBlank()) {
                    Text(
                        text = podcast.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.episode_count, episodeCount, episodeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (order == EpisodeOrder.OLDEST_FIRST) R.string.sort_oldest_first else R.string.sort_newest_first,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showActions) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (podcast.subscribed) {
                    FilledTonalButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.in_my_podcasts), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Button(onClick = onSubscribe, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_to_my_podcasts), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                FilledTonalButton(onClick = onDownloadAll, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.download_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (podcast.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = podcast.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .animateContentSize()
                    .clickable { expanded = !expanded },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    showDownloadButton: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.play), onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    episode.completed -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                episode.publishedAt?.let(::formatDate),
                episode.durationSec?.let(::formatDuration),
                if (episode.completed) stringResource(R.string.episode_listened) else null,
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onAddToQueue) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = stringResource(R.string.add_to_queue),
            )
        }
        if (showDownloadButton) {
            DownloadButton(
                state = episode.downloadState,
                progress = episode.downloadProgress,
                onDownload = onDownload,
                onCancel = onCancelDownload,
                onDelete = onDeleteDownload,
            )
        }
    }
}

@Composable
private fun DownloadButton(
    state: DownloadState,
    progress: Int,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    when (state) {
        DownloadState.NONE -> IconButton(onClick = onDownload) {
            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.download))
        }
        DownloadState.FAILED -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(R.string.download_retry),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        DownloadState.QUEUED, DownloadState.DOWNLOADING -> IconButton(onClick = onCancel) {
            Box(contentAlignment = Alignment.Center) {
                if (state == DownloadState.DOWNLOADING && progress in 1..100) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(30.dp),
                    )
                } else {
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                }
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.download_cancel),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        DownloadState.DOWNLOADED -> IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.DownloadDone,
                contentDescription = stringResource(R.string.delete_download),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showCancel: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = if (showCancel) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        } else {
            null
        },
    )
}
