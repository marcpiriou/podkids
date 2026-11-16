package fr.podkids.app.ui.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.podkids.app.data.PodcastRepository
import fr.podkids.app.data.db.EpisodeEntity
import fr.podkids.app.data.db.PodcastEntity
import fr.podkids.app.data.settings.EpisodeOrder
import fr.podkids.app.data.sortedByOrder
import fr.podkids.app.playback.PlayerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PodcastEvent { ADDED_TO_QUEUE, ALREADY_IN_QUEUE, REFRESH_FAILED, REMOVED }

class PodcastViewModel(
    private val podcastId: Long,
    private val repository: PodcastRepository,
    private val player: PlayerConnection,
    /** Ordre par défaut défini dans les réglages. */
    defaultOrder: EpisodeOrder,
) : ViewModel() {

    /** Ordre courant : celui des réglages à l'ouverture, puis celui choisi avec l'icône de tri. */
    private val _order = MutableStateFlow(defaultOrder)
    val order: StateFlow<EpisodeOrder> = _order.asStateFlow()

    val podcast: StateFlow<PodcastEntity?> = repository.observePodcast(podcastId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val episodes: StateFlow<List<EpisodeEntity>> =
        combine(repository.observeEpisodes(podcastId), _order) { list, order -> list.sortedByOrder(order) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _events = Channel<PodcastEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Rafraîchissement automatique si le flux n'a pas été relu récemment.
        viewModelScope.launch {
            val current = repository.observePodcast(podcastId).first() ?: return@launch
            if (System.currentTimeMillis() - current.lastRefreshedAt > AUTO_REFRESH_AFTER_MS) refresh(silent = true)
        }
    }

    fun refresh(silent: Boolean = false) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                repository.refresh(podcastId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!silent) _events.send(PodcastEvent.REFRESH_FAILED)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun toggleOrder() {
        _order.value = when (_order.value) {
            EpisodeOrder.NEWEST_FIRST -> EpisodeOrder.OLDEST_FIRST
            EpisodeOrder.OLDEST_FIRST -> EpisodeOrder.NEWEST_FIRST
        }
    }

    fun subscribe() {
        viewModelScope.launch { repository.subscribe(podcastId) }
    }

    fun remove() {
        viewModelScope.launch {
            repository.removePodcast(podcastId)
            _events.send(PodcastEvent.REMOVED)
        }
    }

    fun play(episodeId: Long) {
        viewModelScope.launch {
            repository.getEpisodeWithPodcast(episodeId)?.let(player::playNow)
        }
    }

    fun addToQueue(episodeId: Long) {
        viewModelScope.launch {
            val entry = repository.getEpisodeWithPodcast(episodeId) ?: return@launch
            _events.send(if (player.addToQueue(entry)) PodcastEvent.ADDED_TO_QUEUE else PodcastEvent.ALREADY_IN_QUEUE)
        }
    }

    fun download(episodeId: Long) {
        viewModelScope.launch { repository.downloadEpisode(episodeId) }
    }

    fun downloadAll() {
        viewModelScope.launch { repository.downloadAll(podcastId) }
    }

    fun removeDownload(episodeId: Long) {
        viewModelScope.launch { repository.removeDownload(episodeId) }
    }

    private companion object {
        const val AUTO_REFRESH_AFTER_MS = 60 * 60 * 1000L
    }
}
