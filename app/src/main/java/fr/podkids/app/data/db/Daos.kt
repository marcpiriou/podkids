package fr.podkids.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PodcastDao {
    @Query("SELECT * FROM podcasts WHERE subscribed = 1 ORDER BY title COLLATE NOCASE")
    abstract fun observeSubscribed(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    abstract fun observe(id: Long): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    abstract suspend fun get(id: Long): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    abstract suspend fun getByFeedUrl(feedUrl: String): PodcastEntity?

    @Insert
    abstract suspend fun insert(podcast: PodcastEntity): Long

    @Update
    abstract suspend fun update(podcast: PodcastEntity)

    @Query("UPDATE podcasts SET subscribed = :subscribed WHERE id = :id")
    abstract suspend fun setSubscribed(id: Long, subscribed: Boolean)

    @Query("DELETE FROM podcasts WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Supprime les aperçus du catalogue jamais ajoutés et absents de la file d'attente. */
    @Query(
        """
        DELETE FROM podcasts
        WHERE subscribed = 0
          AND id NOT IN (
            SELECT e.podcastId FROM episodes e INNER JOIN queue q ON q.episodeId = e.id
          )
        """,
    )
    abstract suspend fun deleteUnusedPreviews()
}

@Dao
abstract class EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC, id ASC")
    abstract fun observeForPodcast(podcastId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    abstract suspend fun get(id: Long): EpisodeEntity?

    @Query(
        """
        SELECT e.*, p.title AS podcastTitle, p.imageUrl AS podcastImageUrl
        FROM episodes e INNER JOIN podcasts p ON p.id = e.podcastId
        WHERE e.id = :id
        """,
    )
    abstract suspend fun getWithPodcast(id: Long): EpisodeWithPodcast?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>

    @Query(
        """
        UPDATE episodes
        SET title = :title, description = :description, audioUrl = :audioUrl,
            imageUrl = :imageUrl, publishedAt = :publishedAt, durationSec = :durationSec
        WHERE podcastId = :podcastId AND guid = :guid
        """,
    )
    abstract suspend fun updateMetadata(
        podcastId: Long,
        guid: String,
        title: String,
        description: String,
        audioUrl: String,
        imageUrl: String?,
        publishedAt: Long?,
        durationSec: Long?,
    )

    /** Insère les nouveaux épisodes et met à jour les existants sans toucher à l'écoute ni au téléchargement. */
    @Transaction
    open suspend fun upsertFromFeed(episodes: List<EpisodeEntity>) {
        val rowIds = insertAll(episodes)
        episodes.forEachIndexed { index, episode ->
            if (rowIds[index] == -1L) {
                updateMetadata(
                    podcastId = episode.podcastId,
                    guid = episode.guid,
                    title = episode.title,
                    description = episode.description,
                    audioUrl = episode.audioUrl,
                    imageUrl = episode.imageUrl,
                    publishedAt = episode.publishedAt,
                    durationSec = episode.durationSec,
                )
            }
        }
    }

    @Query("UPDATE episodes SET positionMs = :positionMs, completed = 0 WHERE id = :id")
    abstract suspend fun savePosition(id: Long, positionMs: Long)

    @Query("UPDATE episodes SET positionMs = 0, completed = 1 WHERE id = :id")
    abstract suspend fun markCompleted(id: Long)

    @Query("UPDATE episodes SET downloadState = :state, downloadProgress = :progress WHERE id = :id")
    abstract suspend fun setDownloadState(id: Long, state: DownloadState, progress: Int)

    /** Sans effet si le téléchargement a été annulé entre-temps. */
    @Query("UPDATE episodes SET downloadProgress = :progress WHERE id = :id AND downloadState = 'DOWNLOADING'")
    abstract suspend fun updateDownloadProgress(id: Long, progress: Int)

    /** Remet en attente un téléchargement interrompu par le système (sans effet s'il a été annulé). */
    @Query(
        "UPDATE episodes SET downloadState = 'QUEUED', downloadProgress = 0 WHERE id = :id AND downloadState = 'DOWNLOADING'",
    )
    abstract suspend fun requeueIfDownloading(id: Long)

    @Query(
        "UPDATE episodes SET downloadState = 'DOWNLOADED', downloadProgress = 100, localPath = :path WHERE id = :id",
    )
    abstract suspend fun setDownloaded(id: Long, path: String)

    @Query("UPDATE episodes SET downloadState = 'NONE', downloadProgress = 0, localPath = NULL WHERE id = :id")
    abstract suspend fun clearDownload(id: Long)

    @Query(
        """
        SELECT id FROM episodes
        WHERE podcastId = :podcastId AND downloadState IN ('NONE', 'FAILED')
        ORDER BY publishedAt DESC, id ASC
        """,
    )
    abstract suspend fun idsToDownload(podcastId: Long): List<Long>

    @Query("SELECT id FROM episodes WHERE podcastId = :podcastId AND downloadState != 'NONE'")
    abstract suspend fun idsWithDownload(podcastId: Long): List<Long>
}

@Dao
abstract class QueueDao {
    @Query(
        """
        SELECT e.*, p.title AS podcastTitle, p.imageUrl AS podcastImageUrl
        FROM queue q
        INNER JOIN episodes e ON e.id = q.episodeId
        INNER JOIN podcasts p ON p.id = e.podcastId
        ORDER BY q.position
        """,
    )
    abstract suspend fun load(): List<EpisodeWithPodcast>

    @Query("DELETE FROM queue")
    abstract suspend fun clear()

    @Insert
    abstract suspend fun insertAll(items: List<QueueItemEntity>)

    @Transaction
    open suspend fun replace(episodeIds: List<Long>) {
        clear()
        insertAll(episodeIds.mapIndexed { index, id -> QueueItemEntity(position = index, episodeId = id) })
    }
}
