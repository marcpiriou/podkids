package fr.podkids.app.data

import fr.podkids.app.data.settings.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `reconnait le bon mot de passe et refuse les autres`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("chocolat", salt)
        assertTrue(PasswordHasher.matches("chocolat", salt, hash))
        assertFalse(PasswordHasher.matches("Chocolat", salt, hash))
        assertFalse(PasswordHasher.matches("", salt, hash))
    }

    @Test
    fun `l'empreinte est stable et ne contient pas le mot de passe`() {
        val hash = PasswordHasher.hash("1234", "sel")
        assertEquals(hash, PasswordHasher.hash("1234", "sel"))
        assertEquals(64, hash.length)
        assertFalse(hash.contains("1234"))
    }

    @Test
    fun `le sel change l'empreinte`() {
        assertNotEquals(PasswordHasher.newSalt(), PasswordHasher.newSalt())
        assertNotEquals(PasswordHasher.hash("1234", "a"), PasswordHasher.hash("1234", "b"))
    }
}
