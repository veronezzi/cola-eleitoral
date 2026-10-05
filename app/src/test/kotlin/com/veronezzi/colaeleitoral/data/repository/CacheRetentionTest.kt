package com.veronezzi.colaeleitoral.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.data.local.db.CandidateColumns
import com.veronezzi.colaeleitoral.data.local.db.CandidateEntity
import com.veronezzi.colaeleitoral.data.local.db.FetchStateEntity
import com.veronezzi.colaeleitoral.data.local.db.MunicipalityEntity
import com.veronezzi.colaeleitoral.data.local.db.OfficeEntity
import com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase
import com.veronezzi.colaeleitoral.data.mapper.toEntity
import com.veronezzi.colaeleitoral.data.remote.TseCallExecutor
import com.veronezzi.colaeleitoral.data.remote.opendata.OpenDataFileStore
import com.veronezzi.colaeleitoral.data.testing.MutableClock
import com.veronezzi.colaeleitoral.data.testing.TestElections
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 60-day retention at app start (S7): old elections go, the current one stays. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CacheRetentionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now: Instant = Instant.parse("2026-10-05T13:00:00Z")
    private val clock = MutableClock(now)
    private lateinit var db: PublicCacheDatabase
    private lateinit var openDataDir: File
    private lateinit var retention: CacheRetention

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PublicCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        openDataDir = temporaryFolder.newFolder("open-data")
        val files = OpenDataFileStore(
            client = OkHttpClient(),
            baseUrl = "https://cdn.tse.jus.br/estatistica/sead/odsele/".toHttpUrl(),
            directory = openDataDir,
            executor = TseCallExecutor(),
            clock = clock,
            ioDispatcher = Dispatchers.IO,
        )
        retention = CacheRetention(db, CachePolicy(clock), files)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun electionsNotOpenedFor60DaysAreDeletedExceptTheCurrentOne() = runTest {
        db.electionDao().insertAll(listOf(TestElections.GENERAL_2026, TestElections.MUNICIPAL_2024, GENERAL_2022).map { it.toEntity() })
        cache(TestElections.GENERAL_2026.id, lastUsedDaysAgo = 90)
        cache(GENERAL_2022.id, lastUsedDaysAgo = 61)
        cache(TestElections.MUNICIPAL_2024.id, lastUsedDaysAgo = 10)
        db.municipalityDao().insertAll(listOf(MunicipalityEntity("01120", "AC", "ACRELÂNDIA", 1, "acrelandia")))
        db.fetchStateDao().upsert(state(FetchKeys.municipalities("AC"), daysAgo = 61))
        db.municipalityDao().insertAll(listOf(MunicipalityEntity("70017", "SP", "SÃO PAULO", 1, "sao paulo")))
        db.fetchStateDao().upsert(state(FetchKeys.municipalities("SP"), daysAgo = 5))

        assertEquals(AppResult.Success(Unit), retention.cleanUp())

        assertEquals("the current election stays for election day offline", 1, candidates(TestElections.GENERAL_2026.id))
        assertEquals(1, candidates(TestElections.MUNICIPAL_2024.id))
        assertEquals(0, candidates(GENERAL_2022.id))
        assertTrue(db.officeDao().electionIds().none { it == GENERAL_2022.id })
        val keys = db.fetchStateDao().getAll().map { it.fetchKey }.toSet()
        assertFalse(keys.toString(), keys.any { it.contains(":${GENERAL_2022.id}:") })
        assertTrue(FetchKeys.candidates(TestElections.MUNICIPAL_2024.id, "SP", 1) in keys)
        assertFalse(FetchKeys.municipalities("AC") in keys)
        assertTrue(FetchKeys.municipalities("SP") in keys)
        assertEquals("the elections list itself stays", 3, db.electionDao().getAll().size)
    }

    @Test
    fun openDataCopiesNotValidatedFor60DaysAreDeleted() = runTest {
        val old = dataset("consulta_cand_2022", daysAgo = 61)
        val recent = dataset("consulta_cand_2026", daysAgo = 1)

        retention.cleanUp()

        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    private suspend fun cache(electionId: Long, lastUsedDaysAgo: Long) {
        db.candidateDao().insertAll(
            listOf(CandidateEntity(electionId, 1, CandidateColumns("SP", 1, 10, "NOME", null, "P", 10, null, null, "Deferido", null, null, null, null))),
        )
        db.officeDao().insertAll(listOf(OfficeEntity(electionId, "SP", 1, "Cargo", null)))
        db.fetchStateDao().upsert(state(FetchKeys.candidates(electionId, "SP", 1), lastUsedDaysAgo))
        db.fetchStateDao().upsert(state(FetchKeys.offices(electionId, "SP"), lastUsedDaysAgo))
    }

    private suspend fun candidates(electionId: Long) = db.candidateDao().electionIds().count { it == electionId }

    private fun state(key: String, daysAgo: Long): FetchStateEntity {
        val time = now.minus(Duration.ofDays(daysAgo)).toEpochMilli()
        return FetchStateEntity(key, fetchedAt = time, lastAttemptAt = time, lastError = null, source = null)
    }

    private fun dataset(name: String, daysAgo: Long): File = File(openDataDir, name).apply {
        mkdirs()
        File(this, "manifest").apply {
            writeText("\"etag\"\n0\n")
            setLastModified(now.minus(Duration.ofDays(daysAgo)).toEpochMilli())
        }
    }

    private companion object {
        val GENERAL_2022 = Election(2040602022, 2022, "Eleição Geral Federal 2022", null, ElectionScope.GENERAL, LocalDate.of(2022, 10, 2))
    }
}
