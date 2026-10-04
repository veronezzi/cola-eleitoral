package com.veronezzi.colaeleitoral.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Cache of public TSE data (`public_cache.db`). Everything here can be downloaded again, so
 * schema changes may migrate destructively; schemas are exported to `app/schemas`.
 */
@Database(
    entities = [
        ElectionEntity::class,
        MunicipalityEntity::class,
        OfficeEntity::class,
        CandidateEntity::class,
        CandidateDetailEntity::class,
        RunningMateEntity::class,
        FetchStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PublicCacheDatabase : RoomDatabase() {
    abstract fun electionDao(): ElectionDao

    abstract fun municipalityDao(): MunicipalityDao

    abstract fun officeDao(): OfficeDao

    abstract fun candidateDao(): CandidateDao

    abstract fun candidateDetailDao(): CandidateDetailDao

    abstract fun fetchStateDao(): FetchStateDao

    companion object {
        const val NAME = "public_cache.db"
    }
}
