package com.veronezzi.colaeleitoral.data.local.secure

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/** Everything in the picks file. Sensitive: never logged (see the `toString` overrides). */
@Serializable
data class BallotStateDto(
    val picks: List<BallotPickDto> = emptyList(),
    /** Set when a previous file could not be decrypted and was replaced; cleared by the user. */
    val picksLost: Boolean = false,
) {
    override fun toString(): String = "BallotStateDto(picks=${picks.size}, picksLost=$picksLost)"
}

/**
 * Serialized [com.veronezzi.colaeleitoral.domain.model.BallotPick]. [source] is the
 * [com.veronezzi.colaeleitoral.domain.model.DataSource] name; files written before it existed have
 * no such key and read as the primary source.
 */
@Serializable
data class BallotPickDto(
    val electionId: Long,
    val electionYear: Int,
    val round: Int,
    val officeCode: Int,
    val officeName: String,
    val urnaOrder: Int,
    val digitCount: Int,
    val slot: Int,
    val ueCode: String,
    val candidateId: Long,
    val candidateNumber: String,
    val ballotName: String,
    val partyAcronym: String,
    val coalition: String? = null,
    val runningMateNames: List<String> = emptyList(),
    val statusAtSave: String,
    val savedAtEpochMillis: Long,
    val source: String? = null,
) {
    override fun toString(): String = "BallotPickDto(redacted)"
}

/**
 * DataStore serializer of the picks file, `[version: 1 byte][IV: 12 bytes][ciphertext + GCM tag]`
 * (ARCHITECTURE.md 5.2), with associated data binding it to this app and format version.
 *
 * Only a file that can never be read again is a [CorruptionException] (the DataStore corruption
 * handler then replaces it with an empty state flagged `picksLost`): failed authentication
 * (`AEADBadTagException`: altered or truncated file), a key that is missing or permanently
 * invalid ([BallotKeyInvalidatedException]), an unknown format version or invalid JSON. Every
 * other failure (a `ProviderException` or `KeyStoreException` right after boot, an I/O error) is
 * an [IOException]: DataStore keeps the file untouched and the read is tried again later. The key
 * is deleted only when it is permanently invalid. Exceptions never carry causes or details (a
 * parser message could quote plaintext).
 */
class EncryptedBallotSerializer(private val cipher: BallotCipher) : Serializer<BallotStateDto> {
    override val defaultValue: BallotStateDto = BallotStateDto()

    override suspend fun readFrom(input: InputStream): BallotStateDto {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        if (bytes[0] != FORMAT_VERSION) throw CorruptionException("Unsupported ballot file version")
        val plaintext = decrypt(bytes)
        return try {
            BallotJson.decodeFromString(BallotStateDto.serializer(), plaintext.decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Ballot file unreadable")
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Ballot file unreadable")
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun writeTo(t: BallotStateDto, output: OutputStream) {
        val plaintext = BallotJson.encodeToString(BallotStateDto.serializer(), t).encodeToByteArray()
        try {
            val sealed = try {
                cipher.encrypt(plaintext, ASSOCIATED_DATA)
            } catch (e: BallotKeyInvalidatedException) {
                // Permanently invalid key: whatever it encrypted is unreadable anyway, and the
                // state being written is complete, so a new key loses nothing.
                cipher.deleteKey()
                cipher.encrypt(plaintext, ASSOCIATED_DATA)
            }
            output.write(FORMAT_VERSION.toInt())
            output.write(sealed)
        } catch (e: GeneralSecurityException) {
            // Transient Keystore failure: the write fails and DataStore keeps the previous file.
            throw IOException("Could not encrypt the ballot")
        } catch (e: RuntimeException) {
            throw IOException("Could not encrypt the ballot")
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decrypt(bytes: ByteArray): ByteArray = try {
        cipher.decrypt(bytes.copyOfRange(1, bytes.size), ASSOCIATED_DATA)
    } catch (e: BallotKeyInvalidatedException) {
        cipher.deleteKey()
        throw CorruptionException("Ballot key unavailable")
    } catch (e: AEADBadTagException) {
        throw CorruptionException("Ballot file failed authentication")
    } catch (e: GeneralSecurityException) {
        throw IOException("Ballot key temporarily unusable")
    } catch (e: RuntimeException) {
        // ProviderException and other Keystore failures seen on some devices, often just after boot.
        throw IOException("Ballot key temporarily unusable")
    }

    companion object {
        const val FORMAT_VERSION: Byte = 1
        val ASSOCIATED_DATA: ByteArray = "com.veronezzi.colaeleitoral:ballot:v1".encodeToByteArray()

        /** File name in `noBackupFilesDir`. */
        const val FILE_NAME = "ballot.enc"

        private val BallotJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
