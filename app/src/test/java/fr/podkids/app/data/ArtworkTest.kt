package fr.podkids.app.data

import fr.podkids.app.data.db.EpisodeEntity
import fr.podkids.app.data.db.EpisodeWithPodcast
import fr.podkids.app.data.db.localImageUri
import fr.podkids.app.data.download.ArtworkStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ArtworkTest {

    @Test
    fun `reduction par puissances de 2 sans descendre sous la taille cible`() {
        assertEquals(1, ArtworkStore.sampleSize(600, 600, 800))
        assertEquals(1, ArtworkStore.sampleSize(1400, 1400, 800))
        assertEquals(2, ArtworkStore.sampleSize(1600, 1600, 800))
        assertEquals(4, ArtworkStore.sampleSize(3000, 3000, 800))
    }

    @Test
    fun `une copie locale absente n'est pas utilisee`() {
        assertNull(localImageUri(null))
        assertNull(localImageUri("/chemin/qui/n/existe/pas.jpg"))
    }

    @Test
    fun `les copies locales passent avant les adresses en ligne`() {
        val podcastFile = File.createTempFile("podcast", ".jpg").apply { deleteOnExit() }
        val episodeFile = File.createTempFile("episode", ".jpg").apply { deleteOnExit() }

        fun entry(episodeImage: String?, episodeLocal: String?, podcastLocal: String?) = EpisodeWithPodcast(
            episode = EpisodeEntity(
                id = 1, podcastId = 1, guid = "ep", title = "Épisode", description = "",
                audioUrl = "https://example.org/ep.mp3", imageUrl = episodeImage, publishedAt = null,
                durationSec = null, localImagePath = episodeLocal,
            ),
            podcastTitle = "Podcast",
            podcastImageUrl = "https://example.org/podcast.jpg",
            podcastLocalImagePath = podcastLocal,
        )

        assertEquals("file://" + episodeFile.absolutePath, entry("https://example.org/ep.jpg", episodeFile.path, null).artworkUrl)
        assertEquals("https://example.org/ep.jpg", entry("https://example.org/ep.jpg", null, podcastFile.path).artworkUrl)
        assertEquals("file://" + podcastFile.absolutePath, entry(null, null, podcastFile.path).artworkUrl)
        assertEquals("https://example.org/podcast.jpg", entry(null, null, null).artworkUrl)
    }
}
