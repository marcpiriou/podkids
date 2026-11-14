package fr.podkids.app.ui.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import fr.podkids.app.data.catalog.CatalogCategory
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
    onSearch: () -> Unit,
    onSelectCategory: (CatalogCategory) -> Unit,
    onRetry: () -> Unit,
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
                icon = { Icon(Icons.Rounded.RssFeed, contentDescription = null) },
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
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSearch()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                CatalogCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.activeSearch.isEmpty() && state.category == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(stringResource(category.labelRes())) },
                        leadingIcon = {
                            Icon(category.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.listError -> CatalogMessage(
                        text = stringResource(R.string.catalog_error),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRetry,
                    )
                    state.results.isEmpty() -> CatalogMessage(
                        text = stringResource(
                            if (state.activeSearch.isEmpty()) R.string.catalog_empty else R.string.search_no_result,
                        ),
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

@Composable
private fun CatalogMessage(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

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

private fun CatalogCategory.labelRes(): Int = when (this) {
    CatalogCategory.TOP -> R.string.category_top
    CatalogCategory.STORIES -> R.string.category_stories
    CatalogCategory.LEARNING -> R.string.category_learning
    CatalogCategory.ANIMALS -> R.string.category_animals
}

private fun CatalogCategory.icon(): ImageVector = when (this) {
    CatalogCategory.TOP -> Icons.Rounded.Star
    CatalogCategory.STORIES -> Icons.Rounded.AutoStories
    CatalogCategory.LEARNING -> Icons.Rounded.School
    CatalogCategory.ANIMALS -> Icons.Rounded.Pets
}
