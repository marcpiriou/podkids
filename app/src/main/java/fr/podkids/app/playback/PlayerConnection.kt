package fr.podkids.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import fr.podkids.app.data.db.EpisodeWithPodcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(
    val episodeId: Long,
    val title: String,
    val podcastTitle: String,
    val artworkUrl: String?,
)

data class PlayerUiState(
    val connected: Boolean = false,
    /** Épisode en cours, ou null si rien n'est chargé. */
    val current: QueueEntry? = null,
    /** Épisodes à suivre, dans l'ordre de lecture (sans l'épisode en cours). */
    val upcoming: List<QueueEntry> = emptyList(),
    /** true dès que la lecture est demandée, même pendant la mise en mémoire tampon. */
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
)

data class PlaybackProgress(val positionMs: Long = 0, val durationMs: Long = 0) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Pont entre l'interface et [PlaybackService] : encapsule le MediaController et expose l'état de
 * lecture sous forme de flux. Toutes les méthodes doivent être appelées depuis le thread principal.
 */
class PlayerConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val _errors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Émet quand un épisode ne peut pas être lu (hors connexion, fichier introuvable…). */
    val errors: SharedFlow<Unit> = _errors.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            _errors.tryEmit(Unit)
        }
    }

    /** À appeler quand l'interface devient visible (Activity.onStart). */
    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future || future.isCancelled) return@addListener
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(listener)
                publishState(connected)
                startProgressUpdates()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** À appeler quand l'interface n'est plus visible (Activity.onStop). La lecture continue dans le service. */
    fun disconnect() {
        progressJob?.cancel()
        progressJob = null
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _state.value = _state.value.copy(connected = false)
    }

    /** Lit immédiatement un épisode : il remplace l'épisode en cours, la file d'attente est conservée. */
    fun playNow(entry: EpisodeWithPodcast) {
        val player = controller ?: return
        val item = entry.toMediaItem()
        val startMs = entry.resumePositionMs
        val currentIndex = player.currentMediaItemIndex
        val existingIndex = indexOf(player, item.mediaId)
        when {
            player.mediaItemCount == 0 -> player.setMediaItem(item, startMs)
            existingIndex == currentIndex -> Unit
            else -> {
                // L'épisode est placé juste après l'épisode en cours, puis on saute dessus :
                // le service retire alors l'ancien épisode de la liste.
                val target = currentIndex + 1
                if (existingIndex >= 0) {
                    player.moveMediaItem(existingIndex, target)
                } else {
                    player.addMediaItem(target, item)
                }
                player.seekTo(target, startMs)
            }
        }
        player.prepare()
        player.play()
    }

    /** Ajoute un épisode à la fin de la file. @return false s'il y figurait déjà. */
    fun addToQueue(entry: EpisodeWithPodcast): Boolean {
        val player = controller ?: return false
        val item = entry.toMediaItem()
        if (indexOf(player, item.mediaId) >= 0) return false
        if (player.mediaItemCount == 0) {
            player.setMediaItem(item, entry.resumePositionMs)
            player.prepare()
        } else {
            player.addMediaItem(item)
        }
        return true
    }

    /** Lit tout de suite un épisode de la file ; [upcomingIndex] est l'index dans [PlayerUiState.upcoming]. */
    fun playUpcoming(upcomingIndex: Int) {
        val player = controller ?: return
        val index = player.currentMediaItemIndex + 1 + upcomingIndex
        if (index >= player.mediaItemCount) return
        val target = player.currentMediaItemIndex + 1
        if (index != target) player.moveMediaItem(index, target)
        player.seekTo(target, C.TIME_UNSET)
        player.prepare()
        player.play()
    }

    fun removeUpcoming(upcomingIndex: Int) {
        val player = controller ?: return
        val index = player.currentMediaItemIndex + 1 + upcomingIndex
        if (index < player.mediaItemCount) player.removeMediaItem(index)
    }

    fun moveUpcomingUp(upcomingIndex: Int) {
        val player = controller ?: return
        if (upcomingIndex <= 0) return
        val index = player.currentMediaItemIndex + 1 + upcomingIndex
        if (index < player.mediaItemCount) player.moveMediaItem(index, index - 1)
    }

    fun clearUpcoming() {
        val player = controller ?: return
        val from = player.currentMediaItemIndex + 1
        if (from < player.mediaItemCount) player.removeMediaItems(from, player.mediaItemCount)
    }

    fun togglePlay() {
        val player = controller ?: return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE
        ) {
            player.pause()
            return
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
        player.play()
    }

    fun seekBack() {
        controller?.seekBack()
        publishProgress()
    }

    fun seekForward() {
        controller?.seekForward()
        publishProgress()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        publishProgress()
    }

    fun skipToNext() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    private fun indexOf(player: Player, mediaId: String): Int =
        (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == mediaId } ?: -1

    private fun publishState(player: Player) {
        val currentIndex = player.currentMediaItemIndex
        val entries = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toQueueEntry() }
        val active = player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        _state.value = PlayerUiState(
            connected = true,
            current = entries.getOrNull(currentIndex),
            upcoming = if (currentIndex + 1 < entries.size) entries.subList(currentIndex + 1, entries.size) else emptyList(),
            isPlaying = player.playWhenReady && active,
            isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
        )
        publishProgress()
    }

    private fun publishProgress() {
        val player = controller ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        _progress.value = PlaybackProgress(
            positionMs = if (duration > 0) position.coerceAtMost(duration) else position,
            durationMs = duration,
        )
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                publishProgress()
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun MediaItem.toQueueEntry() = QueueEntry(
        episodeId = episodeId ?: -1L,
        title = mediaMetadata.title?.toString().orEmpty(),
        podcastTitle = mediaMetadata.artist?.toString().orEmpty(),
        artworkUrl = mediaMetadata.artworkUri?.toString(),
    )

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
