package fr.podkids.app.data

import fr.podkids.app.data.catalog.CatalogApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogApiTest {

    private fun result(
        id: Long,
        name: String,
        feedUrl: String?,
        genres: List<String>,
        explicitness: String = "notExplicit",
    ): String {
        val feed = if (feedUrl == null) "" else "\"feedUrl\": \"$feedUrl\","
        return """
            {
              "collectionId": $id,
              "collectionName": "$name",
              "artistName": "Auteur $id",
              $feed
              "artworkUrl100": "https://example.org/$id-100.jpg",
              "artworkUrl600": "https://example.org/$id-600.jpg",
              "collectionExplicitness": "$explicitness",
              "genreIds": [${genres.joinToString(",") { "\"$it\"" }}]
            }
        """.trimIndent()
    }

    private fun response(vararg results: String) =
        """{"resultCount": ${results.size}, "results": [${results.joinToString(",")}]}"""

    @Test
    fun `garde uniquement les podcasts pour enfants avec un flux`() {
        val json = response(
            result(1, "Histoires pour enfants", "https://example.org/1.xml", listOf("1520", "26", "1305")),
            result(2, "Actualité", "https://example.org/2.xml", listOf("1489", "26")),
            result(3, "Sans flux", null, listOf("1305", "26")),
            result(4, "Explicite", "https://example.org/4.xml", listOf("1305", "26"), explicitness = "explicit"),
            result(5, "Parentalité", "https://example.org/5.xml", listOf("1521", "26", "1305")),
        )
        val podcasts = CatalogApi.parseResults(json)
        assertEquals(listOf(1L), podcasts.map { it.id })
        with(podcasts.single()) {
            assertEquals("Histoires pour enfants", title)
            assertEquals("Auteur 1", author)
            assertEquals("https://example.org/1.xml", feedUrl)
            assertEquals("https://example.org/1-600.jpg", artworkUrl)
        }
    }

    @Test
    fun `supprime les doublons de flux`() {
        val json = response(
            result(1, "A", "https://example.org/same.xml", listOf("1305")),
            result(2, "B", "https://example.org/same.xml", listOf("1305")),
        )
        assertEquals(1, CatalogApi.parseResults(json).size)
    }

    @Test
    fun `lit les identifiants d'un classement`() {
        val json = """
            {"feed": {"entry": [
              {"id": {"label": "x", "attributes": {"im:id": "1463322273"}}},
              {"id": {"label": "y", "attributes": {"im:id": "1167870621"}}}
            ]}}
        """.trimIndent()
        assertEquals(listOf(1463322273L, 1167870621L), CatalogApi.parseChartIds(json))
    }

    @Test
    fun `accepte un classement vide ou a entree unique`() {
        assertTrue(CatalogApi.parseChartIds("""{"feed": {}}""").isEmpty())
        val single = """{"feed": {"entry": {"id": {"attributes": {"im:id": "42"}}}}}"""
        assertEquals(listOf(42L), CatalogApi.parseChartIds(single))
    }
}
