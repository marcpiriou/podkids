package fr.podkids.app.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Rubriques du catalogue, dans leur ordre d'affichage. */
enum class CatalogCategory { BEDTIME, TALES, STORIES, DISCOVERY, MUSIC }

data class CatalogPodcast(
    val id: Long,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String,
    /** Âge minimal annoncé par l'éditeur du podcast. */
    val minAge: Int,
    val category: CatalogCategory,
)

data class CatalogSection(val category: CatalogCategory, val podcasts: List<CatalogPodcast>)

/**
 * Catalogue de podcasts pour enfants, sélectionné à la main et embarqué dans l'application
 * (`assets/catalog.json`). Chaque podcast porte l'âge minimal annoncé par son éditeur et une rubrique.
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
    val AGE_CHOICES = listOf(3, 4, 5, 6, 7)

    /**
     * Choix du sélecteur auquel un podcast est rattaché : son âge minimal, ramené aux bornes du
     * sélecteur. Un podcast « dès 2 ans » apparaît sous « 3 ans » ; un podcast « dès 9 ans » sous
     * « 7 ans », le dernier choix valant pour 7 ans et plus.
     */
    fun ageChoiceOf(minAge: Int): Int = minAge.coerceIn(AGE_CHOICES.first(), AGE_CHOICES.last())

    fun parse(json: String): List<CatalogPodcast> {
        val items = JSONObject(json).optJSONArray("podcasts") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val feedUrl = item.optString("feedUrl").trim()
            val title = item.optString("title").trim()
            val category = CatalogCategory.entries.firstOrNull { it.name == item.optString("category") }
            val minAge = item.optInt("minAge", -1)
            if (feedUrl.isEmpty() || title.isEmpty() || category == null || minAge < 0) return@mapNotNull null
            CatalogPodcast(
                id = item.optLong("id", index.toLong()),
                title = title,
                author = item.optString("author").trim(),
                artworkUrl = item.optString("artworkUrl").trim().ifEmpty { null },
                feedUrl = feedUrl,
                minAge = minAge,
                category = category,
            )
        }.distinctBy { it.feedUrl }
    }

    /**
     * Filtre le catalogue puis le regroupe par rubrique, en conservant l'ordre du fichier.
     * @param ages choix d'âge cochés ; vide = tous les âges.
     * @param category rubrique retenue ; null = toutes.
     */
    fun filter(
        podcasts: List<CatalogPodcast>,
        query: String,
        ages: Set<Int>,
        category: CatalogCategory?,
    ): List<CatalogSection> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        val matching = podcasts.filter { podcast ->
            (category == null || podcast.category == category) &&
                (ages.isEmpty() || ageChoiceOf(podcast.minAge) in ages) &&
                (words.isEmpty() || normalize(podcast.title + " " + podcast.author).let { text -> words.all { it in text } })
        }
        return CatalogCategory.entries.mapNotNull { section ->
            matching.filter { it.category == section }
                .takeIf { it.isNotEmpty() }
                ?.let { CatalogSection(section, it) }
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
