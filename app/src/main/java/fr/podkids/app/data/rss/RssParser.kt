package fr.podkids.app.data.rss

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String?,
    val publishedAt: Long?,
    val durationSec: Long?,
)

class InvalidFeedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Analyseur de flux RSS 2.0 de podcast (avec les extensions iTunes).
 * Basé sur SAX pour fonctionner à l'identique sur Android et sur la JVM des tests unitaires.
 */
object RssParser {

    fun parse(input: InputStream): ParsedFeed {
        val handler = FeedHandler()
        try {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = true
            // Pas de résolution d'entités externes (protection XXE) ; ignoré si non supporté.
            for (feature in listOf(
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            )) {
                runCatching { factory.setFeature(feature, false) }
            }
            factory.newSAXParser().parse(InputSource(input), handler)
        } catch (e: Exception) {
            throw InvalidFeedException("Flux RSS illisible", e)
        }
        if (!handler.sawChannel) throw InvalidFeedException("Ce document n'est pas un flux RSS")
        return handler.build()
    }

    private class FeedHandler : DefaultHandler() {
        var sawChannel = false
        private var inItem = false
        private var inChannelImage = false
        private val text = StringBuilder()

        private var title = ""
        private var author = ""
        private var description = ""
        private var imageUrl: String? = null
        private val episodes = mutableListOf<ParsedEpisode>()

        private var item = ItemBuilder()

