package fr.podkids.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.podkids.app.data.PodcastRepository
import fr.podkids.app.data.catalog.CatalogApi
import fr.podkids.app.data.catalog.CatalogCategory
import fr.podkids.app.data.catalog.CatalogPodcast
import fr.podkids.app.data.rss.InvalidFeedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogError { NETWORK, INVALID_FEED }

data class CatalogUiState(
    val query: String = "",
    /** Recherche actuellement affichée (vide = classement de la rubrique). */
    val activeSearch: String = "",
    val category: CatalogCategory = CatalogCategory.TOP,
    val loading: Boolean = false,
    val results: List<CatalogPodcast> = emptyList(),
    val listError: Boolean = false,
    /** Ouverture d'un podcast en cours (chargement de son flux RSS). */
    val opening: Boolean = false,
    val openError: CatalogError? = null,
)

class CatalogViewModel(
    private val repository: PodcastRepository,
    private val catalog: CatalogApi,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private val _openPodcast = Channel<Long>(Channel.BUFFERED)

    /** Identifiants des podcasts à ouvrir une fois leur flux chargé. */
    val openPodcast = _openPodcast.receiveAsFlow()

    private var loadJob: Job? = null
    private val topCache = mutableMapOf<CatalogCategory, List<CatalogPodcast>>()

    init {
        loadTop(CatalogCategory.TOP)
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        if (query.isBlank() && _state.value.activeSearch.isNotEmpty()) loadTop(_state.value.category)
    }

    fun search() {
        val term = _state.value.query.trim()
        if (term.isEmpty()) {
            loadTop(_state.value.category)
            return
        }
        load(activeSearch = term, category = _state.value.category) { catalog.search(term) }
    }

    fun selectCategory(category: CatalogCategory) {
        _state.update { it.copy(query = "") }
        loadTop(category)
    }

    fun retry() {
        if (_state.value.activeSearch.isNotEmpty()) search() else loadTop(_state.value.category)
    }

    fun open(podcast: CatalogPodcast) = openFeed(podcast.feedUrl, podcast.artworkUrl, subscribe = false)

    fun addByRssUrl(url: String) = openFeed(url, fallbackImageUrl = null, subscribe = true)

    fun dismissOpenError() = _state.update { it.copy(openError = null) }

    private fun loadTop(category: CatalogCategory) {
        load(activeSearch = "", category = category) {
            topCache[category] ?: catalog.top(category).also { if (it.isNotEmpty()) topCache[category] = it }
        }
    }

    private fun load(activeSearch: String, category: CatalogCategory, fetch: suspend () -> List<CatalogPodcast>) {
        loadJob?.cancel()
        _state.update {
            it.copy(activeSearch = activeSearch, category = category, loading = true, listError = false, results = emptyList())
        }
        loadJob = viewModelScope.launch {
            try {
                val results = fetch()
                _state.update { it.copy(loading = false, results = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, listError = true) }
            }
        }
    }

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
