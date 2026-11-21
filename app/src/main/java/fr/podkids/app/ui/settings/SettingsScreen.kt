package fr.podkids.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Surface
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import fr.podkids.app.ui.components.SetPasswordDialog
import fr.podkids.app.data.settings.DarkMode
import fr.podkids.app.data.settings.EpisodeOrder
import fr.podkids.app.data.settings.UiSettings

/** Réglages : personnalisation de l'interface (couleur, apparence, tailles). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UiSettings,
    versionName: String,
    onBack: () -> Unit,
    onDarkModeChange: (DarkMode) -> Unit,
    onEpisodeOrderChange: (EpisodeOrder) -> Unit,
    onHideCatalogOnHomeChange: (Boolean) -> Unit,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    onSwipeToMarkPlayedChange: (Boolean) -> Unit,
    onSetPassword: (String) -> Unit,
    onClearPassword: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingCard(icon = Icons.Rounded.Lock, title = stringResource(R.string.setting_lock)) {
                var showSetPassword by rememberSaveable { mutableStateOf(false) }
                Text(
                    text = stringResource(
                        if (settings.settingsLocked) R.string.setting_lock_on else R.string.setting_lock_off,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (settings.settingsLocked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showSetPassword = true }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.password_change), maxLines = 1)
                        }
                        OutlinedButton(onClick = onClearPassword, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.password_remove), maxLines = 1)
                        }
                    }
                } else {
                    Button(onClick = { showSetPassword = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.password_set))
                    }
                }
                if (showSetPassword) {
                    SetPasswordDialog(
                        onConfirm = {
                            showSetPassword = false
                            onSetPassword(it)
                        },
                        onDismiss = { showSetPassword = false },
                    )
                }
            }

            SettingCard(icon = Icons.Rounded.ChildCare, title = stringResource(R.string.setting_children_access)) {
                Text(
                    text = stringResource(R.string.setting_child_mode_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.setting_hide_catalog),
                    description = stringResource(R.string.setting_hide_catalog_description),
                    checked = settings.hideCatalogOnHome,
                    onCheckedChange = onHideCatalogOnHomeChange,
                )
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.setting_downloaded_only),
                    description = stringResource(R.string.setting_downloaded_only_description),
                    checked = settings.downloadedOnly,
                    onCheckedChange = onDownloadedOnlyChange,
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.setting_swipe_played),
                    description = stringResource(R.string.setting_swipe_played_description),
                    checked = settings.swipeToMarkPlayed,
                    onCheckedChange = onSwipeToMarkPlayedChange,
                )
            }

            SettingCard(icon = Icons.AutoMirrored.Rounded.Sort, title = stringResource(R.string.setting_episode_order)) {
                ChoiceRow(
                    options = EpisodeOrder.entries,
                    selected = settings.episodeOrder,
                    label = { stringResource(it.labelRes()) },
                    onSelect = onEpisodeOrderChange,
                )
            }

            SettingCard(icon = Icons.Rounded.DarkMode, title = stringResource(R.string.setting_appearance)) {
                ChoiceRow(
                    options = DarkMode.entries,
                    selected = settings.darkMode,
                    label = { stringResource(it.labelRes()) },
                    onSelect = onDarkModeChange,
                )
            }

            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Ligne de réglage avec interrupteur ; toute la ligne est cliquable. */
@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        // L'interrupteur suit la ligne : le clic est géré par toggleable.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Choix exclusif présenté en segments de même largeur, qui occupent toute la largeur de la carte
 * sans jamais en dépasser. Le libellé passe sur deux lignes si la taille du texte l'exige.
 */
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                selected = isSelected,
                onClick = { onSelect(option) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 48.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

private fun DarkMode.labelRes(): Int = when (this) {
    DarkMode.SYSTEM -> R.string.appearance_system
    DarkMode.LIGHT -> R.string.appearance_light
    DarkMode.DARK -> R.string.appearance_dark
}

private fun EpisodeOrder.labelRes(): Int = when (this) {
    EpisodeOrder.OLDEST_FIRST -> R.string.episode_order_oldest_first
    EpisodeOrder.NEWEST_FIRST -> R.string.episode_order_newest_first
}