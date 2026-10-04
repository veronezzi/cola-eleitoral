package com.veronezzi.meusantinho.data.local.db

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CacheDaoTest {
    private lateinit var db: PublicCacheDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PublicCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun candidatesAreScopedByElectionUnitAndOffice() = runTest {
        val dao = db.candidateDao()
        dao.insertAll(listOf(candidate(1, "BR", 1), candidate(2, "SP", 6), candidate(3, "SP", 6), candidate(4, "SP", 6, ELECTION + 1)))

        assertEquals(setOf(2L, 3L), dao.observe(ELECTION, "SP", 6).first().map { it.id }.toSet())

        dao.delete(ELECTION, "SP", 6)
        assertTrue(dao.observe(ELECTION, "SP", 6).first().isEmpty())
        assertEquals(1, dao.observe(ELECTION, "BR", 1).first().size)
        assertEquals(1, dao.observe(ELECTION + 1, "SP", 6).first().size)
        assertEquals("BR", dao.get(ELECTION, 1)?.columns?.ueCode)
    }

    @Test
    fun replacingAKeyInATransactionKeepsOnlyTheNewRows() = runTest {
        val dao = db.candidateDao()
        dao.insertAll(listOf(candidate(1, "SP", 6), candidate(2, "SP", 6)))

        db.withTransaction {
            dao.delete(ELECTION, "SP", 6)
            dao.insertAll(listOf(candidate(2, "SP", 6, registration = "Indeferido"), candidate(3, "SP", 6)))
        }

        val rows = dao.observe(ELECTION, "SP", 6).first().sortedBy { it.id }
        assertEquals(listOf(2L, 3L), rows.map { it.id })
        assertEquals("Indeferido", rows.first().columns.registrationStatus)
    }

    @Test
    fun detailAndRunningMatesAreStoredTogether() = runTest {
        val dao = db.candidateDetailDao()
        dao.insert(
            CandidateDetailEntity(
                electionId = ELECTION,
                candidateId = 1,
                columns = columns("BR", 1),
                coalitionType = "Coligação",
                coalitionComposition = null,
                officialPageUrl = "https://divulgacandcontas.tse.jus.br/divulga/",
                photoPublishable = true,
                lastUpdate = "2026-09-18T14:39",
            ),
        )
        dao.insertRunningMates(listOf(mate(1, "2º Suplente"), mate(0, "1º Suplente")))

        assertEquals("Coligação", dao.observe(ELECTION, 1).first()?.coalitionType)
        assertEquals(listOf("1º Suplente", "2º Suplente"), dao.observeRunningMates(ELECTION, 1).first().map { it.role })
        dao.deleteRunningMates(ELECTION, 1)
        assertTrue(dao.observeRunningMates(ELECTION, 1).first().isEmpty())
        assertNull(dao.observe(ELECTION, 2).first())
    }

    @Test
    fun fetchStateIsUpserted() = runTest {
        val dao = db.fetchStateDao()
        dao.upsert(FetchStateEntity("elections", fetchedAt = 1, lastAttemptAt = 1, lastError = null, source = "DIVULGA_CAND_CONTAS"))
        dao.upsert(FetchStateEntity("elections", fetchedAt = 1, lastAttemptAt = 2, lastError = "network", source = "DIVULGA_CAND_CONTAS"))

        val state = dao.observe("elections").first()
        assertEquals(2L, state?.lastAttemptAt)
        assertEquals("network", state?.lastError)
        assertNull(dao.get("offices:1:SP"))
    }

    @Test
    fun electionsAndMunicipalitiesRoundTrip() = runTest {
        db.electionDao().insertAll(listOf(ElectionEntity(20322002026, 2026, "Eleição Geral Federal 2026", "GENERAL", null, "2026-10-04")))
        db.municipalityDao().insertAll(listOf(MunicipalityEntity("01120", "AC", "ACRELÂNDIA", 2045202024)))

        assertEquals(20322002026L, db.electionDao().observe(20322002026).first()?.id)
        assertEquals("01120", db.municipalityDao().observeByUf("AC").first().single().code)
        db.municipalityDao().deleteByUf("AC")
        assertTrue(db.municipalityDao().observeByUf("AC").first().isEmpty())
    }

    private fun columns(ueCode: String, officeCode: Int, registration: String = "Deferido") = CandidateColumns(
        ueCode = ueCode,
        officeCode = officeCode,
        number = 13,
        ballotName = "NOME",
        fullName = null,
        partyAcronym = "PT",
        partyNumber = 13,
        partyName = null,
        coalition = null,
        registrationStatus = registration,
        totalizationStatus = null,
        onBallotStatus = null,
        isFit = null,
        photoUrl = null,
    )

    private fun candidate(id: Long, ueCode: String, officeCode: Int, electionId: Long = ELECTION, registration: String = "Deferido") =
        CandidateEntity(electionId, id, columns(ueCode, officeCode, registration))

    private fun mate(position: Int, role: String) = RunningMateEntity(
        electionId = ELECTION,
        candidateId = 1,
        position = position,
        mateId = null,
        role = role,
        ballotName = "SUPLENTE $position",
        fullName = null,
        partyAcronym = null,
        photoUrl = null,
        status = null,
    )

    private companion object {
        const val ELECTION = 20322002026L
    }
}
