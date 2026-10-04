package com.veronezzi.colaeleitoral.domain.repository

import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.BallotSlotKey
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.SavePickResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The user's picks, encrypted on the device (AES-GCM key in AndroidKeyStore, file excluded from
 * backup). Implementations must never log pick contents.
 */
interface BallotRepository {
    /**
     * True after saved picks could not be decrypted (Keystore key invalidated, file corrupted)
     * and were discarded so the app keeps working. The UI shows a one-time notice ("Não foi
     * possível ler as escolhas salvas neste aparelho; elas foram apagadas por segurança.") and
     * then calls [acknowledgePicksLost]. Survives restarts until acknowledged.
     */
    fun observePicksLost(): Flow<Boolean> = flowOf(false)

    /** Clears the [observePicksLost] flag once the user has seen the notice. */
    suspend fun acknowledgePicksLost() {}

    /** Picks of one election round, in urna order then slot. */
    fun observeBallot(electionId: Long, round: Round): Flow<List<BallotPick>>

    /** Saves or replaces the pick in [BallotPick.key]. Refuses the same candidate in two slots. */
    suspend fun savePick(pick: BallotPick): SavePickResult

    suspend fun removePick(key: BallotSlotKey)

    suspend fun clearBallot(electionId: Long, round: Round)

    /** Deletes every pick and the encryption key ("Apagar meus dados"). */
    suspend fun deleteAll()
}
