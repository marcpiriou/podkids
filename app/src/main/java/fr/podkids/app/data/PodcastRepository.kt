package fr.podkids.app.data

import fr.podkids.app.data.db.EpisodeEntity
import fr.podkids.app.data.db.EpisodeWithPodcast
import fr.podkids.app.data.db.PodcastEntity
import fr.podkids.app.data.db.PodkidsDatabase
import fr.podkids.app.data.download.EpisodeDownloader
import fr.podkids.app.data.rss.InvalidFeedException
import fr.podkids.app.data.rss.ParsedFeed
import fr.podkids.app.data.rss.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class PodcastRepository(
    private val database: PodkidsDatabase,
    private val httpClient: OkHttpClient,
    private val downloader: EpisodeDownloader,
) {
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()
    private val queueDao = database.queueDao()

    /** @param downloadedOnly ne garder que les podcasts ayant au moins un épisode téléchargé. */
    fun observeLibrary(downloadedOnly: Boolean = false): Flow<List<PodcastEntity>> =
        if (downloadedOnly) podcastDao.observeWithDownloads() else podcastDao.observeSubscribed()

    fun observePodcast(id: Long): Flow<PodcastEntity?> = podcastDao.observe(id)

    fun observeEpisodes(podcastId: Long): Flow<List<EpisodeEntity>> = episodeDao.observeForPodcast(podcastId)

    suspend fun getEpisodeWithPodcast(episodeId: Long): EpisodeWithPodcast? = episodeDao.getWithPodcast(episodeId)

    /**
     * Charge un flux RSS et l'enregistre en base.
     * @param subscribe true pour l'ajouter directement à « Mes podcasts », false pour un simple aperçu.
     * @return l'identifiant local du podcast.
     * @throws InvalidFeedException si l'adresse ou le contenu n'est pas un flux de podcast.
     * @throws IOException en cas d'erreur réseau.
     */
    suspend fun openFeed(feedUrl: String, fallbackImageUrl: String? = null, subscribe: Boolean = false): Long {
        val url = normalizeFeedUrl(feedUrl) ?: throw InvalidFeedException("Adresse de flux invalide")
        val feed = fetchFeed(url)
        if (feed.episodes.isEmpty()) throw InvalidFeedException("Ce flux ne contient aucun épisode audio")

        val now = System.currentTimeMillis()
        val existing = podcastDao.getByFeedUrl(url)
        val podcastId = if (existing == null) {
            podcastDao.insert(
                PodcastEntity(
                    feedUrl = url,
                    title = feed.title.ifBlank { url },
                    author = feed.author,
                    description = feed.description,
                    imageUrl = feed.imageUrl ?: fallbackImageUrl,
                    subscribed = subscribe,
                    addedAt = now,
                    lastRefreshedAt = now,
                ),
            )
        } else {
            podcastDao.update(
                existing.copy(
                    title = feed.title.ifBlank { existing.title },
                    author = feed.author,
                    description = feed.description,
                    imageUrl = feed.imageUrl ?: existing.imageUrl ?: fallbackImageUrl,
                    subscribed = existing.subscribed || subscribe,
                    lastRefreshedAt = now,
                ),
            )
            existing.id
        }
        storeEpisodes(podcastId, feed)
        return podcastId
    }

    /** Recharge le flux d'un podcast déjà connu pour récupérer les nouveaux épisodes. */
    suspend fun refresh(podcastId: Long) {
        val podcast = podcastDao.get(podcastId) ?: return
        openFeed(podcast.feedUrl)
    }

    suspend fun subscribe(podcastId: Long) = podcastDao.setSubscribed(podcastId, true)

    /** Retire un podcast de la bibliothèque, avec ses épisodes téléchargés. */
    suspend fun removePodcast(podcastId: Long) {
        downloader.removeAllForPodcast(podcastId)
        podcastDao.delete(podcastId)
    }

    suspend fun downloadEpisode(episodeId: Long) {
        val episode = episodeDao.get(episodeId) ?: return
        // Télécharger un épisode revient à vouloir garder le podcast.
        podcastDao.setSubscribed(episode.podcastId, true)
        downloader.enqueue(episodeId)
    }

    suspend fun downloadAll(podcastId: Long) {
        podcastDao.setSubscribed(podcastId, true)
        downloader.enqueueAll(podcastId)
    }

    suspend fun removeDownload(episodeId: Long) = downloader.remove(episodeId)

    suspend fun savePosition(episodeId: Long, positionMs: Long) =
        episodeDao.savePosition(episodeId, positionMs.coerceAtLeast(0))

    suspend fun markCompleted(episodeId: Long) = episodeDao.markCompleted(episodeId)

    /** Marque un épisode comme lu ou non lu (non lu : reprise depuis le début). */
    suspend fun setPlayed(episodeId: Long, played: Boolean) =
        if (played) episodeDao.markCompleted(episodeId) else episodeDao.markUnplayed(episodeId)

    /** Liste de lecture enregistrée et index de l'épisode en cours dans cette liste. */
    suspend fun loadQueue(): Pair<List<EpisodeWithPodcast>, Int> = queueDao.loadWithCurrentIndex()

    suspend fun saveQueue(episodeIds: List<Long>, currentIndex: Int) = queueDao.replace(episodeIds, currentIndex)

    suspend fun cleanUpPreviews() = podcastDao.deleteUnusedPreviews()

    private suspend fun storeEpisodes(podcastId: Long, feed: ParsedFeed) {
        episodeDao.upsertFromFeed(
            feed.episodes.map { episode ->
                EpisodeEntity(
                    podcastId = podcastId,
                    guid = episode.guid,
                    title = episode.title,
                    description = episode.description,
                    audioUrl = episode.audioUrl,
                    imageUrl = episode.imageUrl,
                    publishedAt = episode.publishedAt,
                    durationSec = episode.durationSec,
                )
            },
        )
    }

    private suspend fun fetchFeed(url: String): ParsedFeed = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            RssParser.parse(response.body.byteStream())
        }
    }

    companion object {
        /** Tolère les saisies sans schéma et les liens « feed:// » ou « podcast:// ». */
        fun normalizeFeedUrl(input: String): String? {
            var url = input.trim()
            if (url.isEmpty()) return null
            for (scheme in listOf("feed://", "podcast://", "pcast://", "itpc://")) {
                if (url.startsWith(scheme, ignoreCase = true)) url = "https://" + url.substring(scheme.length)
            }
            if (url.startsWith("feed:", ignoreCase = true)) url = url.substring("feed:".length)
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }
            val parsed = url.toHttpUrlOrNull() ?: return null
            if (!parsed.host.contains('.')) return null
            return parsed.toString()
        }
    }
}
