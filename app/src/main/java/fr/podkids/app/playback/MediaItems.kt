package fr.podkids.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import fr.podkids.app.data.db.EpisodeWithPodcast
import java.io.File

/** L'identifiant de l'épisode sert de mediaId ; le fichier local est préféré au streaming s'il existe. */
fun EpisodeWithPodcast.toMediaItem(): MediaItem {
    val localFile = episode.localPath?.let { File(it) }?.takeIf { it.exists() }
    val uri = if (localFile != null) Uri.fromFile(localFile) else Uri.parse(episode.audioUrl)
    val metadata = MediaMetadata.Builder()
        .setTitle(episode.title)
        .setArtist(podcastTitle)
        .setAlbumTitle(podcastTitle)
        .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
        .setExtras(
            Bundle().apply {
                putLong(EXTRA_SKIP_INTRO_MS, podcastSkipIntroSec * 1000L)
                putLong(EXTRA_SKIP_OUTRO_MS, podcastSkipOutroSec * 1000L)
                // Durée annoncée par le flux (0 = inconnue) et point de départ : temps restant de la file.
                putLong(EXTRA_DURATION_MS, (episode.durationSec ?: 0L) * 1000L)
                putLong(EXTRA_START_MS, startPositionMs)
            },
        )
        .build()
    return MediaItem.Builder()
        .setMediaId(episode.id.toString())
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

val MediaItem.episodeId: Long? get() = mediaId.toLongOrNull()

private const val EXTRA_SKIP_INTRO_MS = "podkids.skipIntroMs"
private const val EXTRA_SKIP_OUTRO_MS = "podkids.skipOutroMs"
private const val EXTRA_DURATION_MS = "podkids.durationMs"
private const val EXTRA_START_MS = "podkids.startMs"

/**
 * Temps qu'il reste à écouter sur cet épisode quand il démarrera (durée moins la reprise ou le début
 * sauté) ; null si le flux n'indique pas la durée.
 */
val MediaItem.remainingMs: Long?
    get() {
        val extras = mediaMetadata.extras ?: return null
        val duration = extras.getLong(EXTRA_DURATION_MS)
        if (duration <= 0) return null
        return (duration - extras.getLong(EXTRA_START_MS)).coerceAtLeast(0)
    }

/** Durée à sauter au début de l'épisode (réglage du podcast), en ms. */
val MediaItem.skipIntroMs: Long get() = mediaMetadata.extras?.getLong(EXTRA_SKIP_INTRO_MS) ?: 0L

/** Passer à la suite quand il reste moins que cette durée (réglage du podcast), en ms. */
val MediaItem.skipOutroMs: Long get() = mediaMetadata.extras?.getLong(EXTRA_SKIP_OUTRO_MS) ?: 0L
