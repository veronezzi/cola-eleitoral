package com.veronezzi.colaeleitoral.domain.model

import java.time.Instant

/** Identifies one vote on the ballot: offices with two seats have slots 1 and 2. */
data class BallotSlotKey(
    val electionId: Long,
    val round: Round,
    val officeCode: Int,
    val slot: Int,
)

/**
 * A saved choice. It is a self-contained snapshot, so the cola renders offline even when the cache
 * is cleared or the TSE is unreachable. Sensitive data (political opinion, LGPD art. 5º, II):
 * stored only encrypted on the device, never logged, never sent anywhere, never in notifications.
 *
 * @property slot 1-based vote index, up to [Office.maxPicks].
 * @property candidateNumber exactly [digitCount] digits, as typed on the urna.
 * @property statusAtSave TSE registration status text when the pick was saved.
 */
data class BallotPick(
    val electionId: Long,
    val electionYear: Int,
    val round: Round,
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
    val coalition: String?,
    val runningMateNames: List<String>,
    val statusAtSave: String,
    val savedAt: Instant,
) {
    val key: BallotSlotKey get() = BallotSlotKey(electionId, round, officeCode, slot)
}

sealed interface SavePickResult {
    data object Saved : SavePickResult

    /**
     * The candidate is already in another slot of the same office. On the urna a repeated Senate
     * vote is void (Res. TSE 23.751/2026), so the app refuses it.
     */
    data class DuplicateCandidate(val otherSlot: Int) : SavePickResult

    data class Failed(val error: AppError) : SavePickResult
}
