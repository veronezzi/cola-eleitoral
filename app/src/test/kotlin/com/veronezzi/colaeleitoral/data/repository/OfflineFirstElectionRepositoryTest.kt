package com.veronezzi.colaeleitoral.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase
import com.veronezzi.colaeleitoral.data.remote.CandidateSourceSelector
import com.veronezzi.colaeleitoral.data.remote.DivulgaCandContasSource
import com.veronezzi.colaeleitoral.data.remote.TseCallExecutor
import com.veronezzi.colaeleitoral.data.remote.opendata.TseOpenDataSource
import com.veronezzi.colaeleitoral.data.testing.Fixtures
import com.veronezzi.colaeleitoral.data.testing.MutableClock
import com.veronezzi.colaeleitoral.data.testing.RoutingDispatcher
import com.veronezzi.colaeleitoral.data.testing.TestElections
import com.veronezzi.colaeleitoral.data.testing.TestNetwork
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OfflineFirstElectionRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val routes = RoutingDispatcher()
    private val clock = MutableClock(TestElections.ELECTION_DAY_MORNING)
    private lateinit var db: PublicCacheDatabase
    private lateinit var repository: OfflineFirstElectionRepository

    @Before
    fun setUp() {
        server.dispatcher = routes
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PublicCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val primary = DivulgaCandContasSource(TestNetwork.api(server, clock), TseCallExecutor(random = Random(0)), Dispatchers.Default)
        val openData = TestNetwork.openDataStore(server, clock, temporaryFolder.newFolder())
        val selector = CandidateSourceSelector(primary, TseOpenDataSource(openData, Dispatchers.IO), clock)
        repository = OfflineFirstElectionRepository(
            db, primary, selector, openData, CandidateDetailMemoryCache(), CachePolicy(clock), Dispatchers.IO, Dispatchers.Default,
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun electionsAreCachedNewestFirst() = runTest {
        routes.on(ELECTIONS) { TestNetwork.json(Fixtures.text("tse/eleicoes-ordinarias.json")) }

        assertEquals(AppResult.Success(Unit), repository.refreshElections())

        val cached = repository.observeElections().first()
        assertEquals(13, cached.value.size)
        assertEquals(TestElections.GENERAL_2026, cached.value.first())
        assertEquals(listOf(2026, 2024, 2022, 2020, 2020, 2018), cached.value.take(6).map { it.year })
        assertEquals("15/11 before the 14/11 AP election", 2030402020L, cached.value[3].id)
        assertFalse(cached.isStale)
    }

    @Test
    fun blockedFirstRunFallsBackToTheBundledSnapshot() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }

        assertEquals(AppResult.Failure(AppError.Blocked(403)), repository.refreshElections())

        val cached = repository.observeElections().first()
        assertEquals(13, cached.value.size)
        assertEquals(TestElections.GENERAL_2026, cached.value.first())
        assertEquals(ElectionSeed.CAPTURED_AT, cached.fetchedAt)
        assertTrue(cached.isStale)
        assertEquals(AppError.Blocked(403), cached.lastError)
    }

    @Test
    fun snapshotIsNotUsedOnceItIsOutdated() {
        assertTrue(ElectionSeed.electionsFor(java.time.LocalDate.of(2026, 11, 24)).isNotEmpty())
        assertTrue(ElectionSeed.electionsFor(java.time.LocalDate.of(2027, 1, 1)).isEmpty())
    }

    @Test
    fun ballotFallsBackToTheOfficesDefinedByLaw() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }
        val location = VoterLocation("AL")

        assertEquals(AppResult.Failure(AppError.Blocked(403)), repository.refreshBallotOffices(TestElections.GENERAL_2026, location))

        val ballot = repository.observeBallotOffices(TestElections.GENERAL_2026, location, Round.FIRST).first()
        assertEquals(listOf(6, 7, 5, 3, 1), ballot.value.map { it.code })
        assertEquals(2, ballot.value.single { it.code == 5 }.maxPicks)
        assertEquals("BR", ballot.value.single { it.code == 1 }.ueCode)
        assertEquals("AL", ballot.value.single { it.code == 3 }.ueCode)
        assertNull(ballot.fetchedAt)
        assertTrue(ballot.isStale)
    }

    @Test
    fun ballotUsesTheOfficesListedByTheTse() = runTest {
        routes.on("${TestNetwork.API_PREFIX}eleicao/listar/municipios/20322002026/BR/cargos") { TestNetwork.json(CARGOS_BR) }
        routes.on("${TestNetwork.API_PREFIX}eleicao/listar/municipios/20322002026/DF/cargos") { TestNetwork.json(CARGOS_DF) }
        val location = VoterLocation("DF")

        assertEquals(AppResult.Success(Unit), repository.refreshBallotOffices(TestElections.GENERAL_2026, location))

        val ballot = repository.observeBallotOffices(TestElections.GENERAL_2026, location, Round.FIRST).first()
        assertEquals(listOf(6, 8, 5, 3, 1), ballot.value.map { it.code })
        assertEquals(listOf(172, 433, 13, 11, 14), ballot.value.map { it.candidateCount })
        assertEquals(clock.instant(), ballot.fetchedAt)
        assertFalse(ballot.isStale)
        val runoff = repository.observeBallotOffices(TestElections.GENERAL_2026, location, Round.SECOND).first()
        assertEquals(listOf(3, 1), runoff.value.map { it.code })
    }

    @Test
    fun voterAbroadHasOnlyThePresident() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }

        val ballot = repository.observeBallotOffices(
            TestElections.GENERAL_2026,
            VoterLocation(ElectoralUnit.ABROAD_CODE),
            Round.FIRST,
        ).first()

        assertEquals(listOf(1), ballot.value.map { it.code })
    }

    @Test
    fun municipalitiesComeFromTheLatestMunicipalElection() = runTest {
        routes.on(ELECTIONS) { TestNetwork.json(Fixtures.text("tse/eleicoes-ordinarias.json")) }
        routes.on("${TestNetwork.API_PREFIX}eleicao/buscar/AC/2045202024/municipios") {
            TestNetwork.json(Fixtures.text("tse/municipios.json"))
        }

        assertEquals(AppResult.Success(Unit), repository.refreshMunicipalities("AC"))

        val municipalities = repository.observeMunicipalities("AC").first().value
        assertEquals("ACRELÂNDIA", municipalities.first().name)
        assertEquals("01120", municipalities.first().code)
        assertTrue(municipalities.all { it.uf == "AC" && it.isMunicipality })
    }

    @Test
    fun clearCacheDeletesEverything() = runTest {
        routes.on(ELECTIONS) { TestNetwork.json(Fixtures.text("tse/eleicoes-ordinarias.json")) }
        repository.refreshElections()

        repository.clearCache()

        val cached = repository.observeElections().first()
        assertTrue(cached.value.isEmpty())
        assertNull(cached.fetchedAt)
    }

    private companion object {
        const val ELECTIONS = "/divulga/rest/v1/eleicao/ordinarias"

        // E3 payloads in the documented format (ARCHITECTURE.md 2.2; no archived E3 response
        // exists). The counts are the 2026 open-data numbers for BR and DF.
        const val CARGOS_BR = """{"unidadeEleitoralDTO":{"codigo":"BR","nome":"BRASIL","sigla":"BR"},
            "cargos":[{"codigo":1,"nome":"Presidente","contagem":14},{"codigo":2,"nome":"Vice-presidente","contagem":14}]}"""
        const val CARGOS_DF = """{"unidadeEleitoralDTO":{"codigo":"DF","nome":"DISTRITO FEDERAL","sigla":"DF"},
            "cargos":[{"codigo":3,"nome":"Governador","contagem":11},{"codigo":4,"nome":"Vice-governador","contagem":11},
            {"codigo":5,"nome":"Senador","contagem":13},{"codigo":6,"nome":"Deputado Federal","contagem":172},
            {"codigo":8,"nome":"Deputado Distrital","contagem":433},{"codigo":9,"nome":"1º Suplente","contagem":13},
            {"codigo":10,"nome":"2º Suplente","contagem":15}]}"""
    }
}
