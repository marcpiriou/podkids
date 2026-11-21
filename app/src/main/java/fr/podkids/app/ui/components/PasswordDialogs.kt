package fr.podkids.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import fr.podkids.app.R
import fr.podkids.app.data.settings.PasswordHasher
import kotlinx.coroutines.launch

/**
 * Demande le mot de passe des réglages.
 * @param explanation ce que le mot de passe permet (ouvrir les réglages, passer en mode adulte).
 * @param checkPassword vérifie le mot de passe saisi.
 * @param onUnlocked appelé quand le mot de passe est correct.
 */
@Composable
fun UnlockSettingsDialog(
    explanation: String,
    checkPassword: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    fun submit() {
        if (password.isEmpty() || checking) return
        checking = true
        scope.launch {
            val ok = checkPassword(password)
            checking = false
            if (ok) {
                onUnlocked()
            } else {
                wrong = true
                password = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Un appui hors de la fenêtre ne la ferme pas : les icônes qui l'ouvrent sont dans les coins
        // de l'écran, un second appui ou un appui un peu long la refermait aussitôt. Elle se ferme
        // avec « Annuler » ou le bouton retour.
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.unlock_title)) },
        text = {
            Column {
                Text(explanation)
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        wrong = false
                    },
                    label = stringResource(R.string.password_label),
                    isError = wrong,
                    supportingText = if (wrong) stringResource(R.string.password_wrong) else null,
                    onDone = ::submit,
                    modifier = Modifier.focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.password_forgotten_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = password.isNotEmpty() && !checking) {
                Text(stringResource(R.string.unlock_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Saisie d'un nouveau mot de passe, avec confirmation. */
@Composable
fun SetPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    val tooShort = password.length < PasswordHasher.MIN_LENGTH
    val mismatch = password != confirmation

    fun submit() {
        if (tooShort || mismatch) {
            showErrors = true
        } else {
            onConfirm(password)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Un appui hors de la fenêtre ne la ferme pas : les icônes qui l'ouvrent sont dans les coins
        // de l'écran, un second appui ou un appui un peu long la refermait aussitôt. Elle se ferme
        // avec « Annuler » ou le bouton retour.
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.set_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.set_password_explanation, PasswordHasher.MIN_LENGTH))
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.password_label),
                    isError = showErrors && tooShort,
                    supportingText = if (showErrors && tooShort) {
                        stringResource(R.string.password_too_short, PasswordHasher.MIN_LENGTH)
                    } else {
                        null
                    },
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = stringResource(R.string.password_confirm_label),
                    isError = showErrors && !tooShort && mismatch,
                    supportingText = if (showErrors && !tooShort && mismatch) {
                        stringResource(R.string.password_mismatch)
                    } else {
                        null
                    },
                    onDone = ::submit,
                )
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = password.isNotEmpty()) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    supportingText: String?,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}
