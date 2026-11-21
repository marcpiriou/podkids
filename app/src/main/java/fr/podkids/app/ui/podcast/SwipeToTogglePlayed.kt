package fr.podkids.app.ui.podcast

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Ligne d'épisode que l'on peut glisser vers la gauche ou la droite pour la marquer lue / non lue.
 * Au-delà d'un seuil, l'action est déclenchée au lâcher et la ligne revient à sa place.
 * L'action est aussi proposée aux lecteurs d'écran.
 */
@Composable
fun SwipeToTogglePlayed(
    enabled: Boolean,
    played: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val actionLabel = stringResource(if (played) R.string.mark_unplayed else R.string.mark_played)
    val background = if (played) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val onBackground = if (played) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(actionLabel) {
                        onToggle()
                        true
                    },
                )
            },
    ) {
        // Fond révélé pendant le glissement : icône et libellé de l'action.
        if (offset.value != 0f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .matchParentSize()
                    .background(background)
                    .padding(horizontal = 24.dp),
            ) {
                if (offset.value < 0f) Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (played) Icons.Rounded.Replay else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = onBackground,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge, color = onBackground)
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset.value }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(played) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val triggered = abs(offset.value) >= threshold
                            scope.launch { offset.animateTo(0f) }
                            if (triggered) onToggle()
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dragAmount) }
                    }
                },
        ) {
            content()
        }
    }
}

private val SWIPE_THRESHOLD = 96.dp
