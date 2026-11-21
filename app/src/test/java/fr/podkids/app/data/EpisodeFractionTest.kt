package fr.podkids.app.data

import fr.podkids.app.ui.podcast.episodeFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeFractionTest {

    @Test
    fun `avancement proportionnel a la position`() {
        assertEquals(0f, episodeFraction(0, 600_000, completed = false)!!, 0.0001f)
        assertEquals(0.25f, episodeFraction(150_000, 600_000, completed = false)!!, 0.0001f)
    }

    @Test
    fun `un episode lu est complet`() {
        assertEquals(1f, episodeFraction(0, 600_000, completed = true)!!, 0.0001f)
        assertEquals(1f, episodeFraction(0, 0, completed = true)!!, 0.0001f)
    }

    @Test
    fun `duree inconnue et depassements`() {
        assertNull(episodeFraction(10_000, 0, completed = false))
        assertEquals(1f, episodeFraction(700_000, 600_000, completed = false)!!, 0.0001f)
    }
}
