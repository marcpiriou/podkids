package fr.podkids.app.data

import fr.podkids.app.data.db.EpisodeEntity
import fr.podkids.app.data.db.EpisodeWithPodcast
import org.junit.Assert.assertEquals
import org.junit.Test

class StartPositionTest {

    private fun entry(positionMs: Long, completed: Boolean = false, introSec: Int = 0) = EpisodeWithPodcast(
        episode = EpisodeEntity(
            id = 1,
            podcastId = 1,
            guid = "ep",
            title = "Épisode",
            description = "",
            audioUrl = "https://example.org/ep.mp3",
            imageUrl = null,
            publishedAt = null,
            durationSec = null,
            positionMs = positionMs,
            completed = completed,
        ),
        podcastTitle = "Podcast",
        podcastImageUrl = null,
        podcastSkipIntroSec = introSec,
    )

    @Test
    fun `sans reglage la lecture reprend ou elle s'etait arretee`() {
        assertEquals(0L, entry(0).startPositionMs)
        assertEquals(42_000L, entry(42_000).startPositionMs)
    }

    @Test
    fun `le debut a sauter s'applique a un episode commence depuis le debut`() {
        assertEquals(30_000L, entry(0, introSec = 30).startPositionMs)
        assertEquals(30_000L, entry(10_000, introSec = 30).startPositionMs)
    }

    @Test
    fun `une reprise au dela du debut a sauter est conservee`() {
        assertEquals(90_000L, entry(90_000, introSec = 30).startPositionMs)
    }

    @Test
    fun `un episode deja lu repart apres le debut a sauter`() {
        assertEquals(30_000L, entry(500_000, completed = true, introSec = 30).startPositionMs)
    }
}
