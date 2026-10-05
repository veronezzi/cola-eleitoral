package com.veronezzi.colaeleitoral.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.veronezzi.colaeleitoral.domain.model.normalizeForSearch

/**
 * Cache of public TSE data (`public_cache.db`). Everything here can be downloaded again, so an
 * unknown schema change may migrate destructively; schemas are exported to `app/schemas`.
 *
 * Version 2 drops the detail tables (details now live only in memory, S2) and adds
 * `municipalities.searchKey`, keeping the cached lists so an update works offline.
 */
@Database(
    entities = [
        ElectionEntity::class,
        MunicipalityEntity::class,
        OfficeEntity::class,
        CandidateEntity::class,
        FetchStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PublicCacheDatabase : RoomDatabase() {
    abstract fun electionDao(): ElectionDao

    abstract fun municipalityDao(): MunicipalityDao

    abstract fun officeDao(): OfficeDao

    abstract fun candidateDao(): CandidateDao

    abstract fun fetchStateDao(): FetchStateDao

    companion object {
        const val NAME = "public_cache.db"

        /**
         * 1 → 2: candidate details and running mates are no longer persisted, so their tables and
         * their `detail:` freshness rows go away (the detail a voter opened hints at the pick).
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `candidate_details`")
                db.execSQL("DROP TABLE IF EXISTS `running_mates`")
                db.execSQL("DELETE FROM `fetch_state` WHERE substr(`fetchKey`, 1, 7) = 'detail:'")
                db.execSQL("ALTER TABLE `municipalities` ADD COLUMN `searchKey` TEXT NOT NULL DEFAULT ''")
                val names = buildList {
                    db.query("SELECT `code`, `name` FROM `municipalities`").use { cursor ->
                        while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                    }
                }
                for ((code, name) in names) {
                    db.execSQL("UPDATE `municipalities` SET `searchKey` = ? WHERE `code` = ?", arrayOf(normalizeForSearch(name), code))
                }
            }
        }
    }
}
