package fr.podkids.app.data

import fr.podkids.app.data.catalog.Catalog
import fr.podkids.app.data.catalog.CatalogPodcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {

    private fun podcast(id: Long, title: String, minAge: Int, author: String = "Auteur") =
        CatalogPodcast(
            id = id,
            title = title,
            author = author,
            artworkUrl = null,
            feedUrl = "https://example.org/$id.xml",
            minAge = minAge,
        )

    private val sample = listOf(
        podcast(1, "Les Petits Dormeurs", 1),
        podcast(2, "Léo la licorne", 3),
        podcast(3, "Contes du grimoire", 4),
        podcast(4, "Mission pourquoi ?", 5, author = "Studio Lune"),
        podcast(5, "Curieux de sciences", 7),
        podcast(6, "Les aventures de Tina", 9),
        podcast(7, "La bibliothèque des ados", 12),
    )

    private fun ids(query: String = "", ages: Set<Int> = emptySet()) =
        Catalog.filter(sample, query, ages).map { it.id }

    @Test
    fun `le selecteur propose les ages de 3 a 12 ans`() {
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12), Catalog.AGE_CHOICES)
    }

    @Test
    fun `sans critere tout le catalogue est affiche dans l'ordre du fichier`() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), ids())
    }

    @Test
    fun `le selecteur d'age accepte plusieurs choix`() {
        assertEquals(listOf(3L), ids(ages = setOf(4)))
        assertEquals(listOf(3L, 4L), ids(ages = setOf(4, 5)))
        assertEquals(listOf(6L), ids(ages = setOf(9)))
        assertEquals(listOf(5L, 7L), ids(ages = setOf(7, 12)))
        assertTrue(ids(ages = setOf(6)).isEmpty())
    }

    @Test
    fun `les podcasts pour les plus petits sont rattaches au choix 3 ans`() {
        assertEquals(listOf(1L, 2L), ids(ages = setOf(3)))
        assertEquals(3, Catalog.ageChoiceOf(1))
        assertEquals(5, Catalog.ageChoiceOf(5))
        assertEquals(9, Catalog.ageChoiceOf(9))
        assertEquals(12, Catalog.ageChoiceOf(12))
    }

    @Test
    fun `la recherche et l'age se combinent`() {
        assertEquals(listOf(4L), ids(query = "pourquoi", ages = setOf(5)))
        assertTrue(ids(query = "pourquoi", ages = setOf(7)).isEmpty())
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
              {"id": 1, "title": "Valide", "author": "A", "feedUrl": "https://example.org/a.xml", "minAge": 3},
              {"id": 2, "title": "Doublon", "author": "A", "feedUrl": "https://example.org/a.xml", "minAge": 3},
              {"id": 3, "title": "Sans flux", "minAge": 3},
              {"id": 4, "title": "Sans âge", "feedUrl": "https://example.org/c.xml"}
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
        assertEquals("Podcasts triés par âge croissant", podcasts.map { it.minAge }.sorted(), podcasts.map { it.minAge })

        podcasts.forEach { podcast ->
            assertTrue("Flux en https : ${podcast.title}", podcast.feedUrl.startsWith("https://"))
            assertTrue("Pochette en https : ${podcast.title}", podcast.artworkUrl?.startsWith("https://") == true)
            assertTrue("Âge entre 1 et 12 ans : ${podcast.title}", podcast.minAge in 1..12)
            assertTrue("Auteur renseigné : ${podcast.title}", podcast.author.isNotBlank())
        }
        // Chaque choix d'âge du sélecteur propose au moins un podcast.
        Catalog.AGE_CHOICES.forEach { age ->
            assertTrue("Aucun podcast pour $age ans", podcasts.any { Catalog.ageChoiceOf(it.minAge) == age })
        }
    }
}
