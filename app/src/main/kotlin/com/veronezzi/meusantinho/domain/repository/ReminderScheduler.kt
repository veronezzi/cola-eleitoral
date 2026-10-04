package com.veronezzi.meusantinho.domain.repository

import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.Round
import java.time.LocalDate

/**
 * Opt-in election-day reminder (WorkManager in the data layer), at
 * [com.veronezzi.meusantinho.domain.model.ElectionCalendar.REMINDER_TIME] Brasília time.
 * The notification text is generic: it never includes picks or candidate names.
 */
interface ReminderScheduler {
    /** Schedules, or replaces, the reminder for [round] of [election] on [date]. Past dates are ignored. */
    suspend fun schedule(election: Election, round: Round, date: LocalDate)

    suspend fun cancel(electionId: Long, round: Round)

    suspend fun cancelAll()
}
