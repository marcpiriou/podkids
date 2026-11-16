package fr.podkids.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import fr.podkids.app.data.settings.DarkMode
import fr.podkids.app.data.settings.EpisodeOrder
import fr.podkids.app.data.settings.TextSize
import fr.podkids.app.data.settings.ThemeColor
import fr.podkids.app.data.settings.TileSize
import fr.podkids.app.data.settings.UiSettings
import fr.podkids.app.ui.theme.palette

/** Réglages : personnalisation de l'interface (couleur, apparence, tailles). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UiSettings,
    versionName: String,
    onBack: () -> Unit,
    onThemeColorChange: (ThemeColor) -> Unit,
    onDarkModeChange: (DarkMode) -> Unit,
    onTextSizeChange: (TextSize) -> Unit,
    onTileSizeChange: (TileSize) -> Unit,
    onEpisodeOrderChange: (EpisodeOrder) -> Unit,
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
            SettingCard(icon = Icons.Rounded.Palette, title = stringResource(R.string.setting_color)) {
                // Les cinq couleurs se partagent la largeur : toutes restent visibles sans défilement.
                Row(modifier = Modifier.fillMaxWidth()) {
                    ThemeColor.entries.forEach { color ->
                        ColorSwatch(
                            color = color.palette().primary,
                            label = stringResource(color.labelRes()),
                            selected = settings.themeColor == color,
                            onClick = { onThemeColorChange(color) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SettingCard(icon = Icons.Rounded.DarkMode, title = stringResource(R.string.setting_appearance)) {
                ChoiceRow(
                    options = DarkMode.entries,
                    selected = settings.darkMode,
                    label = { stringResource(it.labelRes()) },
                    onSelect = onDarkModeChange,
                )
            }

            SettingCard(icon = Icons.Rounded.FormatSize, title = stringResource(R.string.setting_text_size)) {
                ChoiceRow(
                    options = TextSize.entries,
                    selected = settings.textSize,
                    label = { stringResource(it.labelRes()) },
                    onSelect = onTextSizeChange,
                )
            }

            SettingCard(icon = Icons.Rounded.GridView, title = stringResource(R.string.setting_tile_size)) {
                ChoiceRow(
                    options = TileSize.entries,
                    selected = settings.tileSize,
                    label = { stringResource(it.labelRes()) },
                    onSelect = onTileSizeChange,
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

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                leadingIcon = if (option == selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ThemeColor.labelRes(): Int = when (this) {
    ThemeColor.OCEAN -> R.string.color_ocean
    ThemeColor.FOREST -> R.string.color_forest
    ThemeColor.STRAWBERRY -> R.string.color_strawberry
    ThemeColor.SUN -> R.string.color_sun
    ThemeColor.GALAXY -> R.string.color_galaxy
}

private fun DarkMode.labelRes(): Int = when (this) {
    DarkMode.SYSTEM -> R.string.appearance_system
    DarkMode.LIGHT -> R.string.appearance_light
    DarkMode.DARK -> R.string.appearance_dark
}

private fun TextSize.labelRes(): Int = when (this) {
    TextSize.NORMAL -> R.string.text_size_normal
    TextSize.LARGE -> R.string.text_size_large
    TextSize.XLARGE -> R.string.text_size_xlarge
}

private fun EpisodeOrder.labelRes(): Int = when (this) {
    EpisodeOrder.OLDEST_FIRST -> R.string.episode_order_oldest_first
    EpisodeOrder.NEWEST_FIRST -> R.string.episode_order_newest_first
}

private fun TileSize.labelRes(): Int = when (this) {
    TileSize.SMALL -> R.string.tile_size_small
    TileSize.LARGE -> R.string.tile_size_large
}
