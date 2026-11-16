package fr.podkids.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadState { NONE, QUEUED, DOWNLOADING, DOWNLOADED, FAILED }

@Entity(
    tableName = "podcasts",
    indices = [Index(value = ["feedUrl"], unique = true)],
)
data class PodcastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    /** false = simple aperçu ouvert depuis le catalogue, true = présent dans « Mes podcasts ». */
    val subscribed: Boolean,
    val addedAt: Long,
    val lastRefreshedAt: Long,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["podcastId", "guid"], unique = true),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String?,
    val publishedAt: Long?,
    val durationSec: Long?,
    val positionMs: Long = 0,
    val completed: Boolean = false,
    val downloadState: DownloadState = DownloadState.NONE,
    /** 0..100, ou -1 quand la taille du fichier est inconnue. */
    val downloadProgress: Int = 0,
    val localPath: String? = null,
)

/**
 * Liste de lecture persistée : la position 0 est l'épisode en cours, les positions négatives les
 * épisodes précédents (historique), les positions positives la file d'attente.
 */
@Entity(tableName = "queue")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val episodeId: Long,
)

data class EpisodeWithPodcast(
    @Embedded val episode: EpisodeEntity,
    @ColumnInfo(name = "podcastTitle") val podcastTitle: String,
    @ColumnInfo(name = "podcastImageUrl") val podcastImageUrl: String?,
) {
    val artworkUrl: String? get() = episode.imageUrl ?: podcastImageUrl

    /** Position de reprise : on repart du début si l'épisode a déjà été écouté en entier. */
    val resumePositionMs: Long get() = if (episode.completed) 0L else episode.positionMs
}
