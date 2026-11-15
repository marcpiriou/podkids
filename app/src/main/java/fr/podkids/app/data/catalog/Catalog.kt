package fr.podkids.app.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

data class CatalogPodcast(
    val id: Long,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String,
    /** Âge minimal annoncé par l'éditeur du podcast. */
    val minAge: Int,
)

/**
 * Catalogue de podcasts pour enfants, sélectionné à la main et embarqué dans l'application
 * (`assets/catalog.json`). Chaque podcast porte l'âge minimal annoncé par son éditeur.
 */
class CatalogRepository(private val context: Context) {
    private val mutex = Mutex()
    private var cached: List<CatalogPodcast>? = null

    suspend fun podcasts(): List<CatalogPodcast> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            Catalog.parse(json)
        }.also { cached = it }
    }

    private companion object {
        const val ASSET_NAME = "catalog.json"
    }
}

object Catalog {
    /** Choix proposés par le sélecteur d'âge (« à partir de … ans »). */
    val AGE_CHOICES = (3..12).toList()

    /**
     * Choix du sélecteur auquel un podcast est rattaché : son âge minimal, ramené aux bornes du
     * sélecteur (un podcast « dès 2 ans » apparaît sous « 3 ans »).
     */
    fun ageChoiceOf(minAge: Int): Int = minAge.coerceIn(AGE_CHOICES.first(), AGE_CHOICES.last())

    fun parse(json: String): List<CatalogPodcast> {
        val items = JSONObject(json).optJSONArray("podcasts") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val feedUrl = item.optString("feedUrl").trim()
            val title = item.optString("title").trim()
            val minAge = item.optInt("minAge", -1)
            if (feedUrl.isEmpty() || title.isEmpty() || minAge < 0) return@mapNotNull null
            CatalogPodcast(
                id = item.optLong("id", index.toLong()),
                title = title,
                author = item.optString("author").trim(),
                artworkUrl = item.optString("artworkUrl").trim().ifEmpty { null },
                feedUrl = feedUrl,
                minAge = minAge,
            )
        }.distinctBy { it.feedUrl }
    }

    /**
     * Filtre le catalogue en conservant l'ordre du fichier (du plus jeune âge au plus élevé).
     * @param ages choix d'âge cochés ; vide = tous les âges.
     */
    fun filter(podcasts: List<CatalogPodcast>, query: String, ages: Set<Int>): List<CatalogPodcast> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        return podcasts.filter { podcast ->
            (ages.isEmpty() || ageChoiceOf(podcast.minAge) in ages) &&
                (words.isEmpty() || normalize(podcast.title + " " + podcast.author).let { text -> words.all { it in text } })
        }
    }

    private val diacritics = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{Nd}]+")

    /** Minuscules, sans accents ni ponctuation : « Léo, la licorne ! » devient « leo la licorne ». */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return separators.replace(diacritics.replace(decomposed, "").lowercase(Locale.ROOT), " ").trim()
    }
}
