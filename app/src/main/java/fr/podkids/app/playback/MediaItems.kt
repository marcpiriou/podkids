package fr.podkids.app.playback

import android.net.Uri
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
        .build()
    return MediaItem.Builder()
        .setMediaId(episode.id.toString())
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

val MediaItem.episodeId: Long? get() = mediaId.toLongOrNull()
