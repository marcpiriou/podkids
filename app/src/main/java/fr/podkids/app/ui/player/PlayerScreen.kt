package fr.podkids.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.podkids.app.R
import fr.podkids.app.playback.PlaybackProgress
import fr.podkids.app.playback.PlayerUiState
import fr.podkids.app.playback.QueueEntry
import fr.podkids.app.ui.components.Artwork
import fr.podkids.app.ui.components.formatClock
import kotlinx.coroutines.flow.StateFlow

/** Écran « Lecture en cours » : lecteur complet et file d'attente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    progress: StateFlow<PlaybackProgress>,
    onBack: () -> Unit,
    /** null : pas de bouton vers le catalogue (catalogue réservé aux réglages). */
    onOpenCatalog: (() -> Unit)?,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onPlayUpcoming: (Int) -> Unit,
    onMoveUpcomingUp: (Int) -> Unit,
    onRemoveUpcoming: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val current = state.current
        if (current == null) {
            EmptyPlayer(onOpenCatalog = onOpenCatalog, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "player") {
                NowPlaying(
                    current = current,
                    state = state,
                    progress = progress,
                    onTogglePlay = onTogglePlay,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onSeekTo = onSeekTo,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                )
            }
            item(key = "queue-header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.queue),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.upcoming.isNotEmpty()) {
                        TextButton(onClick = onClearUpcoming) { Text(stringResource(R.string.queue_clear)) }
                    }
                }
            }
            if (state.upcoming.isEmpty()) {
                item(key = "queue-empty") {
                    Text(
                        text = stringResource(R.string.queue_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            itemsIndexed(state.upcoming, key = { _, entry -> entry.episodeId }) { index, entry ->
                QueueRow(
                    entry = entry,
                    canMoveUp = index > 0,
                    onPlay = { onPlayUpcoming(index) },
                    onMoveUp = { onMoveUpcomingUp(index) },
                    onRemove = { onRemoveUpcoming(index) },
                )
            }
        }
    }
}

@Composable
private fun NowPlaying(
    current: QueueEntry,
    state: PlayerUiState,
    progress: StateFlow<PlaybackProgress>,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Artwork(
            url = current.artworkUrl,
            cornerRadius = 28.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(0.8f)
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = current.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = current.podcastTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        SeekBar(progress = progress, onSeekTo = onSeekTo)
        // Cinq commandes de largeur égale : précédent, -10 s, lecture, +30 s, suivant.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ControlButton(
                icon = Icons.Rounded.SkipPrevious,
                description = stringResource(R.string.previous_episode),
                enabled = state.hasPrevious,
                onClick = onSkipPrevious,
            )
            ControlButton(
                icon = Icons.Rounded.Replay10,
                description = stringResource(R.string.seek_back),
                onClick = onSeekBack,
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1.4f)) {
                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(80.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                        modifier = Modifier.size(52.dp),
                    )
                }
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.tertiary,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(92.dp),
                    )
                }
            }
            ControlButton(
                icon = Icons.Rounded.Forward30,
                description = stringResource(R.string.seek_forward),
                onClick = onSeekForward,
            )
            ControlButton(
                icon = Icons.Rounded.SkipNext,
                description = stringResource(R.string.next_episode),
                enabled = state.upcoming.isNotEmpty(),
                onClick = onSkipNext,
            )
        }
    }
}

@Composable
private fun RowScope.ControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun SeekBar(progress: StateFlow<PlaybackProgress>, onSeekTo: (Long) -> Unit) {
    val value by progress.collectAsStateWithLifecycle()
    // Pendant le glissement, le curseur suit le doigt et non la position réelle de lecture.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: value.fraction
    val shownPositionMs = dragFraction?.let { (it * value.durationMs).toLong() } ?: value.positionMs

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeekTo((it * value.durationMs).toLong()) }
                dragFraction = null
            },
            enabled = value.durationMs > 0,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatClock(shownPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (value.durationMs > 0) formatClock(value.durationMs) else "–:––",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    canMoveUp: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.play), onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Artwork(url = entry.artworkUrl, cornerRadius = 12.dp, modifier = Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.podcastTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canMoveUp) {
            IconButton(onClick = onMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.queue_move_up))
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.queue_remove))
        }
    }
}

@Composable
private fun EmptyPlayer(onOpenCatalog: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.nothing_playing),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.nothing_playing_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onOpenCatalog != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onOpenCatalog) {
                Icon(Icons.Rounded.Explore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_catalog))
            }
        }
    }
}