        private class ItemBuilder {
            var guid = ""
            var title = ""
            var description = ""
            var summary = ""
            var audioUrl = ""
            var imageUrl: String? = null
            var pubDate: Long? = null
            var duration: Long? = null
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            text.setLength(0)
            val name = elementName(localName, qName)
            val itunes = isItunes(uri, qName)
            when {
                name == "channel" -> sawChannel = true
                name == "item" -> {
                    inItem = true
                    item = ItemBuilder()
                }
                name == "image" && itunes -> {
                    val href = attributes.getValue("href")?.trim().orEmpty()
                    if (href.isNotEmpty()) {
                        if (inItem) item.imageUrl = href else imageUrl = href
                    }
                }
                name == "image" && !inItem -> inChannelImage = true
                name == "enclosure" && inItem -> {
                    val url = attributes.getValue("url")?.trim().orEmpty()
                    val type = attributes.getValue("type").orEmpty()
                    if (url.isNotEmpty() && item.audioUrl.isEmpty() && isAudio(type, url)) {
                        item.audioUrl = url
                    }
                }
                name == "content" && inItem && isMedia(uri, qName) -> {
                    val url = attributes.getValue("url")?.trim().orEmpty()
                    val type = attributes.getValue("type").orEmpty()
                    if (url.isNotEmpty() && item.audioUrl.isEmpty() && type.startsWith("audio")) {
                        item.audioUrl = url
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            val itunes = isItunes(uri, qName)
            // Élément RSS standard, hors espace de noms (évite de confondre <title> et <media:title>).
            val plain = uri.isNullOrEmpty() && !qName.orEmpty().contains(':')
            val value = text.toString().trim()
            text.setLength(0)
            when {
                name == "item" -> {
                    finishItem()
                    inItem = false
                }
                name == "image" && !itunes && !inItem -> inChannelImage = false
                inItem -> when {
                    name == "title" && plain -> item.title = value
                    name == "title" && itunes -> if (item.title.isEmpty()) item.title = value
                    name == "guid" -> item.guid = value
                    name == "description" -> item.description = value
                    name == "summary" && itunes -> item.summary = value
                    name == "encoded" -> if (item.description.isEmpty()) item.description = value
                    name == "pubdate" -> item.pubDate = parseDate(value)
                    name == "duration" && itunes -> item.duration = parseDuration(value)
                }
                inChannelImage -> if (name == "url" && imageUrl == null && value.isNotEmpty()) imageUrl = value
                else -> when {
                    name == "title" && plain -> title = value
                    name == "description" -> description = value
                    name == "summary" && itunes -> if (description.isEmpty()) description = value
                    name == "author" && itunes -> author = value
                    name == "managingeditor" -> if (author.isEmpty()) author = value
                }
            }
        }

        private fun finishItem() {
            if (item.audioUrl.isEmpty()) return
            episodes += ParsedEpisode(
                guid = item.guid.ifEmpty { item.audioUrl },
                title = stripHtml(item.title).ifEmpty { "Épisode" },
                description = stripHtml(item.description.ifEmpty { item.summary }),
                audioUrl = item.audioUrl,
                imageUrl = item.imageUrl,
                publishedAt = item.pubDate,
                durationSec = item.duration,
            )
        }

        fun build(): ParsedFeed = ParsedFeed(
            title = stripHtml(title),
            author = stripHtml(author),
            description = stripHtml(description),
            imageUrl = imageUrl,
            // Un même guid ne doit apparaître qu'une fois (contrainte d'unicité en base).
            episodes = episodes.distinctBy { it.guid },
        )
    }

    private fun elementName(localName: String?, qName: String?): String {
        val raw = if (!localName.isNullOrEmpty()) localName else qName.orEmpty().substringAfter(':')
        return raw.lowercase(Locale.ROOT)
    }

    private fun isItunes(uri: String?, qName: String?): Boolean =
        uri.orEmpty().contains("itunes", ignoreCase = true) ||
            qName.orEmpty().startsWith("itunes:", ignoreCase = true)

    private fun isMedia(uri: String?, qName: String?): Boolean =
        uri.orEmpty().contains("mrss", ignoreCase = true) ||
            qName.orEmpty().startsWith("media:", ignoreCase = true)

    private val audioExtensions = listOf(".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".wav", ".flac")

    private fun isAudio(type: String, url: String): Boolean {
        if (type.startsWith("audio", ignoreCase = true)) return true
        if (type.startsWith("video", ignoreCase = true) || type.startsWith("image", ignoreCase = true)) return false
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return type.isBlank() || audioExtensions.any { path.endsWith(it) }
    }

    private val datePatterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm Z",
        "dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
    )

    fun parseDate(value: String): Long? {
        val text = value.trim()
        if (text.isEmpty()) return null
        for (pattern in datePatterns) {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.isLenient = true
            val position = ParsePosition(0)
            val date = runCatching { format.parse(text, position) }.getOrNull()
            if (date != null && position.index > 0) return date.time
        }
        return null
    }

    /** Accepte « 3723 », « 62:03 », « 01:02:03 » ou « 12:34.5 ». */
    fun parseDuration(value: String): Long? {
        val text = value.trim()
        if (text.isEmpty()) return null
        val parts = text.split(':')
        if (parts.size > 3) return null
        var total = 0L
        for (part in parts) {
            val number = part.trim().toDoubleOrNull() ?: return null
            if (number < 0) return null
            total = total * 60 + number.toLong()
        }
        return total.takeIf { it > 0 }
    }

    private val tagRegex = Regex("<[^>]+>")
    private val breakRegex = Regex("(?i)<\\s*(br|/p|/div|/li)\\s*/?\\s*>")
    private val numericEntityRegex = Regex("&#(x?[0-9a-fA-F]+);")
    private val blankLinesRegex = Regex("\\n\\s*\\n\\s*")
    private val spacesRegex = Regex("[ \\t\\u00A0]+")

    /** Retire le balisage HTML des descriptions pour un affichage en texte brut. */
    fun stripHtml(html: String): String {
        if (html.isEmpty()) return html
        var text = breakRegex.replace(html, "\n")
        text = tagRegex.replace(text, "")
        text = text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&rsquo;", "’")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&hellip;", "…")
        text = numericEntityRegex.replace(text) { match ->
            val code = match.groupValues[1]
            val point = if (code.startsWith("x", ignoreCase = true)) {
                code.substring(1).toIntOrNull(16)
            } else {
                code.toIntOrNull()
            }
            if (point != null && Character.isValidCodePoint(point)) String(Character.toChars(point)) else ""
        }
        text = text.replace("&amp;", "&")
        text = spacesRegex.replace(text, " ")
        text = blankLinesRegex.replace(text, "\n\n")
        return text.trim()
    }
}
