package fr.podkids.app.data

import fr.podkids.app.data.catalog.Catalog
import fr.podkids.app.data.catalog.CatalogCategory
import fr.podkids.app.data.catalog.CatalogPodcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {

    private fun podcast(id: Long, title: String, minAge: Int, category: CatalogCategory, author: String = "Auteur") =
        CatalogPodcast(
            id = id,
            title = title,
            author = author,
            artworkUrl = null,
            feedUrl = "https://example.org/$id.xml",
            minAge = minAge,
            category = category,
        )

    private val sample = listOf(
        podcast(1, "Les Petits Dormeurs", 1, CatalogCategory.BEDTIME),
        podcast(2, "Léo la licorne", 3, CatalogCategory.BEDTIME),
        podcast(3, "Contes du grimoire", 4, CatalogCategory.TALES),
        podcast(4, "Mission pourquoi ?", 5, CatalogCategory.DISCOVERY, author = "Studio Lune"),
        podcast(5, "Curieux de sciences", 7, CatalogCategory.DISCOVERY),
    )

    private fun ids(query: String = "", ages: Set<Int> = emptySet(), category: CatalogCategory? = null) =
        Catalog.filter(sample, query, ages, category).flatMap { it.podcasts }.map { it.id }

    @Test
    fun `sans critere tout le catalogue est regroupe par rubrique`() {
        val sections = Catalog.filter(sample, "", emptySet(), null)
        assertEquals(
            listOf(CatalogCategory.BEDTIME, CatalogCategory.TALES, CatalogCategory.DISCOVERY),
            sections.map { it.category },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sections.flatMap { it.podcasts }.map { it.id })
    }

    @Test
    fun `le selecteur d'age accepte plusieurs choix`() {
        assertEquals(listOf(3L), ids(ages = setOf(4)))
        assertEquals(listOf(3L, 4L), ids(ages = setOf(4, 5)))
        assertEquals(listOf(5L), ids(ages = setOf(7)))
        assertTrue(ids(ages = setOf(6)).isEmpty())
    }

    @Test
    fun `les podcasts pour les plus petits sont rattaches au choix 3 ans`() {
        assertEquals(listOf(1L, 2L), ids(ages = setOf(3)))
        assertEquals(3, Catalog.ageChoiceOf(1))
        assertEquals(5, Catalog.ageChoiceOf(5))
        assertEquals(7, Catalog.ageChoiceOf(7))
    }

    @Test
    fun `le choix 7 ans regroupe les podcasts pour 7 ans et plus`() {
        val older = sample + podcast(6, "Les aventures de Tina", 9, CatalogCategory.STORIES)
        val ids = Catalog.filter(older, "", setOf(7), null).flatMap { it.podcasts }.map { it.id }
        assertEquals(listOf(6L, 5L), ids)
        assertEquals(7, Catalog.ageChoiceOf(9))
    }

    @Test
    fun `la rubrique et l'age se combinent`() {
        assertEquals(listOf(4L, 5L), ids(category = CatalogCategory.DISCOVERY))
        assertEquals(listOf(4L), ids(ages = setOf(5), category = CatalogCategory.DISCOVERY))
    }

    @Test
    fun `la recherche ignore la casse les accents et la ponctuation`() {
        assertEquals(listOf(2L), ids(query = "LEO licorne"))
        assertEquals(listOf(4L), ids(query = "pourquoi"))
        assertEquals(listOf(4L), ids(query = "studio lune"))
        assertTrue(ids(query = "dinosaure").isEmpty())
    }

    @Test
    fun `ignore les entrees incompletes ou en double`() {
        val json = """
            {"podcasts": [
              {"id": 1, "title": "Valide", "author": "A", "feedUrl": "https://example.org/a.xml", "minAge": 3, "category": "TALES"},
              {"id": 2, "title": "Doublon", "author": "A", "feedUrl": "https://example.org/a.xml", "minAge": 3, "category": "TALES"},
              {"id": 3, "title": "Sans flux", "minAge": 3, "category": "TALES"},
              {"id": 4, "title": "Rubrique inconnue", "feedUrl": "https://example.org/b.xml", "minAge": 3, "category": "AUTRE"},
              {"id": 5, "title": "Sans âge", "feedUrl": "https://example.org/c.xml", "category": "TALES"}
            ]}
        """.trimIndent()
        assertEquals(listOf(1L), Catalog.parse(json).map { it.id })
    }

    /** Contrôle le fichier réellement embarqué dans l'application. */
    @Test
    fun `le catalogue embarque est coherent`() {
        val file = listOf("src/main/assets/catalog.json", "app/src/main/assets/catalog.json")
            .map(::File)
            .first { it.exists() }
        val json = file.readText()
        val podcasts = Catalog.parse(json)

        val declared = Regex("\"feedUrl\"").findAll(json).count()
        assertEquals("Toutes les entrées doivent être valides et sans doublon de flux", declared, podcasts.size)
        assertTrue("Le catalogue ne doit pas être vide", podcasts.size >= 40)
        assertEquals("Identifiants uniques", podcasts.size, podcasts.map { it.id }.toSet().size)

        podcasts.forEach { podcast ->
            assertTrue("Flux en https : ${podcast.title}", podcast.feedUrl.startsWith("https://"))
            assertTrue("Pochette en https : ${podcast.title}", podcast.artworkUrl?.startsWith("https://") == true)
            assertTrue("Âge entre 1 et 9 ans : ${podcast.title}", podcast.minAge in 1..9)
            assertTrue("Auteur renseigné : ${podcast.title}", podcast.author.isNotBlank())
        }
        // Chaque choix d'âge et chaque rubrique proposent au moins un podcast.
        Catalog.AGE_CHOICES.forEach { age ->
            assertTrue("Aucun podcast pour $age ans", podcasts.any { Catalog.ageChoiceOf(it.minAge) == age })
        }
        CatalogCategory.entries.forEach { category ->
            assertTrue("Rubrique vide : $category", podcasts.any { it.category == category })
        }
    }
}
