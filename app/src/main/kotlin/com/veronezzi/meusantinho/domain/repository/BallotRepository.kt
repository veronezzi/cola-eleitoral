package com.veronezzi.meusantinho.domain.repository

import com.veronezzi.meusantinho.domain.model.BallotPick
import com.veronezzi.meusantinho.domain.model.BallotSlotKey
import com.veronezzi.meusantinho.domain.model.Round
import com.veronezzi.meusantinho.domain.model.SavePickResult
import kotlinx.coroutines.flow.Flow

/**
 * The user's picks, encrypted on the device (AES-GCM key in AndroidKeyStore, file excluded from
 * backup). Implementations must never log pick contents.
 */
interface BallotRepository {
    /** Picks of one election round, in urna order then slot. */
    fun observeBallot(electionId: Long, round: Round): Flow<List<BallotPick>>

    /** Saves or replaces the pick in [BallotPick.key]. Refuses the same candidate in two slots. */
    suspend fun savePick(pick: BallotPick): SavePickResult

    suspend fun removePick(key: BallotSlotKey)

    suspend fun clearBallot(electionId: Long, round: Round)

    /** Deletes every pick and the encryption key ("Apagar meus dados"). */
    suspend fun deleteAll()
}
