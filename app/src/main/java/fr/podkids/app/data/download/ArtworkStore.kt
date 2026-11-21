package fr.podkids.app.data.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fr.podkids.app.data.Http
import fr.podkids.app.data.db.EpisodeEntity
import fr.podkids.app.data.db.PodcastEntity
import fr.podkids.app.data.db.PodkidsDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Copies locales des images, pour qu'elles s'affichent hors connexion : la pochette du podcast et,
 * si elle est différente, l'image de chaque épisode téléchargé. Les images sont réduites à
 * [MAX_SIDE_PX] pixels de côté pour limiter la place occupée.
 */
class ArtworkStore(
    context: Context,
    private val httpClient: OkHttpClient,
    database: PodkidsDatabase,
) {
    private val directory = File(context.filesDir, "artwork")
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()

    /** Copie locale de la pochette du podcast ; null si elle n'a pas pu être téléchargée. */
    suspend fun ensurePodcastArtwork(podcast: PodcastEntity): String? {
        podcast.localImagePath?.takeIf { File(it).isFile }?.let { return it }
        val url = podcast.imageUrl ?: return null
        val path = download(url, File(directory, podcastFileName(podcast.id))) ?: return null
        podcastDao.setLocalImage(podcast.id, path)
        return path
    }

    /**
     * Copie locale de l'image d'un épisode. Quand l'épisode n'a pas d'image propre (ou la même que
     * le podcast), la pochette du podcast est réutilisée au lieu d'être téléchargée deux fois.
     */
    suspend fun ensureEpisodeArtwork(episode: EpisodeEntity, podcast: PodcastEntity) {
        episode.localImagePath?.takeIf { File(it).isFile }?.let { return }
        // La pochette du podcast sert aussi au mini-lecteur et à l'accueil : on l'assure dans tous les cas.
        val podcastPath = ensurePodcastArtwork(podcast)
        val ownImage = episode.imageUrl?.takeIf { it != podcast.imageUrl }
        val path = if (ownImage == null) {
            podcastPath
        } else {
            download(ownImage, File(directory, episodeFileName(episode.id)))
        }
        if (path != null) episodeDao.setLocalImage(episode.id, path)
    }

    /** Supprime l'image propre d'un épisode (jamais la pochette partagée du podcast). */
    suspend fun deleteEpisodeArtwork(episodeId: Long) = withContext(Dispatchers.IO) {
        File(directory, episodeFileName(episodeId)).delete()
        Unit
    }

    suspend fun deletePodcastArtwork(podcastId: Long) = withContext(Dispatchers.IO) {
        File(directory, podcastFileName(podcastId)).delete()
        Unit
    }

    /**
     * Rattrapage, au démarrage : télécharge les images des épisodes déjà téléchargés qui n'en ont
     * pas encore (épisodes téléchargés avant cette fonction, ou hors connexion). Sans effet en
     * l'absence de réseau ; réessayé au prochain démarrage.
     */
    suspend fun backfill() {
        for (episode in episodeDao.downloadedWithoutLocalImage()) {
            val podcast = podcastDao.get(episode.podcastId) ?: continue
            try {
                ensureEpisodeArtwork(episode, podcast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Pas de réseau : inutile d'insister pour les autres épisodes.
                if (e is IOException) return
            }
        }
    }

    /** Télécharge une image, la réduit et l'enregistre ; renvoie son chemin, ou null en cas d'échec. */
    private suspend fun download(url: String, target: File): String? = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val request = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        val bytes = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body
            if (body.contentLength() > MAX_DOWNLOAD_BYTES) return@withContext null
            body.bytes()
        }
        val bitmap = decodeScaled(bytes) ?: return@withContext null
        val temp = File(directory, target.name + ".part")
        try {
            FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
            bitmap.recycle()
        }
        target.delete()
        if (!temp.renameTo(target)) {
            temp.delete()
            return@withContext null
        }
        target.absolutePath
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE_PX)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val largest = maxOf(decoded.width, decoded.height)
        if (largest <= MAX_SIDE_PX) return decoded
        val scale = MAX_SIDE_PX.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    companion object {
        const val MAX_SIDE_PX = 800
        private const val JPEG_QUALITY = 88
        private const val MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024

        fun podcastFileName(podcastId: Long) = "podcast_$podcastId.jpg"
        fun episodeFileName(episodeId: Long) = "episode_$episodeId.jpg"

        /** Plus grande puissance de 2 qui garde l'image au-dessus de [maxSide] après réduction. */
        fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
            return sample
        }
    }
}
