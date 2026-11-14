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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.podkids.app.R
import fr.podkids.app.data.db.PodcastEntity
import fr.podkids.app.data.settings.UiSettings
import fr.podkids.app.playback.PlayerUiState
import fr.podkids.app.ui.components.Artwork

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    /** null tant que la bibliothèque n'est pas chargée. */
    library: List<PodcastEntity>?,
    settings: UiSettings,
    playerState: PlayerUiState,
    onOpenSettings: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenPodcast: (Long) -> Unit,
    onTogglePlay: () -> Unit,
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = if (settings.childName.isBlank()) {
                        stringResource(R.string.greeting)
                    } else {
                        stringResource(R.string.greeting_named, settings.childName)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                CatalogCard(onClick = onOpenCatalog)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                NowPlayingCard(state = playerState, onClick = onOpenPlayer, onTogglePlay = onTogglePlay)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.my_podcasts),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (library != null && library.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.library_empty),
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
        }
    }
}

/** Grande carte d'accès : pastille d'icône, titre et sous-titre, sur un dégradé de couleur. */
@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: List<Color>,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
) {
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
                .background(Brush.linearGradient(colors))
                .padding(18.dp),
        ) {
            if (leading != null) {
                leading()
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun CatalogCard(onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    FeatureCard(
        icon = Icons.Rounded.Explore,
        title = stringResource(R.string.catalog),
        subtitle = stringResource(R.string.catalog_card_subtitle),
        colors = listOf(palette.primary.forCard(), palette.secondary.forCard()),
        onClick = onClick,
    )
}

@Composable
private fun NowPlayingCard(state: PlayerUiState, onClick: () -> Unit, onTogglePlay: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val current = state.current
    val colors = listOf(palette.tertiary.forCard(), palette.primary.forCard())
    if (current == null) {
        FeatureCard(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = stringResource(R.string.now_playing),
            subtitle = stringResource(R.string.nothing_playing),
            colors = colors,
            onClick = onClick,
        )
    } else {
        FeatureCard(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = current.title,
            subtitle = if (state.upcoming.isEmpty()) {
                current.podcastTitle
            } else {
                pluralStringResource(R.plurals.queue_count, state.upcoming.size, state.upcoming.size)
            },
            colors = colors,
            onClick = onClick,
            leading = { Artwork(url = current.artworkUrl, cornerRadius = 16.dp, modifier = Modifier.size(68.dp)) },
            trailing = {
                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(56.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                        modifier = Modifier.size(34.dp),
                    )
                }
            },
        )
    }
}

/** Les cartes portent du texte blanc : en thème sombre, les couleurs claires sont assombries. */
private fun Color.forCard(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.5f) Color(red * 0.55f, green * 0.55f, blue * 0.55f) else this
}

@Composable
fun PodcastTile(title: String, imageUrl: String?, onClick: () -> Unit, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Artwork(
            url = imageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
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
