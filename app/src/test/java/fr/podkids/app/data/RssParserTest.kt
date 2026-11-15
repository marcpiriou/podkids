package fr.podkids.app.data

import fr.podkids.app.data.rss.InvalidFeedException
import fr.podkids.app.data.rss.RssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {

    private fun parse(xml: String) = RssParser.parse(xml.trimIndent().byteInputStream())

    private val sampleFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"
             xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
             xmlns:content="http://purl.org/rss/1.0/modules/content/"
             xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <title>Les histoires du soir</title>
            <description><![CDATA[<p>Des histoires <b>douces</b> &amp; drôles.</p>]]></description>
            <itunes:author>Studio Lune</itunes:author>
            <itunes:image href="https://example.org/cover.jpg"/>
            <image>
              <url>https://example.org/fallback.png</url>
              <title>Titre de l'image à ignorer</title>
            </image>
            <item>
              <title>Le renard et la lune</title>
              <media:title>Titre média à ignorer</media:title>
              <guid isPermaLink="false">ep-2</guid>
              <pubDate>Tue, 03 Mar 2026 06:00:00 +0100</pubDate>
              <itunes:duration>00:12:30</itunes:duration>
              <itunes:image href="https://example.org/ep2.jpg"/>
              <description>Une histoire de renard.</description>
              <enclosure url="https://example.org/audio/ep2.mp3?source=rss" length="123" type="audio/mpeg"/>
            </item>
            <item>
              <title>Le hérisson pressé</title>
              <guid>ep-1</guid>
              <pubDate>Mon, 02 Mar 2026 06:00:00 GMT</pubDate>
              <itunes:duration>754</itunes:duration>
              <content:encoded><![CDATA[<p>Première ligne</p><p>Seconde ligne</p>]]></content:encoded>
              <enclosure url="https://example.org/audio/ep1.m4a" type="audio/x-m4a"/>
            </item>
            <item>
              <title>Article sans audio</title>
              <guid>post-1</guid>
              <enclosure url="https://example.org/image.jpg" type="image/jpeg"/>
            </item>
          </channel>
        </rss>
    """

    @Test
    fun `lit les informations du podcast`() {
        val feed = parse(sampleFeed)
        assertEquals("Les histoires du soir", feed.title)
        assertEquals("Studio Lune", feed.author)
        assertEquals("Des histoires douces & drôles.", feed.description)
        assertEquals("https://example.org/cover.jpg", feed.imageUrl)
    }

    @Test
    fun `ne garde que les episodes audio`() {
        val feed = parse(sampleFeed)
        assertEquals(listOf("ep-2", "ep-1"), feed.episodes.map { it.guid })
    }

    @Test
    fun `lit le detail d'un episode`() {
        val episode = parse(sampleFeed).episodes.first()
        assertEquals("Le renard et la lune", episode.title)
        assertEquals("https://example.org/audio/ep2.mp3?source=rss", episode.audioUrl)
        assertEquals("https://example.org/ep2.jpg", episode.imageUrl)
        assertEquals(750L, episode.durationSec)
        assertEquals("Une histoire de renard.", episode.description)
        // 3 mars 2026 06:00 (+01:00) = 05:00 UTC
        assertEquals(1772514000000L, episode.publishedAt)
    }

    @Test
    fun `utilise content encoded quand la description est absente`() {
        val episode = parse(sampleFeed).episodes[1]
        assertEquals("Première ligne\nSeconde ligne", episode.description)
        assertEquals(754L, episode.durationSec)
        assertNotNull(episode.publishedAt)
        assertNull(episode.imageUrl)
    }

    @Test
    fun `utilise l'image du canal a defaut d'image itunes`() {
        val feed = parse(
            """
            <rss version="2.0"><channel>
              <title>Sans iTunes</title>
              <image><url>https://example.org/logo.png</url></image>
              <item><title>Un</title><enclosure url="https://example.org/1.mp3" type="audio/mpeg"/></item>
            </channel></rss>
            """,
        )
        assertEquals("https://example.org/logo.png", feed.imageUrl)
        // Sans guid, l'adresse du fichier audio sert d'identifiant.
        assertEquals("https://example.org/1.mp3", feed.episodes.single().guid)
    }

    /** Extrait (structure réelle, textes remplacés) du flux officiel « Les aventures de Tina » de France Inter. */
    @Test
    fun `lit un flux Radio France`() {
        val feed = javaClass.getResourceAsStream("/radiofrance_feed.xml")!!.use { RssParser.parse(it) }
        assertEquals("Les aventures de Tina", feed.title)
        assertTrue(feed.imageUrl!!.startsWith("https://www.radiofrance.fr/"))
        assertEquals(2, feed.episodes.size)
        val episode = feed.episodes.first()
        assertEquals("6d9ebabc-559f-484b-aa6c-c1de5ca0ebb5", episode.guid)
        assertTrue(episode.title.startsWith("Tina et les boucliers de Mars"))
        assertTrue(episode.audioUrl.startsWith("https://proxycast.radiofrance.fr/"))
        assertEquals(15 * 60 + 35L, episode.durationSec)
        assertNotNull(episode.publishedAt)
    }

    @Test
    fun `rejette un document qui n'est pas un flux RSS`() {
        assertThrows(InvalidFeedException::class.java) { parse("<html><body>Bonjour</body></html>") }
        assertThrows(InvalidFeedException::class.java) { parse("ceci n'est pas du XML") }
    }

    @Test
    fun `analyse les durees`() {
        assertEquals(3723L, RssParser.parseDuration("01:02:03"))
        assertEquals(3723L, RssParser.parseDuration("3723"))
        assertEquals(754L, RssParser.parseDuration("12:34.5"))
        assertNull(RssParser.parseDuration(""))
        assertNull(RssParser.parseDuration("bientôt"))
    }

    @Test
    fun `retire le balisage HTML`() {
        assertEquals("Bonjour à tous", RssParser.stripHtml("<p>Bonjour&nbsp;à <a href=\"#\">tous</a></p>"))
        assertEquals("L'été & l'hiver", RssParser.stripHtml("L&#39;été &amp; l&#x27;hiver"))
    }
}
