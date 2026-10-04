package com.veronezzi.meusantinho.di

import com.veronezzi.meusantinho.data.local.secure.EncryptedBallotRepository
import com.veronezzi.meusantinho.data.local.settings.DataStoreSettingsRepository
import com.veronezzi.meusantinho.data.repository.OfflineFirstCandidateRepository
import com.veronezzi.meusantinho.data.repository.OfflineFirstElectionRepository
import com.veronezzi.meusantinho.domain.repository.BallotRepository
import com.veronezzi.meusantinho.domain.repository.CandidateRepository
import com.veronezzi.meusantinho.domain.repository.ElectionRepository
import com.veronezzi.meusantinho.domain.repository.ReminderScheduler
import com.veronezzi.meusantinho.domain.repository.SettingsRepository
import com.veronezzi.meusantinho.work.WorkManagerReminderScheduler
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
