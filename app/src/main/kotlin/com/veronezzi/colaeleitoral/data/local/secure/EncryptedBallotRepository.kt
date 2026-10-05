package com.veronezzi.colaeleitoral.data.local.secure

import androidx.datastore.core.DataStore
import com.veronezzi.colaeleitoral.core.common.IoDispatcher
import com.veronezzi.colaeleitoral.data.local.isStorageFailure
import com.veronezzi.colaeleitoral.data.mapper.dataSourceOf
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.BallotSlotKey
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.SavePickResult
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The encrypted DataStore of picks, its cipher and its file (for "Apagar meus dados"). */
class BallotStore(
    val dataStore: DataStore<BallotStateDto>,
    val cipher: BallotCipher,
    val file: File,
)

/**
 * Picks encrypted at rest ([EncryptedBallotSerializer]) in `noBackupFilesDir`. Nothing here logs,
 * and failures never carry pick contents. A file that can never be read again is replaced by an
 * empty state and reported once through [observePicksLost] (risk R7). A read that fails for now
 * (Keystore not ready, I/O error) deletes nothing: [observeUnavailable] turns true, the ballot
 * shows the empty placeholder, writes fail with `AppError.Storage` (DataStore never writes over a
 * file it could not read) and the read is retried with backoff or on [retryRead].
 */
@Singleton
class EncryptedBallotRepository @Inject constructor(
    private val store: BallotStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BallotRepository {
    private val retryRequests = MutableStateFlow(0)

    private val reads: Flow<BallotRead> = store.dataStore.data
        .map<BallotStateDto, BallotRead> { BallotRead.Available(it) }
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            val seen = retryRequests.value
            emit(BallotRead.Unavailable)
            withTimeoutOrNull(retryDelay(attempt)) { retryRequests.first { it != seen } }
            true
        }

    override fun observeBallot(electionId: Long, round: Round): Flow<List<BallotPick>> = reads
        .map { read ->
            read.state?.picks.orEmpty()
                .filter { it.electionId == electionId && it.round == round.number }
                .mapNotNull { it.toDomainOrNull() }
                .sortedWith(PICK_ORDER)
        }
        .distinctUntilChanged()

    override fun observePicksLost(): Flow<Boolean> = reads.map { it.state?.picksLost == true }.distinctUntilChanged()

    override fun observeUnavailable(): Flow<Boolean> = reads.map { it is BallotRead.Unavailable }.distinctUntilChanged()

    override suspend fun retryRead() {
        retryRequests.update { it + 1 }
    }

    override suspend fun acknowledgePicksLost() {
        update { it.copy(picksLost = false) }
    }

    override suspend fun savePick(pick: BallotPick): SavePickResult {
        var result: SavePickResult = SavePickResult.Saved
        return try {
            store.dataStore.updateData { current ->
                val duplicate = current.picks.firstOrNull {
                    it.electionId == pick.electionId && it.round == pick.round.number &&
                        it.officeCode == pick.officeCode && it.slot != pick.slot && it.candidateId == pick.candidateId
                }
                if (duplicate != null) {
                    result = SavePickResult.DuplicateCandidate(duplicate.slot)
                    current
                } else {
                    result = SavePickResult.Saved
                    current.copy(picks = current.picks.filterNot { it.isAt(pick.key) } + pick.toDto())
                }
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Deliberately without the exception: nothing about the pick may leave this method.
            SavePickResult.Failed(if (e.isStorageFailure()) AppError.Storage else AppError.Unknown(null))
        }
    }

    override suspend fun removePick(key: BallotSlotKey) {
        update { current -> current.copy(picks = current.picks.filterNot { it.isAt(key) }) }
    }

    override suspend fun clearBallot(electionId: Long, round: Round) {
        update { current ->
            current.copy(picks = current.picks.filterNot { it.electionId == electionId && it.round == round.number })
        }
    }

    override suspend fun deleteAll() {
        update { BallotStateDto() }
        withContext(ioDispatcher) {
            store.cipher.deleteKey()
            store.file.delete()
        }
    }

    /** Applies [transform]; a storage failure leaves the picks unchanged instead of crashing. */
    private suspend fun update(transform: (BallotStateDto) -> BallotStateDto) {
        try {
            store.dataStore.updateData { transform(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Deliberately not logged: the state may hold picks.
        }
    }

    /** One read of the picks file: its state, or "can't be read right now". */
    private sealed interface BallotRead {
        val state: BallotStateDto?

        data class Available(override val state: BallotStateDto) : BallotRead

        data object Unavailable : BallotRead {
            override val state: BallotStateDto? = null
        }
    }

    private companion object {
        private val RETRY_BASE: Duration = 2.seconds
        private val RETRY_MAX: Duration = 1.minutes

        /** 2 s, 4 s, 8 s... up to 1 min between automatic read attempts. */
        fun retryDelay(attempt: Long): Duration = minOf(RETRY_MAX, RETRY_BASE * (1 shl attempt.coerceAtMost(5).toInt()))

        val PICK_ORDER: Comparator<BallotPick> = compareBy({ it.urnaOrder }, { it.officeCode }, { it.slot })

        fun BallotPickDto.isAt(key: BallotSlotKey): Boolean = electionId == key.electionId &&
            round == key.round.number && officeCode == key.officeCode && slot == key.slot

        fun BallotPick.toDto() = BallotPickDto(
            electionId = electionId,
            electionYear = electionYear,
            round = round.number,
            officeCode = officeCode,
            officeName = officeName,
            urnaOrder = urnaOrder,
            digitCount = digitCount,
            slot = slot,
            ueCode = ueCode,
            candidateId = candidateId,
            candidateNumber = candidateNumber,
            ballotName = ballotName,
            partyAcronym = partyAcronym,
            coalition = coalition,
            runningMateNames = runningMateNames,
            statusAtSave = statusAtSave,
            savedAtEpochMillis = savedAt.toEpochMilli(),
            source = source.name,
        )

        fun BallotPickDto.toDomainOrNull(): BallotPick? = BallotPick(
            electionId = electionId,
            electionYear = electionYear,
            round = Round.fromNumber(round) ?: return null,
            officeCode = officeCode,
            officeName = officeName,
            urnaOrder = urnaOrder,
            digitCount = digitCount,
            slot = slot,
            ueCode = ueCode,
            candidateId = candidateId,
            candidateNumber = candidateNumber,
            ballotName = ballotName,
            partyAcronym = partyAcronym,
            coalition = coalition,
            runningMateNames = runningMateNames,
            statusAtSave = statusAtSave,
            savedAt = Instant.ofEpochMilli(savedAtEpochMillis),
            source = dataSourceOf(source),
        )
    }
}
