package fr.podkids.app.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import fr.podkids.app.data.catalog.Catalog
import fr.podkids.app.data.catalog.CatalogPodcast
import fr.podkids.app.data.settings.UiSettings
import fr.podkids.app.ui.home.PodcastTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    settings: UiSettings,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleAge: (Int) -> Unit,
    onOpen: (CatalogPodcast) -> Unit,
    onAddByRssUrl: (String) -> Unit,
    onDismissOpenError: () -> Unit,
) {
    var showRssDialog by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showRssDialog = true },
                // Le composant masque son libellé aux lecteurs d'écran : c'est l'icône qui porte la description.
                icon = { Icon(Icons.Rounded.RssFeed, contentDescription = stringResource(R.string.add_rss)) },
                text = { Text(stringResource(R.string.add_rss)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            AgeSelector(selected = state.ages, onToggle = onToggleAge)

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.results.isEmpty() -> Text(
                        text = stringResource(R.string.catalog_no_result),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = settings.tileSize.minWidthDp.dp),
                        // Marge basse pour que le bouton flottant ne masque pas la dernière ligne.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.results, key = { it.id }) { podcast ->
                            PodcastTile(
                                title = podcast.title,
                                subtitle = podcast.author,
                                imageUrl = podcast.artworkUrl,
                                badge = pluralStringResource(R.plurals.age_from, podcast.minAge, podcast.minAge),
                                onClick = { onOpen(podcast) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRssDialog) {
        AddRssDialog(
            onDismiss = { showRssDialog = false },
            onConfirm = { url ->
                showRssDialog = false
                onAddByRssUrl(url)
            },
        )
    }

    if (state.opening) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.size(16.dp))
                    Text(stringResource(R.string.loading_podcast))
                }
            },
        )
    }

    state.openError?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissOpenError,
            confirmButton = {
                TextButton(onClick = onDismissOpenError) { Text(stringResource(R.string.ok)) }
            },
            title = { Text(stringResource(R.string.open_error_title)) },
            text = {
                Text(
                    stringResource(
                        when (error) {
                            CatalogError.NETWORK -> R.string.open_error_network
                            CatalogError.INVALID_FEED -> R.string.open_error_invalid
                        },
                    ),
                )
            },
        )
    }
}

/**
 * Sélecteur d'âge à choix multiples : « À partir de » 3 à 12 ans. Rien de coché = tous les âges.
 * Les choix sont répartis sur deux lignes de largeur égale, pour rester tous visibles quelle que soit
 * la taille du texte.
 */
@Composable
private fun AgeSelector(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.age_selector_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Catalog.AGE_CHOICES.chunked(AGES_PER_ROW).forEach { rowAges ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowAges.forEach { age ->
                    val isSelected = age in selected
                    Surface(
                        checked = isSelected,
                        onCheckedChange = { onToggle(age) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = pluralStringResource(R.plurals.age_years, age, age),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val AGES_PER_ROW = 5

@Composable
private fun AddRssDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RssFeed, contentDescription = null) },
        title = { Text(stringResource(R.string.add_rss)) },
        text = {
            Column(modifier = Modifier.imePadding()) {
                Text(stringResource(R.string.add_rss_explanation))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.rss_url_label)) },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) onConfirm(url) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
