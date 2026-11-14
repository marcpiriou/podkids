package fr.podkids.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedUrlTest {

    @Test
    fun `ajoute le schema manquant`() {
        assertEquals("https://example.org/feed.xml", PodcastRepository.normalizeFeedUrl("  example.org/feed.xml "))
    }

    @Test
    fun `convertit les liens feed et podcast`() {
        assertEquals("https://example.org/feed.xml", PodcastRepository.normalizeFeedUrl("feed://example.org/feed.xml"))
        assertEquals("https://example.org/feed.xml", PodcastRepository.normalizeFeedUrl("feed:https://example.org/feed.xml"))
        assertEquals("https://example.org/feed", PodcastRepository.normalizeFeedUrl("podcast://example.org/feed"))
    }

    @Test
    fun `conserve les adresses http`() {
        assertEquals("http://example.org/rss", PodcastRepository.normalizeFeedUrl("http://example.org/rss"))
    }

    @Test
    fun `rejette les saisies invalides`() {
        assertNull(PodcastRepository.normalizeFeedUrl(""))
        assertNull(PodcastRepository.normalizeFeedUrl("pas une adresse"))
        assertNull(PodcastRepository.normalizeFeedUrl("localhost"))
    }
}
