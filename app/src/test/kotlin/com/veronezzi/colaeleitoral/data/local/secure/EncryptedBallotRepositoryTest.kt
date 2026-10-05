package com.veronezzi.colaeleitoral.data.local.secure

import app.cash.turbine.test
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.veronezzi.colaeleitoral.data.testing.FakeBallotCipher
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.SavePickResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.ProviderException
import java.time.Instant

/** Picks encrypted with a software AES-GCM key standing in for the AndroidKeyStore. */
class EncryptedBallotRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val cipher = FakeBallotCipher()
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var repository: EncryptedBallotRepository

    @Before
    fun setUp() {
        file = File(temporaryFolder.root, EncryptedBallotSerializer.FILE_NAME)
        repository = open()
    }

    @After
    fun tearDown() = runBlocking { scope.coroutineContext.job.cancelAndJoin() }

    /** A DataStore over [file], as the app builds it in StorageModule. */
    private fun open(): EncryptedBallotRepository {
        scope = CoroutineScope(Dispatchers.IO + Job())
        val dataStore = DataStoreFactory.create(
            serializer = EncryptedBallotSerializer(cipher),
            corruptionHandler = ReplaceFileCorruptionHandler { BallotStateDto(picksLost = true) },
            scope = scope,
            produceFile = { file },
        )
        return EncryptedBallotRepository(BallotStore(dataStore, cipher, file), Dispatchers.IO)
    }

    /** Simulates an app restart: the next read comes from the file. */
    private suspend fun restart() {
        scope.coroutineContext.job.cancelAndJoin()
        repository = open()
    }

    @Test
    fun picksComeBackInUrnaOrder() = runTest {
        repository.savePick(pick(officeCode = 1, urnaOrder = 5, candidateId = 13))
        repository.savePick(pick(officeCode = 5, urnaOrder = 3, slot = 2, candidateId = 200))
        repository.savePick(pick(officeCode = 6, urnaOrder = 1, candidateId = 5070))
        repository.savePick(pick(officeCode = 5, urnaOrder = 3, slot = 1, candidateId = 100))

        val ballot = repository.observeBallot(ELECTION, Round.FIRST).first()

        assertEquals(listOf(6 to 1, 5 to 1, 5 to 2, 1 to 1), ballot.map { it.officeCode to it.slot })
        assertTrue(repository.observeBallot(ELECTION, Round.SECOND).first().isEmpty())
    }

    @Test
    fun theSameSenatorInBothSlotsIsRefused() = runTest {
        repository.savePick(pick(officeCode = 5, urnaOrder = 3, slot = 1, candidateId = 100))

        val result = repository.savePick(pick(officeCode = 5, urnaOrder = 3, slot = 2, candidateId = 100))

        assertEquals(SavePickResult.DuplicateCandidate(otherSlot = 1), result)
        assertEquals(listOf(1), repository.observeBallot(ELECTION, Round.FIRST).first().map { it.slot })
    }

    @Test
    fun savingASlotAgainReplacesThePick() = runTest {
        repository.savePick(pick(officeCode = 3, urnaOrder = 4, candidateId = 15))

        assertEquals(SavePickResult.Saved, repository.savePick(pick(officeCode = 3, urnaOrder = 4, candidateId = 80)))
        assertEquals(listOf(80L), repository.observeBallot(ELECTION, Round.FIRST).first().map { it.candidateId })
    }

    @Test
    fun removeAndClearOnlyTouchTheirKeys() = runTest {
        val governor = pick(officeCode = 3, urnaOrder = 4, candidateId = 15)
        repository.savePick(governor)
        repository.savePick(pick(officeCode = 1, urnaOrder = 5, candidateId = 13))
        repository.savePick(pick(officeCode = 1, urnaOrder = 5, candidateId = 13, round = Round.SECOND))

        repository.removePick(governor.key)
        assertEquals(listOf(1), repository.observeBallot(ELECTION, Round.FIRST).first().map { it.officeCode })

        repository.clearBallot(ELECTION, Round.FIRST)
        assertTrue(repository.observeBallot(ELECTION, Round.FIRST).first().isEmpty())
        assertEquals(1, repository.observeBallot(ELECTION, Round.SECOND).first().size)
    }

    @Test
    fun picksAreEncryptedAtRestAndSurviveARestart() = runTest {
        val saved = pick(officeCode = 6, urnaOrder = 1, candidateId = 5070, ballotName = "NOME DE URNA SECRETO")
        repository.savePick(saved)

        val bytes = file.readBytes()
        assertEquals(EncryptedBallotSerializer.FORMAT_VERSION, bytes[0])
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("NOME DE URNA SECRETO"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("5070"))

        restart()

        assertEquals(listOf(saved), repository.observeBallot(ELECTION, Round.FIRST).first())
        assertFalse(repository.observePicksLost().first())
    }

    @Test
    fun alteredFileIsReplacedAndReportedUntilAcknowledged() = runTest {
        repository.savePick(pick(officeCode = 6, urnaOrder = 1, candidateId = 5070))
        scope.coroutineContext.job.cancelAndJoin()
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
        repository = open()

        assertTrue(repository.observeBallot(ELECTION, Round.FIRST).first().isEmpty())
        assertTrue(repository.observePicksLost().first())

        repository.acknowledgePicksLost()
        assertFalse(repository.observePicksLost().first())
        restart()
        assertFalse(repository.observePicksLost().first())
    }

    @Test
    fun invalidatedKeyLosesThePicksWithoutCrashingAndStartsOver() = runTest {
        repository.savePick(pick(officeCode = 6, urnaOrder = 1, candidateId = 5070))
        cipher.invalidate()
        restart()

        assertTrue(repository.observePicksLost().first())
        assertTrue(repository.observeBallot(ELECTION, Round.FIRST).first().isEmpty())

        assertEquals(SavePickResult.Saved, repository.savePick(pick(officeCode = 1, urnaOrder = 5, candidateId = 13)))
        restart()
        assertEquals(listOf(13L), repository.observeBallot(ELECTION, Round.FIRST).first().map { it.candidateId })
    }

    @Test
    fun keyMissingFromTheKeystoreIsReportedAsLostPicks() = runTest {
        repository.savePick(pick(officeCode = 6, urnaOrder = 1, candidateId = 5070))
        cipher.loseKey()
        restart()

        assertTrue(repository.observePicksLost().first())
    }

    @Test
    fun deleteAllRemovesTheFileAndTheKey() = runTest {
        repository.savePick(pick(officeCode = 6, urnaOrder = 1, candidateId = 5070))

        repository.deleteAll()

        assertTrue(repository.observeBallot(ELECTION, Round.FIRST).first().isEmpty())
        assertFalse(file.exists())
        assertFalse(cipher.hasKey)
        restart()
        assertFalse("deleting is not a loss", repository.observePicksLost().first())
    }

    @Test
    fun unusableKeystoreFailsTheSaveInsteadOfCrashing() = runTest {
        cipher.failEncryption = true

        val result = repository.savePick(pick(officeCode = 6, urnaOrder = 1, candidateId = 5070))

        assertEquals(SavePickResult.Failed(AppError.Storage), result)
    }

    @Test
    fun providerExceptionOnReadLeavesTheFileIntactAndTheBallotRecovers() = runTest {
        val saved = pick(officeCode = 6, urnaOrder = 1, candidateId = 5070)
        repository.savePick(saved)
        scope.coroutineContext.job.cancelAndJoin()
        val before = file.readBytes()
        cipher.decryptionFailure = ProviderException("Keystore not ready")
        repository = open()

        assertTrue(repository.observeUnavailable().first())
        assertTrue(repository.observeBallot(ELECTION, Round.FIRST).first().isEmpty())
        assertFalse("nothing was lost", repository.observePicksLost().first())
        assertEquals(
            "no write over a file that could not be read",
            SavePickResult.Failed(AppError.Storage),
            repository.savePick(pick(officeCode = 1, urnaOrder = 5, candidateId = 13)),
        )
        assertArrayEquals(before, file.readBytes())
        assertTrue(cipher.hasKey)
        assertEquals(0, cipher.deleteKeyCalls)

        cipher.decryptionFailure = null
        repository.retryRead()

        assertEquals(listOf(saved), repository.observeBallot(ELECTION, Round.FIRST).first())
        assertFalse(repository.observeUnavailable().first())
    }

    @Test
    fun aScreenObservingTheBallotSeesItComeBackAfterRetry() = runTest {
        val saved = pick(officeCode = 6, urnaOrder = 1, candidateId = 5070)
        repository.savePick(saved)
        scope.coroutineContext.job.cancelAndJoin()
        cipher.decryptionFailure = ProviderException("Keystore not ready")
        repository = open()

        repository.observeUnavailable().test {
            assertTrue(awaitItem())
            cipher.decryptionFailure = null
            repository.retryRead()
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(saved), repository.observeBallot(ELECTION, Round.FIRST).first())
    }

    @Test
    fun transientFailureOnWriteKeepsTheKeyAndTheSavedPicks() = runTest {
        val saved = pick(officeCode = 6, urnaOrder = 1, candidateId = 5070)
        repository.savePick(saved)
        cipher.encryptionFailure = ProviderException("Keystore busy")

        assertEquals(SavePickResult.Failed(AppError.Storage), repository.savePick(pick(officeCode = 1, urnaOrder = 5, candidateId = 13)))
        assertTrue(cipher.hasKey)
        assertEquals(0, cipher.deleteKeyCalls)

        cipher.encryptionFailure = null
        restart()
        assertEquals(listOf(saved), repository.observeBallot(ELECTION, Round.FIRST).first())
        assertFalse(repository.observePicksLost().first())
    }

    @Test
    fun sourceIsSavedWithThePick() = runTest {
        val fromOpenData = pick(officeCode = 6, urnaOrder = 1, candidateId = 5070).copy(source = DataSource.TSE_OPEN_DATA)
        repository.savePick(fromOpenData)

        restart()

        assertEquals(DataSource.TSE_OPEN_DATA, repository.observeBallot(ELECTION, Round.FIRST).first().single().source)
    }

    @Test
    fun picksSavedBeforeTheSourceFieldReadAsThePrimarySource() = runTest {
        scope.coroutineContext.job.cancelAndJoin()
        val oldFormat = """{"picks":[{"electionId":$ELECTION,"electionYear":2026,"round":1,"officeCode":6,""" +
            """"officeName":"Deputado Federal","urnaOrder":1,"digitCount":4,"slot":1,"ueCode":"SP","candidateId":5070,""" +
            """"candidateNumber":"5070","ballotName":"NOME","partyAcronym":"P","statusAtSave":"Deferido",""" +
            """"savedAtEpochMillis":0}]}"""
        val sealed = cipher.encrypt(oldFormat.encodeToByteArray(), EncryptedBallotSerializer.ASSOCIATED_DATA)
        file.writeBytes(byteArrayOf(EncryptedBallotSerializer.FORMAT_VERSION) + sealed)
        repository = open()

        val restored = repository.observeBallot(ELECTION, Round.FIRST).first().single()

        assertEquals(DataSource.DIVULGA_CAND_CONTAS, restored.source)
        assertEquals("5070", restored.candidateNumber)
    }

    @Test
    fun pickNeverPrintsWhoWasPicked() {
        val text = pick(officeCode = 6, urnaOrder = 1, candidateId = 5070, ballotName = "SECRETO").toString()

        assertFalse(text.contains("SECRETO"))
        assertFalse(text.contains("5070"))
    }

    @Test
    fun stateNeverPrintsPicks() {
        val dto = BallotStateDto(picks = listOf(BallotPickDto(ELECTION, 2026, 1, 6, "Deputado Federal", 1, 4, 1, "SP", 1, "5070", "SECRETO", "PSOL", statusAtSave = "Deferido", savedAtEpochMillis = 0)))

        assertFalse(dto.toString().contains("SECRETO"))
        assertFalse(dto.picks.single().toString().contains("5070"))
    }

    private fun pick(
        officeCode: Int,
        urnaOrder: Int,
        candidateId: Long,
        slot: Int = 1,
        round: Round = Round.FIRST,
        ballotName: String = "CANDIDATO $candidateId",
    ) = BallotPick(
        electionId = ELECTION,
        electionYear = 2026,
        round = round,
        officeCode = officeCode,
        officeName = "Cargo $officeCode",
        urnaOrder = urnaOrder,
        digitCount = 2,
        slot = slot,
        ueCode = "SP",
        candidateId = candidateId,
        candidateNumber = candidateId.toString(),
        ballotName = ballotName,
        partyAcronym = "PARTIDO",
        coalition = null,
        runningMateNames = listOf("VICE"),
        statusAtSave = "Deferido",
        savedAt = Instant.parse("2026-10-04T13:00:00Z"),
    )

    private companion object {
        const val ELECTION = 20322002026L
    }
}
