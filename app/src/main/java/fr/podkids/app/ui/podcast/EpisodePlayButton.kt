package fr.podkids.app.ui.podcast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.podkids.app.R

/**
 * Bouton lecture / pause d'un épisode, entouré d'un anneau qui indique son avancement
 * (0 = pas commencé : anneau bleu, 1 = écouté en entier : anneau grisé bleuté).
 */
@Composable
fun EpisodePlayButton(
    fraction: Float,
    isPlaying: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(52.dp)) {
        CircularProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            // Partie écoutée grisée bleutée, reste à écouter en bleu : un épisode jamais écouté
            // a un anneau entièrement bleu, un épisode lu un anneau entièrement grisé.
            color = MaterialTheme.colorScheme.outlineVariant,
            trackColor = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            modifier = Modifier.size(46.dp),
        )
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                tint = color,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Avancement d'un épisode entre 0 et 1 ; null si la durée est inconnue. */
fun episodeFraction(positionMs: Long, durationMs: Long, completed: Boolean): Float? = when {
    completed -> 1f
    durationMs <= 0 -> null
    else -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
