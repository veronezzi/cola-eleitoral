package com.veronezzi.colaeleitoral.data.local.secure

import androidx.datastore.core.DataStore
import com.veronezzi.colaeleitoral.core.common.IoDispatcher
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.BallotSlotKey
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.SavePickResult
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The encrypted DataStore of picks, its cipher and its file (for "Apagar meus dados"). */
class BallotStore(
    val dataStore: DataStore<BallotStateDto>,
    val cipher: BallotCipher,
    val file: File,
)

/**
 * Picks encrypted at rest ([EncryptedBallotSerializer]) in `noBackupFilesDir`. Nothing here logs,
 * and failures never carry pick contents. Unreadable files are replaced by an empty state and
 * reported once through [observePicksLost] (risk R7).
 */
@Singleton
class EncryptedBallotRepository @Inject constructor(
    private val store: BallotStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BallotRepository {
    private val state: Flow<BallotStateDto> = store.dataStore.data
        .catch { e -> if (e is IOException) emit(BallotStateDto()) else throw e }

    override fun observeBallot(electionId: Long, round: Round): Flow<List<BallotPick>> = state
        .map { current ->
            current.picks
                .filter { it.electionId == electionId && it.round == round.number }
                .mapNotNull { it.toDomainOrNull() }
                .sortedWith(PICK_ORDER)
        }
        .distinctUntilChanged()

    override fun observePicksLost(): Flow<Boolean> = state.map { it.picksLost }.distinctUntilChanged()

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
            SavePickResult.Failed(AppError.Unknown(null))
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

    private companion object {
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
        )
    }
}
