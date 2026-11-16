package fr.podkids.app.data

import fr.podkids.app.data.settings.UiSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildModeTest {

    private val restricted = UiSettings(hideCatalogOnHome = true, downloadedOnly = true, settingsLocked = true)

    @Test
    fun `le mode enfant est actif par defaut`() {
        assertTrue(UiSettings().childMode)
    }

    @Test
    fun `en mode enfant les restrictions s'appliquent`() {
        val settings = restricted.copy(childMode = true)
        assertTrue(settings.catalogHidden)
        assertTrue(settings.onlyDownloaded)
        assertTrue(settings.passwordRequired)
    }

    @Test
    fun `en mode adulte tout est accessible`() {
        val settings = restricted.copy(childMode = false)
        assertFalse(settings.catalogHidden)
        assertFalse(settings.onlyDownloaded)
        assertFalse(settings.passwordRequired)
    }

    @Test
    fun `sans restriction le mode enfant ne masque rien`() {
        val settings = UiSettings(childMode = true)
        assertFalse(settings.catalogHidden)
        assertFalse(settings.onlyDownloaded)
        assertFalse(settings.passwordRequired)
    }
}
