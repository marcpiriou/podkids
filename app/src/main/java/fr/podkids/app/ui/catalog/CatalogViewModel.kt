package fr.podkids.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.podkids.app.data.PodcastRepository
import fr.podkids.app.data.catalog.Catalog
import fr.podkids.app.data.catalog.CatalogCategory
import fr.podkids.app.data.catalog.CatalogPodcast
import fr.podkids.app.data.catalog.CatalogRepository
import fr.podkids.app.data.catalog.CatalogSection
import fr.podkids.app.data.rss.InvalidFeedException
import fr.podkids.app.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogError { NETWORK, INVALID_FEED }

data class CatalogUiState(
    val loading: Boolean = true,
    val query: String = "",
    /** Choix d'âge cochés ; vide = tous les âges. */
    val ages: Set<Int> = emptySet(),
    /** Rubrique retenue ; null = toutes. */
    val category: CatalogCategory? = null,
    /** Podcasts correspondant aux critères, regroupés par rubrique. */
    val sections: List<CatalogSection> = emptyList(),
    /** Ouverture d'un podcast en cours (chargement de son flux RSS). */
    val opening: Boolean = false,
    val openError: CatalogError? = null,
)

class CatalogViewModel(
    private val repository: PodcastRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private val _openPodcast = Channel<Long>(Channel.BUFFERED)

    /** Identifiants des podcasts à ouvrir une fois leur flux chargé. */
    val openPodcast = _openPodcast.receiveAsFlow()

    private var podcasts: List<CatalogPodcast> = emptyList()

    init {
        viewModelScope.launch {
            podcasts = runCatching { catalog.podcasts() }.getOrDefault(emptyList())
            // Les âges cochés sont mémorisés dans les réglages : ils sont retrouvés à chaque visite.
            val savedAges = settings.settings.first().catalogAges
            _state.update { it.copy(loading = false, ages = savedAges).withSections() }
            settings.settings.map { it.catalogAges }.distinctUntilChanged().collect { ages ->
                _state.update { it.copy(ages = ages).withSections() }
            }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query).withSections() }

    fun toggleAge(age: Int) {
        val current = _state.value.ages
        val ages = if (age in current) current - age else current + age
        _state.update { it.copy(ages = ages).withSections() }
        viewModelScope.launch { settings.setCatalogAges(ages) }
    }

    fun selectCategory(category: CatalogCategory?) = _state.update { it.copy(category = category).withSections() }

    fun open(podcast: CatalogPodcast) = openFeed(podcast.feedUrl, podcast.artworkUrl, subscribe = false)

    fun addByRssUrl(url: String) = openFeed(url, fallbackImageUrl = null, subscribe = true)

    fun dismissOpenError() = _state.update { it.copy(openError = null) }

    private fun CatalogUiState.withSections(): CatalogUiState =
        copy(sections = Catalog.filter(podcasts, query, ages, category))

    private fun openFeed(feedUrl: String, fallbackImageUrl: String?, subscribe: Boolean) {
        if (_state.value.opening) return
        _state.update { it.copy(opening = true, openError = null) }
        viewModelScope.launch {
            try {
                val id = repository.openFeed(feedUrl, fallbackImageUrl, subscribe)
                _state.update { it.copy(opening = false) }
                _openPodcast.send(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidFeedException) {
                _state.update { it.copy(opening = false, openError = CatalogError.INVALID_FEED) }
            } catch (e: Exception) {
                _state.update { it.copy(opening = false, openError = CatalogError.NETWORK) }
            }
        }
    }
}
