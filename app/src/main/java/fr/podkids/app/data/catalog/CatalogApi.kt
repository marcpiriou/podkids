package fr.podkids.app.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

data class CatalogPodcast(
    val id: Long,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String,
)

/** Rubriques du catalogue, adossées aux genres « Enfants et parents » de l'annuaire Apple Podcasts. */
enum class CatalogCategory(val genreId: Int) {
    TOP(1305),
    STORIES(1520),
    LEARNING(1519),
    ANIMALS(1522),
}

/**
 * Catalogue en ligne s'appuyant sur l'annuaire public Apple Podcasts (iTunes Search API, sans clé).
 * Seuls les podcasts classés « Enfants et parents », non explicites et hors rubrique « Parentalité »
 * sont conservés.
 */
class CatalogApi(private val client: OkHttpClient) {

    suspend fun search(term: String, country: String = defaultCountry()): List<CatalogPodcast> =
        withContext(Dispatchers.IO) {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("media", "podcast")
                .addQueryParameter("term", term)
                .addQueryParameter("country", country)
                .addQueryParameter("explicit", "No")
                .addQueryParameter("limit", "100")
                .build()
            parseResults(get(url.toString()))
        }

    suspend fun top(category: CatalogCategory, country: String = defaultCountry()): List<CatalogPodcast> =
        withContext(Dispatchers.IO) {
            val chartUrl = "https://itunes.apple.com/${country.lowercase(Locale.ROOT)}/rss/toppodcasts/" +
                "genre=${category.genreId}/limit=$CHART_SIZE/json"
            val ids = parseChartIds(get(chartUrl))
            if (ids.isEmpty()) return@withContext emptyList()
            val lookupUrl = "https://itunes.apple.com/lookup".toHttpUrl().newBuilder()
                .addQueryParameter("id", ids.joinToString(","))
                .addQueryParameter("country", country)
                .build()
            val byId = parseResults(get(lookupUrl.toString())).associateBy { it.id }
            // On conserve l'ordre du classement.
            ids.mapNotNull { byId[it] }
        }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body.string()
        }
    }

    companion object {
        const val USER_AGENT = "Podkids/1.0 (Android)"
        private const val CHART_SIZE = 50
        private const val GENRE_KIDS_AND_FAMILY = "1305"
        private const val GENRE_PARENTING = "1521"

        fun defaultCountry(): String {
            val country = Locale.getDefault().country
            return if (country.length == 2 && country.all { it.isLetter() }) country.uppercase(Locale.ROOT) else "FR"
        }

        fun parseChartIds(json: String): List<Long> {
            val feed = JSONObject(json).optJSONObject("feed") ?: return emptyList()
            // L'API renvoie un objet (et non un tableau) lorsqu'il n'y a qu'une seule entrée.
            val entries = feed.optJSONArray("entry")
                ?: return listOfNotNull(feed.optJSONObject("entry")?.let(::chartEntryId))
            return (0 until entries.length()).mapNotNull { index ->
                entries.optJSONObject(index)?.let(::chartEntryId)
            }
        }

        private fun chartEntryId(entry: JSONObject): Long? =
            entry.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id")?.toLongOrNull()

        fun parseResults(json: String): List<CatalogPodcast> {
            val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
            return (0 until results.length())
                .mapNotNull { index -> results.optJSONObject(index)?.let(::toCatalogPodcast) }
                .distinctBy { it.feedUrl }
        }

        private fun toCatalogPodcast(item: JSONObject): CatalogPodcast? {
            val feedUrl = item.optString("feedUrl").trim()
            if (feedUrl.isEmpty() || !feedUrl.startsWith("http", ignoreCase = true)) return null
            if (!isKidFriendly(item)) return null
            val id = item.optLong("collectionId", 0L)
            if (id == 0L) return null
            val artwork = listOf("artworkUrl600", "artworkUrl100", "artworkUrl60")
                .map { item.optString(it) }
                .firstOrNull { it.isNotBlank() }
            return CatalogPodcast(
                id = id,
                title = item.optString("collectionName").ifBlank { item.optString("trackName") },
                author = item.optString("artistName"),
                artworkUrl = artwork,
                feedUrl = feedUrl,
            )
        }

        private fun isKidFriendly(item: JSONObject): Boolean {
            if (item.optString("collectionExplicitness").equals("explicit", ignoreCase = true)) return false
            if (item.optString("trackExplicitness").equals("explicit", ignoreCase = true)) return false
            if (item.optString("contentAdvisoryRating").equals("Explicit", ignoreCase = true)) return false
            val genres = item.optJSONArray("genreIds") ?: return false
            val ids = (0 until genres.length()).map { genres.optString(it) }
            return GENRE_KIDS_AND_FAMILY in ids && GENRE_PARENTING !in ids
        }
    }
}
