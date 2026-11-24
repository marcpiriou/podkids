package fr.podkids.app.data

import fr.podkids.app.playback.QueueDuration
import fr.podkids.app.playback.QueueEntry
import fr.podkids.app.playback.totalDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDurationTest {

    private fun entry(id: Long, remainingMs: Long?) =
        QueueEntry(episodeId = id, title = "Épisode $id", podcastTitle = "Podcast", artworkUrl = null, remainingMs = remainingMs)

    @Test
    fun `file vide`() {
        assertEquals(QueueDuration(0, complete = true), emptyList<QueueEntry>().totalDuration())
    }

    @Test
    fun `somme des temps restants`() {
        assertEquals(QueueDuration(900_000, complete = true), listOf(entry(1, 600_000), entry(2, 300_000)).totalDuration())
    }

    @Test
    fun `une duree inconnue rend le total incomplet`() {
        assertEquals(QueueDuration(600_000, complete = false), listOf(entry(1, 600_000), entry(2, null)).totalDuration())
    }
}
