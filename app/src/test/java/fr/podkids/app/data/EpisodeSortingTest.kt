package fr.podkids.app.data

import fr.podkids.app.data.db.EpisodeEntity
import fr.podkids.app.data.settings.EpisodeOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeSortingTest {

    private fun episode(id: Long, publishedAt: Long?) = EpisodeEntity(
        id = id,
        podcastId = 1,
        guid = "ep-$id",
        title = "Épisode $id",
        description = "",
        audioUrl = "https://example.org/$id.mp3",
        imageUrl = null,
        publishedAt = publishedAt,
        durationSec = null,
    )

    /** Ordre renvoyé par la base : du plus récent au plus ancien, épisodes sans date à la fin. */
    private val fromDatabase = listOf(
        episode(3, 3_000),
        episode(2, 2_000),
        episode(4, 2_000),
        episode(1, 1_000),
        episode(5, null),
    )

    @Test
    fun `ordre chronologique du plus ancien au plus recent`() {
        assertEquals(listOf(1L, 2L, 4L, 3L, 5L), fromDatabase.sortedByOrder(EpisodeOrder.OLDEST_FIRST).map { it.id })
    }

    @Test
    fun `ordre inverse du plus recent au plus ancien`() {
        assertEquals(listOf(3L, 2L, 4L, 1L, 5L), fromDatabase.sortedByOrder(EpisodeOrder.NEWEST_FIRST).map { it.id })
    }
}
