package com.veronezzi.meusantinho.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.veronezzi.meusantinho.domain.model.ElectionCalendar
import com.veronezzi.meusantinho.domain.model.Round
import com.veronezzi.meusantinho.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/**
 * Shows the election-day reminder. Does nothing when the reminder was turned off meanwhile, or
 * when the work runs late (device off) and it is no longer election day before the polls close.
 */
@HiltWorker
class ElectionReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val round = Round.fromNumber(inputData.getInt(KEY_ROUND, Round.FIRST.number)) ?: Round.FIRST
        val electionDay = inputData.getString(KEY_DATE)?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }
        val now = ZonedDateTime.now(clock.withZone(ElectionCalendar.BRASILIA))
        val isStillUseful = electionDay == null ||
            (now.toLocalDate() == electionDay && now.toLocalTime().isBefore(ElectionCalendar.POLLS_CLOSE))
        if (isStillUseful && settingsRepository.settings.first().reminderEnabled) {
            ReminderNotifications.show(applicationContext, round)
        }
        return Result.success()
    }

    companion object {
        const val KEY_ELECTION_ID = "election_id"
        const val KEY_ROUND = "round"
        const val KEY_DATE = "date"
    }
}
