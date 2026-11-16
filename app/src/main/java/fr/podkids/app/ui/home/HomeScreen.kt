package fr.podkids.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import fr.podkids.app.data.db.PodcastEntity
import fr.podkids.app.data.settings.UiSettings
import fr.podkids.app.ui.components.Artwork

/**
 * Accueil : les podcasts de l'enfant, suivis de la carte d'accès au catalogue, qui défile avec la liste
 * (sauf si le catalogue est réservé aux réglages).
 * La lecture en cours n'apparaît que dans le mini-lecteur, en bas de l'écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    /** null tant que la bibliothèque n'est pas chargée. */
    library: List<PodcastEntity>?,
    settings: UiSettings,
    onOpenSettings: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenPodcast: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                },
                // Icône du mode courant : appuyer bascule entre mode enfant et mode adulte.
                actions = {
                    IconButton(onClick = onToggleMode) {
                        Icon(
                            imageVector = if (settings.childMode) Icons.Rounded.ChildCare else Icons.Rounded.SupervisorAccount,
                            contentDescription = stringResource(
                                if (settings.childMode) R.string.switch_to_adult_mode else R.string.switch_to_child_mode,
                            ),
                            tint = if (settings.childMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = settings.tileSize.minWidthDp.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (library != null && library.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(
                            when {
                                settings.onlyDownloaded -> R.string.library_empty_downloaded_only
                                settings.catalogHidden -> R.string.library_empty_no_catalog
                                else -> R.string.library_empty
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                    )
                }
            }
            items(library.orEmpty(), key = { it.id }) { podcast ->
                PodcastTile(
                    title = podcast.title,
                    imageUrl = podcast.imageUrl,
                    onClick = { onOpenPodcast(podcast.id) },
                )
            }
            // Le catalogue vient après les podcasts de l'enfant et défile avec eux,
            // sauf s'il est réservé aux réglages.
            if (library != null && !settings.catalogHidden) {
                item(key = "catalog", span = { GridItemSpan(maxLineSpan) }) {
                    CatalogCard(onClick = onOpenCatalog)
                }
            }
        }
    }
}

/** Grande carte d'accès au catalogue : pastille d'icône, titre et sous-titre, sur un dégradé de couleur. */
@Composable
private fun CatalogCard(onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(palette.primary.forCard(), palette.secondary.forCard())))
                .padding(18.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.catalog),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.catalog_card_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Les cartes portent du texte blanc : en thème sombre, les couleurs claires sont assombries. */
private fun Color.forCard(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.5f) Color(red * 0.55f, green * 0.55f, blue * 0.55f) else this
}

@Composable
fun PodcastTile(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    subtitle: String? = null,
    /** Pastille affichée sur la pochette (âge conseillé dans le catalogue). */
    badge: String? = null,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box {
            Artwork(
                url = imageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
