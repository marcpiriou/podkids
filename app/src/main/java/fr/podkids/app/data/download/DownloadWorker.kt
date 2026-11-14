package fr.podkids.app.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.podkids.app.PodkidsApp
import fr.podkids.app.data.catalog.CatalogApi
import fr.podkids.app.data.db.DownloadState
import fr.podkids.app.data.db.EpisodeEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Télécharge le fichier audio d'un épisode dans le stockage privé de l'application.
 * Le fichier partiel est conservé entre deux tentatives et repris via l'en-tête HTTP Range.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PodkidsApp).container
        val dao = container.database.episodeDao()
        val episodeId = inputData.getLong(KEY_EPISODE_ID, -1L)
        val episode = dao.get(episodeId) ?: return Result.failure()

        val target = EpisodeDownloader.targetFile(applicationContext, episode)
        val part = EpisodeDownloader.partFile(applicationContext, episode)

        return try {
            dao.setDownloadState(episodeId, DownloadState.DOWNLOADING, 0)
            withContext(Dispatchers.IO) {
                download(container.httpClient, episode, part) { progress ->
                    dao.updateDownloadProgress(episodeId, progress)
                }
                target.delete()
                if (!part.renameTo(target)) throw IOException("Impossible de finaliser le fichier")
            }
            dao.setDownloaded(episodeId, target.absolutePath)
            Result.success()
        } catch (e: CancellationException) {
            // Annulation : par l'utilisateur (l'état est remis à zéro par EpisodeDownloader.remove)
            // ou par le système (le travail sera relancé, on repasse donc en attente).
            withContext(NonCancellable) { dao.requeueIfDownloading(episodeId) }
            throw e
        } catch (e: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                dao.setDownloadState(episodeId, DownloadState.QUEUED, 0)
                Result.retry()
            } else {
                part.delete()
                dao.setDownloadState(episodeId, DownloadState.FAILED, 0)
                Result.failure()
            }
        } catch (e: Exception) {
            // Erreur non récupérable (adresse audio invalide, par exemple) : inutile de réessayer.
            part.delete()
            dao.setDownloadState(episodeId, DownloadState.FAILED, 0)
            Result.failure()
        }
    }

    private suspend fun download(
        client: OkHttpClient,
        episode: EpisodeEntity,
        part: File,
        onProgress: suspend (Int) -> Unit,
    ) {
        val existing = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(episode.audioUrl)
            .header("User-Agent", CatalogApi.USER_AGENT)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416) {
                // Reprise impossible : on repart de zéro à la prochaine tentative.
                part.delete()
                throw IOException("Reprise refusée par le serveur")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val resumed = response.code == 206 && existing > 0
            val alreadyHave = if (resumed) existing else 0L
            val bodyLength = response.body.contentLength()
            val total = if (bodyLength > 0) alreadyHave + bodyLength else -1L

            response.body.byteStream().use { input ->
                FileOutputStream(part, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = alreadyHave
                    var lastProgress = Int.MIN_VALUE
                    while (true) {
                        // currentCoroutineContext() : la propriété coroutineContext du worker est un simple dispatcher.
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else -1
                        if (progress != lastProgress && (progress == -1 || progress - lastProgress >= 2)) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        private const val MAX_ATTEMPTS = 5
    }
}
