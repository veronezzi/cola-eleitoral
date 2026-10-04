package com.veronezzi.colaeleitoral.data.local.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/**
 * The real AndroidKeyStore cipher (needs a device or emulator; JVM tests use FakeBallotCipher).
 * Uses its own alias so it never touches the app's key.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreBallotCipherTest {
    private val cipher = KeystoreBallotCipher(alias = "ballot_key_instrumented_test")
    private val associatedData = EncryptedBallotSerializer.ASSOCIATED_DATA
    private val plaintext = """{"picks":[{"ballotName":"TESTE"}]}""".encodeToByteArray()

    @After
    fun tearDown() = cipher.deleteKey()

    @Test
    fun encryptsAndDecryptsWithAFreshIvEachTime() {
        val first = cipher.encrypt(plaintext, associatedData)
        val second = cipher.encrypt(plaintext, associatedData)

        assertEquals(BallotCipher.IV_SIZE + plaintext.size + BallotCipher.TAG_BITS / 8, first.size)
        assertFalse(first.copyOfRange(0, BallotCipher.IV_SIZE).contentEquals(second.copyOfRange(0, BallotCipher.IV_SIZE)))
        assertArrayEquals(plaintext, cipher.decrypt(first, associatedData))
        assertArrayEquals(plaintext, cipher.decrypt(second, associatedData))
    }

    @Test
    fun alteredDataOrOtherAssociatedDataFailsAuthentication() {
        val sealed = cipher.encrypt(plaintext, associatedData)
        val altered = sealed.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }

        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(altered, associatedData) }
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(sealed, "other".encodeToByteArray()) }
    }

    @Test
    fun deletingTheKeyMakesOldDataUnreadable() {
        val sealed = cipher.encrypt(plaintext, associatedData)

        cipher.deleteKey()

        assertThrows(BallotKeyInvalidatedException::class.java) { cipher.decrypt(sealed, associatedData) }
        // A new key is created on the next encryption, and it cannot read the old data either.
        cipher.encrypt(plaintext, associatedData)
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(sealed, associatedData) }
    }
}
