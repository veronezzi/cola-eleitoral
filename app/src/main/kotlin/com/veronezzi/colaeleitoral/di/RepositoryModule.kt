package com.veronezzi.colaeleitoral.di

import com.veronezzi.colaeleitoral.data.local.secure.EncryptedBallotRepository
import com.veronezzi.colaeleitoral.data.local.settings.DataStoreSettingsRepository
import com.veronezzi.colaeleitoral.data.repository.OfflineFirstCandidateRepository
import com.veronezzi.colaeleitoral.data.repository.OfflineFirstElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import com.veronezzi.colaeleitoral.domain.repository.CandidateRepository
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.ReminderScheduler
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import com.veronezzi.colaeleitoral.work.WorkManagerReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Every domain repository and the reminder scheduler, bound to the data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun electionRepository(impl: OfflineFirstElectionRepository): ElectionRepository

    @Binds
    abstract fun candidateRepository(impl: OfflineFirstCandidateRepository): CandidateRepository

    @Binds
    abstract fun ballotRepository(impl: EncryptedBallotRepository): BallotRepository

    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun reminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler
}
