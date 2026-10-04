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

/** Everything in the picks file. Sensitive: never logged (see the `toString` overrides). */
@Serializable
data class BallotStateDto(
    val picks: List<BallotPickDto> = emptyList(),
    /** Set when a previous file could not be decrypted and was replaced; cleared by the user. */
    val picksLost: Boolean = false,
) {
    override fun toString(): String = "BallotStateDto(picks=${picks.size}, picksLost=$picksLost)"
}

/** Serialized [com.veronezzi.colaeleitoral.domain.model.BallotPick]. */
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
) {
    override fun toString(): String = "BallotPickDto(redacted)"
}

/**
 * DataStore serializer of the picks file, `[version: 1 byte][IV: 12 bytes][ciphertext + GCM tag]`
 * (ARCHITECTURE.md 5.2), with associated data binding it to this app and format version.
 *
 * Anything unreadable (invalidated or missing key, altered or truncated file, unknown version)
 * becomes a [CorruptionException] without cause or message details (a parser message could quote
 * plaintext); the DataStore corruption handler then replaces the file with an empty state flagged
 * `picksLost`. A permanently invalid key is deleted so the next write creates a new one.
 */
class EncryptedBallotSerializer(private val cipher: BallotCipher) : Serializer<BallotStateDto> {
    override val defaultValue: BallotStateDto = BallotStateDto()

    override suspend fun readFrom(input: InputStream): BallotStateDto {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        if (bytes[0] != FORMAT_VERSION) throw CorruptionException("Unsupported ballot file version")
        val plaintext = try {
            cipher.decrypt(bytes.copyOfRange(1, bytes.size), ASSOCIATED_DATA)
        } catch (e: BallotKeyInvalidatedException) {
            cipher.deleteKey()
            throw CorruptionException("Ballot key unavailable")
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("Ballot file failed authentication")
        } catch (e: RuntimeException) {
            // ProviderException and other Keystore failures seen on some devices.
            throw CorruptionException("Ballot file unreadable")
        }
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
            } catch (e: GeneralSecurityException) {
                // Invalid or unusable key. The state being written is complete, so starting over
                // with a new key loses nothing.
                cipher.deleteKey()
                cipher.encrypt(plaintext, ASSOCIATED_DATA)
            }
            output.write(FORMAT_VERSION.toInt())
            output.write(sealed)
        } catch (e: GeneralSecurityException) {
            throw IOException("Could not encrypt the ballot")
        } finally {
            plaintext.fill(0)
        }
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
