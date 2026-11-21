package fr.podkids.app.ui.podcast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import fr.podkids.app.data.PodcastRepository

/**
 * Réglages propres à un podcast : secondes à sauter au début de chaque épisode, et seuil de fin
 * en dessous duquel on passe à la suite. 0 (ou vide) désactive le réglage.
 */
@Composable
fun PodcastSettingsDialog(
    podcastTitle: String,
    skipIntroSec: Int,
    skipOutroSec: Int,
    onSave: (introSec: Int, outroSec: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var intro by rememberSaveable { mutableStateOf(if (skipIntroSec > 0) skipIntroSec.toString() else "") }
    var outro by rememberSaveable { mutableStateOf(if (skipOutroSec > 0) skipOutroSec.toString() else "") }
    val introValue = intro.toIntOrNull() ?: 0
    val outroValue = outro.toIntOrNull() ?: 0
    val max = PodcastRepository.MAX_SKIP_SEC
    val introError = introValue > max
    val outroError = outroValue > max

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
        title = { Text(stringResource(R.string.podcast_settings_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.podcast_settings_explanation, podcastTitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                SecondsField(
                    value = intro,
                    onValueChange = { intro = it },
                    label = stringResource(R.string.skip_intro_label),
                    help = stringResource(R.string.skip_intro_help),
                    isError = introError,
                    max = max,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(12.dp))
                SecondsField(
                    value = outro,
                    onValueChange = { outro = it },
                    label = stringResource(R.string.skip_outro_label),
                    help = stringResource(R.string.skip_outro_help),
                    isError = outroError,
                    max = max,
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(introValue, outroValue) }, enabled = !introError && !outroError) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SecondsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    help: String,
    isError: Boolean,
    max: Int,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        // Chiffres uniquement, 4 au plus.
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        suffix = { Text(stringResource(R.string.seconds_suffix)) },
        placeholder = { Text("0") },
        supportingText = { Text(if (isError) stringResource(R.string.skip_too_long, max) else help) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}
