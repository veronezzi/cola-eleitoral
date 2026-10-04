package com.veronezzi.colaeleitoral.ui.common

import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionCalendar
import com.veronezzi.colaeleitoral.domain.model.Round
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The election the user picked in the Home selector, shared by Home and the "Meu santinho" tab.
 * Kept only for the app session: on the next start the app opens with the current election again
 * ([ElectionCalendar.currentElection]). Null means "the current election".
 */
@Singleton
class ElectionSelection @Inject constructor() {
    private val selected = MutableStateFlow<Long?>(null)

    val selectedElectionId: StateFlow<Long?> = selected.asStateFlow()

    fun select(electionId: Long?) {
        selected.value = electionId
    }
}

/** The election shown by the app and its round on the given day. */
data class ElectionContext(
    val election: Election,
    val round: Round,
)

/**
 * The selected election, or the current one when nothing (or an unknown id) is selected, with the
 * round to show on [today] (Brasília). Null when no election is known yet.
 */
fun resolveElection(elections: List<Election>, selectedId: Long?, today: LocalDate): ElectionContext? {
    val election = elections.firstOrNull { it.id == selectedId }
        ?: ElectionCalendar.currentElection(elections, today)
        ?: return null
    return ElectionContext(election, ElectionCalendar.roundOn(election, today))
}

/**
 * Whether picks of [round] can still be added or changed on [today]: false once the round's date
 * has passed (the ballot stays readable). Removing picks is always allowed.
 */
fun Election.isRoundOpen(round: Round, today: LocalDate): Boolean {
    val date = dateOf(round) ?: return true
    return !date.isBefore(today)
}
