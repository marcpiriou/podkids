package fr.podkids.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import fr.podkids.app.AppContainer
import fr.podkids.app.MainActivity
import fr.podkids.app.PodkidsApp
import fr.podkids.app.data.Http
import fr.podkids.app.data.db.EpisodeWithPodcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service de lecture audio (Media3). Il porte le lecteur ExoPlayer et la session média,
 * ce qui fournit la lecture en arrière-plan, la notification et les commandes de l'écran verrouillé.
 *
 * Convention : la liste de lecture du lecteur contient les derniers épisodes écoutés (historique,
 * pour le bouton « épisode précédent »), l'épisode en cours, puis la file d'attente. L'historique est
 * limité à [MAX_HISTORY] épisodes.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var container: AppContainer
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** La file n'est pas persistée tant que la restauration initiale n'est pas terminée. */
    private var restored = false

    override fun onCreate() {
        super.onCreate()
        container = (application as PodkidsApp).container

        // Les adresses audio des podcasts passent souvent par des redirections http <-> https
        // (mesure d'audience) : elles sont refusées par défaut, on les autorise explicitement.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        player.addListener(playerListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        serviceScope.launch { restoreQueue() }
        serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (player.isPlaying) saveCurrentPosition()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Application balayée des tâches récentes : on ne garde le service que si la lecture est en cours.
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveCurrentPosition()
        player.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun restoreQueue() {
        val (saved, currentIndex) = runCatching { container.repository.loadQueue() }.getOrDefault(emptyList<EpisodeWithPodcast>() to 0)
        // Si une lecture a déjà été demandée entre-temps, elle est prioritaire.
        if (saved.isNotEmpty() && player.mediaItemCount == 0) {
            player.setMediaItems(saved.map { it.toMediaItem() }, currentIndex, saved[currentIndex].resumePositionMs)
        }
        restored = true
    }

    private fun persistQueue() {
        if (!restored) return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).episodeId }
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, (ids.size - 1).coerceAtLeast(0))
        container.appScope.launch { container.repository.saveQueue(ids, currentIndex) }
    }

    private fun saveCurrentPosition() {
        val id = player.currentMediaItem?.episodeId ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        savePosition(id, player.currentPosition)
    }

    private fun savePosition(episodeId: Long, positionMs: Long) {
        container.appScope.launch { container.repository.savePosition(episodeId, positionMs) }
    }

    private fun markCompleted(episodeId: Long) {
        container.appScope.launch { container.repository.markCompleted(episodeId) }
    }

    /** Ne conserve que les [MAX_HISTORY] derniers épisodes écoutés avant l'épisode en cours. */
    private fun trimPlayedItems() {
        val excess = player.currentMediaItemIndex - MAX_HISTORY
        if (excess > 0 && player.currentMediaItemIndex < player.mediaItemCount) player.removeMediaItems(0, excess)
    }

    private val playerListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val previousId = oldPosition.mediaItem?.episodeId ?: return
            if (previousId == newPosition.mediaItem?.episodeId) return
            // On quitte un épisode : terminé s'il est allé au bout, sinon on mémorise où il en était.
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                markCompleted(previousId)
            } else {
                savePosition(previousId, oldPosition.positionMs)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            trimPlayedItems()
            // L'index de l'épisode en cours change : on l'enregistre avec la liste.
            persistQueue()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                trimPlayedItems()
                persistQueue()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                player.currentMediaItem?.episodeId?.let { markCompleted(it) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveCurrentPosition()
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * Les contrôleurs n'envoient que l'identifiant de l'épisode : l'adresse à lire (fichier local
         * ou flux distant) est résolue ici, à partir de la base.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            mediaItems.mapNotNull { item ->
                item.episodeId
                    ?.let { container.repository.getEpisodeWithPodcast(it) }
                    ?.toMediaItem()
            }.toMutableList()
        }
    }

    companion object {
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
        private const val MAX_HISTORY = 20
    }
}
