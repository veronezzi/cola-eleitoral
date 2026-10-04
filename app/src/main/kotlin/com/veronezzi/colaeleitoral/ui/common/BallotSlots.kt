package com.veronezzi.colaeleitoral.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.Office

/**
 * One vote on the urna: an office and, for offices with two seats, the slot. [voteNumber] is the
 * 1-based position in the voting sequence (in 2026 the Senate takes positions 3 and 4).
 *
 * @property pick the saved choice for this vote, if any.
 */
data class BallotSlot(
    val office: Office,
    val slot: Int,
    val voteNumber: Int,
    val pick: BallotPick?,
) {
    val key: String get() = "${office.code}:$slot"

    val hasSeveralSeats: Boolean get() = office.maxPicks > 1

    /** The pick was saved for another electoral unit (the user changed their voting place). */
    val pickIsFromOtherPlace: Boolean get() = pick != null && pick.ueCode != office.ueCode
}

/** Votes of a ballot in urna order (ARCHITECTURE.md 2.4), each with its saved pick. */
fun buildBallotSlots(offices: List<Office>, picks: List<BallotPick>): List<BallotSlot> {
    var voteNumber = 0
    return offices.sortedWith(compareBy<Office> { it.urnaOrder }.thenBy { it.code }).flatMap { office ->
        (1..office.maxPicks.coerceAtLeast(1)).map { slot ->
            voteNumber += 1
            BallotSlot(
                office = office,
                slot = slot,
                voteNumber = voteNumber,
                pick = picks.firstOrNull { it.officeCode == office.code && it.slot == slot },
            )
        }
    }
}

/** Saved picks that match no vote of the current ballot (for example, after a change of place). */
fun picksOutsideBallot(slots: List<BallotSlot>, picks: List<BallotPick>): List<BallotPick> {
    val used = slots.mapNotNull { it.pick?.key }.toSet()
    return picks.filter { it.key !in used }.sortedWith(compareBy({ it.urnaOrder }, { it.slot }))
}

/** "Senador: 1ª vaga" for offices with two seats, the office name otherwise. */
@Composable
fun slotLabel(officeName: String, slot: Int, hasSeveralSeats: Boolean): String =
    if (hasSeveralSeats) stringResource(R.string.ballot_slot_label, officeName, slot) else officeName

/** Spoken form for TalkBack: "Senador, primeira vaga". */
@Composable
fun slotSpokenLabel(officeName: String, slot: Int, hasSeveralSeats: Boolean): String = if (hasSeveralSeats) {
    val ordinal = stringResource(if (slot == 1) R.string.ballot_slot_first else R.string.ballot_slot_second)
    stringResource(R.string.ballot_slot_spoken, officeName, ordinal)
} else {
    officeName
}
