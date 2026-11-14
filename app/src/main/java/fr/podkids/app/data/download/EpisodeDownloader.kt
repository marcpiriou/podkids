package fr.podkids.app.data.download

import android.content.Context
import android.os.Environment
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import fr.podkids.app.data.db.DownloadState
import fr.podkids.app.data.db.EpisodeDao
import fr.podkids.app.data.db.EpisodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Pilote le téléchargement des épisodes pour l'écoute hors connexion (WorkManager). */
class EpisodeDownloader(
    private val context: Context,
    private val episodeDao: EpisodeDao,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    suspend fun enqueue(episodeId: Long) {
        val episode = episodeDao.get(episodeId) ?: return
        if (episode.downloadState == DownloadState.DOWNLOADED ||
            episode.downloadState == DownloadState.DOWNLOADING
        ) {
            return
        }
        episodeDao.setDownloadState(episodeId, DownloadState.QUEUED, 0)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to episodeId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        // REPLACE : relance proprement un téléchargement précédemment en échec.
        workManager.enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun enqueueAll(podcastId: Long) {
        episodeDao.idsToDownload(podcastId).forEach { enqueue(it) }
    }

    /** Annule un téléchargement en cours ou supprime le fichier déjà téléchargé. */
    suspend fun remove(episodeId: Long) {
        workManager.cancelUniqueWork(workName(episodeId))
        val episode = episodeDao.get(episodeId) ?: return
        withContext(Dispatchers.IO) {
            episode.localPath?.let { File(it).delete() }
            targetFile(context, episode).delete()
            partFile(context, episode).delete()
        }
        episodeDao.clearDownload(episodeId)
    }

    suspend fun removeAllForPodcast(podcastId: Long) {
        episodeDao.idsWithDownload(podcastId).forEach { remove(it) }
    }

    companion object {
        private const val TAG = "episode-download"

        fun workName(episodeId: Long) = "download-episode-$episodeId"

        private fun directory(context: Context): File {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
                ?: File(context.filesDir, "podcasts")
            dir.mkdirs()
            return dir
        }

        private val knownExtensions = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "mp4")

        fun targetFile(context: Context, episode: EpisodeEntity): File {
            val extension = episode.audioUrl
                .substringBefore('?')
                .substringAfterLast('.', "")
                .lowercase(Locale.ROOT)
                .takeIf { it in knownExtensions } ?: "mp3"
            return File(directory(context), "episode_${episode.id}.$extension")
        }

        fun partFile(context: Context, episode: EpisodeEntity): File =
            File(directory(context), "episode_${episode.id}.part")
    }
}
