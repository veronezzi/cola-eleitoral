package com.veronezzi.colaeleitoral.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Electoral round. The TSE lists both rounds of an ordinary election under one election id. */
enum class Round(val number: Int) {
    FIRST(1),
    SECOND(2),
    ;

    companion object {
        fun fromNumber(number: Int): Round? = entries.firstOrNull { it.number == number }
    }
}

/** TSE `tipoAbrangencia`: "F" (general election) or "M" (municipal election). */
enum class ElectionScope {
    GENERAL,
    MUNICIPAL,
}

/**
 * An ordinary election as listed by the TSE (`eleicao/ordinarias`).
 *
 * @property id TSE election id. Long: the 2026 id is 20322002026, outside the Int range.
 * @property round TSE `turno`. Null when the entry covers both rounds, which is the case for every
 * ordinary election observed so far.
 * @property date first-round date (`dataEleicao`). Null for some old elections (2004-2010).
 */
data class Election(
    val id: Long,
    val year: Int,
    val name: String,
    val round: Round?,
    val scope: ElectionScope,
    val date: LocalDate?,
) {
    /**
     * Second-round date from the Constitution: first Sunday of October for round 1 and last Sunday
     * of October for round 2 (CF art. 77; art. 29, II for mayors). Null when the first round is not
     * in October (2020 was moved to November by EC 107/2020), because the rule does not apply.
     */
    val secondRoundDate: LocalDate?
        get() {
            val first = date ?: return null
            if (first.month != Month.OCTOBER) return null
            val lastSunday = first.with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY))
            return lastSunday.takeIf { it.isAfter(first) }
        }

    /** Date of [round], when known. */
    fun dateOf(round: Round): LocalDate? = when (round) {
        Round.FIRST -> date
        Round.SECOND -> secondRoundDate
    }
}

/** Calendar rules shared by the whole app. All election dates are in Brasília time. */
object ElectionCalendar {
    /** Polls open from 8h to 17h, Brasília time, in the whole country (Res. TSE 23.751/2026). */
    val BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")
    val POLLS_OPEN: LocalTime = LocalTime.of(8, 0)
    val POLLS_CLOSE: LocalTime = LocalTime.of(17, 0)

    /** Time of the opt-in reminder on election day (one hour before the polls open). */
    val REMINDER_TIME: LocalTime = LocalTime.of(7, 0)

    /**
     * The election the app opens with: the nearest one whose last possible round is today or later;
     * if none, the most recent one. [today] must be the current date in [BRASILIA].
     */
    fun currentElection(elections: List<Election>, today: LocalDate): Election? {
        val dated = elections.mapNotNull { election -> election.date?.let { election to it } }
        val upcoming = dated.filter { (election, firstRound) ->
            !(election.secondRoundDate ?: firstRound).isBefore(today)
        }
        return upcoming.minByOrNull { it.second }?.first
            ?: dated.maxByOrNull { it.second }?.first
            ?: elections.maxByOrNull { it.year }
    }

    /**
     * Round to show for [election] on [today]. SECOND only means "between the rounds": whether the
     * voter's units have a runoff comes from the candidates marked "2º turno" by the TSE.
     */
    fun roundOn(election: Election, today: LocalDate): Round {
        election.round?.let { return it }
        val first = election.date ?: return Round.FIRST
        val second = election.secondRoundDate ?: return Round.FIRST
        return if (today.isAfter(first) && !today.isAfter(second)) Round.SECOND else Round.FIRST
    }
}
