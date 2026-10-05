package com.veronezzi.colaeleitoral.di

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.veronezzi.colaeleitoral.core.common.ApplicationScope
import com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase
import com.veronezzi.colaeleitoral.data.local.secure.BallotCipher
import com.veronezzi.colaeleitoral.data.local.secure.BallotStateDto
import com.veronezzi.colaeleitoral.data.local.secure.BallotStore
import com.veronezzi.colaeleitoral.data.local.secure.EncryptedBallotSerializer
import com.veronezzi.colaeleitoral.data.local.secure.KeystoreBallotCipher
import com.veronezzi.colaeleitoral.data.local.settings.DataStoreSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import androidx.datastore.core.DataStore
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    /**
     * Public cache only: known schema changes migrate (keeping the lists for offline use), any
     * other change (a downgrade, for example) drops it and downloads again.
     */
    @Provides
    @Singleton
    fun publicCacheDatabase(@ApplicationContext context: Context): PublicCacheDatabase =
        Room.databaseBuilder(context, PublicCacheDatabase::class.java, PublicCacheDatabase.NAME)
            .addMigrations(PublicCacheDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(DataStoreSettingsRepository.FILE_NAME) },
    )

    @Provides
    @Singleton
    fun ballotCipher(): BallotCipher = KeystoreBallotCipher()

    /**
     * The picks: AES-256-GCM file in `noBackupFilesDir` (never in Auto Backup or device transfer).
     * A file that can never be read again is replaced by an empty state flagged `picksLost`
     * (shown once by the UI); a transient failure leaves the file alone ([EncryptedBallotSerializer]).
     */
    @Provides
    @Singleton
    fun ballotStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        cipher: BallotCipher,
    ): BallotStore {
        val file = File(context.noBackupFilesDir, EncryptedBallotSerializer.FILE_NAME)
        val dataStore: DataStore<BallotStateDto> = DataStoreFactory.create(
            serializer = EncryptedBallotSerializer(cipher),
            corruptionHandler = ReplaceFileCorruptionHandler { BallotStateDto(picksLost = true) },
            scope = scope,
            produceFile = { file },
        )
        return BallotStore(dataStore = dataStore, cipher = cipher, file = file)
    }
}
