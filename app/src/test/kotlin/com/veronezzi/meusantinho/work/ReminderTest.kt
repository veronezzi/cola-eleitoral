package com.veronezzi.meusantinho.work

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.veronezzi.meusantinho.data.testing.FakeSettingsRepository
import com.veronezzi.meusantinho.data.testing.MutableClock
import com.veronezzi.meusantinho.data.testing.TestElections
import com.veronezzi.meusantinho.domain.model.Round
import com.veronezzi.meusantinho.domain.model.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ReminderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = MutableClock(Instant.parse("2026-10-20T12:00:00Z"))
    private val workManager: WorkManager get() = WorkManager.getInstance(context)
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun schedulesOneUniqueRequestForSevenInTheMorningInBrasilia() = runTest {
        val scheduler = WorkManagerReminderScheduler(context, clock)

        scheduler.schedule(TestElections.GENERAL_2026, Round.SECOND, LocalDate.of(2026, 10, 25))
        scheduler.schedule(TestElections.GENERAL_2026, Round.SECOND, LocalDate.of(2026, 10, 25))

        val name = WorkManagerReminderScheduler.uniqueWorkName(TestElections.GENERAL_2026.id, Round.SECOND)
        val active = workManager.getWorkInfosForUniqueWork(name).get().filter { it.state == WorkInfo.State.ENQUEUED }
        val info = active.single()
        // 25/10 07:00 in Brasília (UTC-3) is 10:00 UTC, 4 days and 22 hours after 20/10 12:00 UTC.
        assertEquals(Duration.ofDays(4).plusHours(22).toMillis(), info.initialDelayMillis)
        assertTrue(WorkManagerReminderScheduler.TAG in info.tags)
    }

    @Test
    fun pastReminderTimesAreIgnored() = runTest {
        val scheduler = WorkManagerReminderScheduler(context, clock)

        scheduler.schedule(TestElections.GENERAL_2026, Round.FIRST, LocalDate.of(2026, 10, 4))

        val name = WorkManagerReminderScheduler.uniqueWorkName(TestElections.GENERAL_2026.id, Round.FIRST)
        assertTrue(workManager.getWorkInfosForUniqueWork(name).get().isEmpty())
    }

    @Test
    fun cancelAllRemovesTheReminders() = runTest {
        val scheduler = WorkManagerReminderScheduler(context, clock)
        scheduler.schedule(TestElections.GENERAL_2026, Round.SECOND, LocalDate.of(2026, 10, 25))

        scheduler.cancelAll()

        val name = WorkManagerReminderScheduler.uniqueWorkName(TestElections.GENERAL_2026.id, Round.SECOND)
        assertTrue(workManager.getWorkInfosForUniqueWork(name).get().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun workerPostsAGenericNotificationWhenAllowed() = runTest {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        clock.set(Instant.parse("2026-10-25T10:00:00Z"))

        assertEquals(ListenableWorker.Result.success(), worker(Round.SECOND, "2026-10-25", reminderEnabled = true).doWork())

        val notification = notifications.single()
        assertEquals("Hoje é dia de votar (2º turno)", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "Leve sua cola em papel: o celular não entra na cabine.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(ReminderNotifications.CHANNEL_ID, notification.channelId)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
    }

    @Test
    fun workerStaysSilentWithoutTheNotificationPermission() = runTest {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        clock.set(Instant.parse("2026-10-25T10:00:00Z"))

        worker(Round.SECOND, "2026-10-25", reminderEnabled = true).doWork()

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun workerStaysSilentWhenTheReminderWasTurnedOff() = runTest {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        clock.set(Instant.parse("2026-10-25T10:00:00Z"))

        worker(Round.SECOND, "2026-10-25", reminderEnabled = false).doWork()

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun lateWorkAfterThePollsCloseIsDropped() = runTest {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        clock.set(Instant.parse("2026-10-25T20:30:00Z"))

        worker(Round.SECOND, "2026-10-25", reminderEnabled = true).doWork()

        assertFalse(notifications.any())
    }

    private fun worker(round: Round, date: String, reminderEnabled: Boolean): ElectionReminderWorker {
        val settings = FakeSettingsRepository(UserSettings(reminderEnabled = reminderEnabled))
        return TestListenableWorkerBuilder<ElectionReminderWorker>(context)
            .setInputData(
                workDataOf(
                    ElectionReminderWorker.KEY_ELECTION_ID to TestElections.GENERAL_2026.id,
                    ElectionReminderWorker.KEY_ROUND to round.number,
                    ElectionReminderWorker.KEY_DATE to date,
                ),
            )
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = ElectionReminderWorker(appContext, workerParameters, settings, clock)
                },
            )
            .build()
    }
}
