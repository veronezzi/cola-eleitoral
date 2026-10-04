package com.veronezzi.meusantinho.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.ElectionCalendar
import com.veronezzi.meusantinho.domain.model.Round
import com.veronezzi.meusantinho.domain.repository.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in election-day reminder with WorkManager: one unique `OneTimeWorkRequest` per (election,
 * round), replaced on every [schedule], due at [ElectionCalendar.REMINDER_TIME] in Brasília. No
 * exact alarm: a few minutes of slack are fine. The worker re-checks the setting before notifying.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun schedule(election: Election, round: Round, date: LocalDate) {
        val dueAt = date.atTime(ElectionCalendar.REMINDER_TIME).atZone(ElectionCalendar.BRASILIA).toInstant()
        val delay = Duration.between(clock.instant(), dueAt)
        if (delay.isNegative || delay.isZero) return
        ReminderNotifications.ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<ElectionReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ElectionReminderWorker.KEY_ELECTION_ID to election.id,
                    ElectionReminderWorker.KEY_ROUND to round.number,
                    ElectionReminderWorker.KEY_DATE to date.toString(),
                ),
            )
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(uniqueWorkName(election.id, round), ExistingWorkPolicy.REPLACE, request).await()
    }

    override suspend fun cancel(electionId: Long, round: Round) {
        workManager.cancelUniqueWork(uniqueWorkName(electionId, round)).await()
    }

    override suspend fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG).await()
    }

    companion object {
        const val TAG = "election-reminder"

        fun uniqueWorkName(electionId: Long, round: Round) = "election-reminder-$electionId-${round.number}"
    }
}
