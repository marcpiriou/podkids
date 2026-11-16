package fr.podkids.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.podkids.app.R
import fr.podkids.app.playback.PlaybackProgress
import fr.podkids.app.playback.PlayerUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Mini-lecteur affiché en bas de tous les écrans dès qu'un épisode est chargé :
 * pochette à gauche, titre et lecture/pause. Un appui ouvre le lecteur complet.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    progress: StateFlow<PlaybackProgress>,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.current ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            MiniPlayerProgress(progress)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.open_player), onClick = onOpenPlayer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Artwork(url = current.artworkUrl, cornerRadius = 12.dp, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = current.podcastTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(progress: StateFlow<PlaybackProgress>) {
    // Collecté ici pour ne recomposer que la barre de progression deux fois par seconde.
    val value by progress.collectAsStateWithLifecycle()
    LinearProgressIndicator(
        progress = { value.fraction },
        modifier = Modifier.fillMaxWidth(),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}
