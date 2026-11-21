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

/** Durée à sauter au début de l'épisode (réglage du podcast), en ms. */
val MediaItem.skipIntroMs: Long get() = mediaMetadata.extras?.getLong(EXTRA_SKIP_INTRO_MS) ?: 0L

/** Passer à la suite quand il reste moins que cette durée (réglage du podcast), en ms. */
val MediaItem.skipOutroMs: Long get() = mediaMetadata.extras?.getLong(EXTRA_SKIP_OUTRO_MS) ?: 0L
