package fr.podkids.app.data.settings

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Empreinte du mot de passe des réglages. Seule l'empreinte salée est enregistrée, jamais le mot de
 * passe lui-même. Il s'agit d'un verrou parental contre les manipulations d'un enfant, pas d'une
 * protection contre un attaquant ayant accès aux données de l'appareil.
 */
object PasswordHasher {
    const val MIN_LENGTH = 4
    private const val ITERATIONS = 10_000

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var value = (salt + password).toByteArray(Charsets.UTF_8)
        repeat(ITERATIONS) {
            digest.reset()
            value = digest.digest(value)
        }
        return value.toHex()
    }

    /** Comparaison en temps constant. */
    fun matches(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(password, salt).toByteArray(), expectedHash.toByteArray())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
