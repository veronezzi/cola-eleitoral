package com.veronezzi.meusantinho.di

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.veronezzi.meusantinho.core.common.ApplicationScope
import com.veronezzi.meusantinho.data.local.db.PublicCacheDatabase
import com.veronezzi.meusantinho.data.local.secure.BallotCipher
import com.veronezzi.meusantinho.data.local.secure.BallotStateDto
import com.veronezzi.meusantinho.data.local.secure.BallotStore
import com.veronezzi.meusantinho.data.local.secure.EncryptedBallotSerializer
import com.veronezzi.meusantinho.data.local.secure.KeystoreBallotCipher
import com.veronezzi.meusantinho.data.local.settings.DataStoreSettingsRepository
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
    /** Public cache only: rebuildable, so schema changes drop and re-download. */
    @Provides
    @Singleton
    fun publicCacheDatabase(@ApplicationContext context: Context): PublicCacheDatabase =
        Room.databaseBuilder(context, PublicCacheDatabase::class.java, PublicCacheDatabase.NAME)
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
     * An unreadable file is replaced by an empty state flagged `picksLost` (shown once by the UI).
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
