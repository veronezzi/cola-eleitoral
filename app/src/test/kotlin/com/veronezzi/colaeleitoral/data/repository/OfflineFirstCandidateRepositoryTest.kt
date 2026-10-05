package com.veronezzi.colaeleitoral.data.repository

import android.app.Application
import android.database.sqlite.SQLiteFullException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.data.local.db.CandidateColumns
import com.veronezzi.colaeleitoral.data.local.db.CandidateEntity
import com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase
import com.veronezzi.colaeleitoral.data.remote.CandidateSourceSelector
import com.veronezzi.colaeleitoral.data.remote.DivulgaCandContasSource
import com.veronezzi.colaeleitoral.data.remote.NetworkMonitor
import com.veronezzi.colaeleitoral.data.remote.TseCallExecutor
import com.veronezzi.colaeleitoral.data.remote.opendata.TseOpenDataSource
import com.veronezzi.colaeleitoral.data.testing.Fixtures
import com.veronezzi.colaeleitoral.data.testing.MutableClock
import com.veronezzi.colaeleitoral.data.testing.OpenDataFixtures
import com.veronezzi.colaeleitoral.data.testing.RoutingDispatcher
import com.veronezzi.colaeleitoral.data.testing.TestElections
import com.veronezzi.colaeleitoral.data.testing.TestNetwork
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.CandidateFilter
import com.veronezzi.colaeleitoral.domain.model.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Offline-first lists over Room (Robolectric) with the real network stack against MockWebServer. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OfflineFirstCandidateRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val routes = RoutingDispatcher()
    private val clock = MutableClock(TestElections.ELECTION_DAY_MORNING)
    private val policy = CachePolicy(clock)
    private val details = CandidateDetailMemoryCache()
    private lateinit var db: PublicCacheDatabase
    private lateinit var selector: CandidateSourceSelector
    private lateinit var primary: DivulgaCandContasSource
    private lateinit var repository: OfflineFirstCandidateRepository

    @Before
    fun setUp() {
        server.dispatcher = routes
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PublicCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        primary = DivulgaCandContasSource(
            TestNetwork.api(server, clock, readTimeoutMillis = 300),
            TseCallExecutor(random = Random(0)),
            Dispatchers.Default,
        )
        val fallback = TseOpenDataSource(TestNetwork.openDataStore(server, clock, temporaryFolder.newFolder()), Dispatchers.IO)
        selector = CandidateSourceSelector(primary, fallback, clock)
        repository = OfflineFirstCandidateRepository(db, selector, primary, policy, details, Dispatchers.Default)
        routes.on(OpenDataFixtures.CANDIDATES_PATH) { TestNetwork.zip(OpenDataFixtures.candidatesZip, OpenDataFixtures.CANDIDATES_ETAG) }
        routes.on(OpenDataFixtures.STATUS_PATH) { TestNetwork.zip(OpenDataFixtures.statusZip, OpenDataFixtures.STATUS_ETAG) }
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun presidents(filter: CandidateFilter = CandidateFilter()) =
        repository.observeCandidates(TestElections.GENERAL_2026.id, "BR", 1, filter)

    @Test
    fun apiListIsCachedAndShownByNumber() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json(Fixtures.text("tse/candidatos-listar.json")) }

        assertEquals(AppResult.Success(Unit), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))

        val cached = presidents().first()
        assertEquals(13, cached.value.size)
        assertEquals(listOf(12, 13, 14, 14, 15), cached.value.take(5).map { it.number })
        assertEquals(DataSource.DIVULGA_CAND_CONTAS, cached.source)
        assertEquals(clock.instant(), cached.fetchedAt)
        assertFalse(cached.isStale)
        assertNull(cached.lastError)
        assertEquals(listOf(14), presidents(CandidateFilter(query = "PTB")).first().value.map { it.number }.distinct())
    }

    @Test
    fun akamaiRefusalSwitchesToTheOpenData() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }

        assertEquals(AppResult.Success(Unit), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))

        val cached = presidents().first()
        assertEquals(DataSource.TSE_OPEN_DATA, cached.source)
        assertEquals(listOf(13, 22, 28, 28), cached.value.map { it.number })
        assertEquals("DEFERIDO", cached.value.first().status.registration)
        assertFalse(cached.isStale)
        assertNull(cached.lastError)
        assertEquals("the 403 and its single retry", 2, routes.count(TestNetwork.API_PREFIX))

        // The next list skips the refused API during the cooldown and reuses the downloaded ZIP.
        assertEquals(AppResult.Success(Unit), repository.refreshCandidates(TestElections.GENERAL_2026, "AL", 3))
        val governors = repository.observeCandidates(TestElections.GENERAL_2026.id, "AL", 3).first()
        assertEquals(listOf(15, 80), governors.value.map { it.number })
        assertEquals(DataSource.TSE_OPEN_DATA, governors.source)
        assertEquals(2, routes.count(TestNetwork.API_PREFIX))
        assertEquals(1, routes.count(OpenDataFixtures.CANDIDATES_PATH))
    }

    @Test
    fun whenBothSourcesFailTheApiRefusalIsReported() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }
        routes.on(OpenDataFixtures.CANDIDATES_PATH) { TestNetwork.akamaiDenied() }
        routes.on(OpenDataFixtures.STATUS_PATH) { TestNetwork.akamaiDenied() }

        assertEquals(AppResult.Failure(AppError.Blocked(403)), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))

        val cached = presidents().first()
        assertTrue(cached.value.isEmpty())
        assertNull(cached.fetchedAt)
        assertEquals(AppError.Blocked(403), cached.lastError)
    }

    @Test
    fun municipalElectionsDoNotFallBack() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }

        val result = repository.refreshCandidates(TestElections.MUNICIPAL_2024, "81809", 11)

        assertEquals(AppResult.Failure(AppError.Blocked(403)), result)
        assertEquals(0, routes.count(OpenDataFixtures.CANDIDATES_PATH))
    }

    @Test
    fun notFoundKeepsTheCachedListAndMarksItStale() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json(Fixtures.text("tse/candidatos-listar.json")) }
        repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1)
        val firstFetch = clock.instant()
        clock.advance(Duration.ofHours(2))
        routes.on(PRESIDENT_LIST) { MockResponse.Builder().code(404).build() }

        assertEquals(AppResult.Failure(AppError.NotFound), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))

        val cached = presidents().first()
        assertEquals(13, cached.value.size)
        assertEquals(firstFetch, cached.fetchedAt)
        assertTrue(cached.isStale)
        assertEquals(AppError.NotFound, cached.lastError)
    }

    @Test
    fun malformedJsonIsAParsingErrorWithoutData() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json("""{"candidatos":[{"id":1,""") }

        assertEquals(AppResult.Failure(AppError.Parsing), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))

        val cached = presidents().first()
        assertTrue(cached.value.isEmpty())
        assertTrue(cached.isStale)
        assertEquals(AppError.Parsing, cached.lastError)
    }

    @Test
    fun timeoutIsANetworkError() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json("{}").newBuilder().headersDelay(2, TimeUnit.SECONDS).build() }

        assertEquals(AppResult.Failure(AppError.Network), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))
        assertEquals(AppError.Network, presidents().first().lastError)
    }

    @Test
    fun freshCacheAndTheOneMinuteLimitSpareTheTse() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json(Fixtures.text("tse/candidatos-listar.json")) }
        repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1)

        clock.advance(Duration.ofMinutes(30))
        repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1)
        assertEquals("within the 1 h election-week TTL", 1, routes.count(PRESIDENT_LIST))

        repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1, force = true)
        repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1, force = true)
        assertEquals("one forced refresh per minute", 2, routes.count(PRESIDENT_LIST))
    }

    @Test
    fun detailIsCachedWithItsOwnStatusAndLink() = runTest {
        routes.on("${TestNetwork.API_PREFIX}candidatura/buscar/2026/SP/20322002026/candidato/250002539612") {
            TestNetwork.json(Fixtures.text("tse/candidato-buscar.json"))
        }

        assertEquals(
            AppResult.Success(Unit),
            repository.refreshCandidateDetail(TestElections.GENERAL_2026, "SP", 250002539612),
        )

        val cached = repository.observeCandidateDetail(TestElections.GENERAL_2026.id, 250002539612).first()
        val detail = requireNotNull(cached.value)
        assertEquals("Consta da urna", detail.candidate.status.onBallot)
        assertEquals(
            "https://divulgacandcontas.tse.jus.br/divulga/#/candidato/2026/20322002026/SP/250002539612",
            detail.officialPageUrl,
        )
        assertEquals(DataSource.DIVULGA_CAND_CONTAS, cached.source)
    }

    @Test
    fun filterOptionsListPartiesAndVerbatimStatuses() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json(Fixtures.text("tse/candidatos-listar.json")) }
        repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1)

        val options = repository.observeFilterOptions(TestElections.GENERAL_2026.id, "BR", 1).first()

        assertEquals(listOf("Cancelado", "Deferido", "Indeferido"), options.registrationStatuses)
        assertEquals(12, options.parties.size)
        assertEquals("DC", options.parties.first().acronym)
    }

    @Test
    fun fullDiskIsAStorageFailureAndKeepsTheCachedList() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json(Fixtures.text("tse/candidatos-listar.json")) }
        repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1)
        val bigList = presidentList(copies = 40)
        fillTheDisk()
        assertThrows("the DAO itself fails as on a full disk", SQLiteFullException::class.java) {
            runBlocking { db.candidateDao().insertAll(bigListEntities()) }
        }
        clock.advance(Duration.ofHours(2))
        routes.on(PRESIDENT_LIST) { TestNetwork.json(bigList) }

        assertEquals(AppResult.Failure(AppError.Storage), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))
        assertEquals("the cached list is untouched", 13, presidents().first().value.size)
    }

    @Test
    fun aFailureThatNeverReachedTheTseCanBeRetriedAtOnce() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json("{}").newBuilder().headersDelay(2, TimeUnit.SECONDS).build() }
        assertEquals(AppResult.Failure(AppError.Network), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))
        routes.on(PRESIDENT_LIST) { TestNetwork.json(Fixtures.text("tse/candidatos-listar.json")) }

        assertEquals(AppResult.Success(Unit), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1, force = true))
        assertEquals(13, presidents().first().value.size)
    }

    @Test
    fun aRefusalIsNotAskedAgainForAMinuteUnlessTheNetworkChanges() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }
        val municipal = TestElections.MUNICIPAL_2024

        assertEquals(AppResult.Failure(AppError.Blocked(403)), repository.refreshCandidates(municipal, "81809", 11))
        assertEquals(AppResult.Failure(AppError.Blocked(403)), repository.refreshCandidates(municipal, "81809", 11, force = true))
        assertEquals("the 403 and its single retry, nothing more", 2, routes.count(TestNetwork.API_PREFIX))

        NetworkMonitor(ApplicationProvider.getApplicationContext(), policy, selector).onNetworkChanged()
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.json(Fixtures.text("tse/candidatos-listar.json")) }

        assertEquals(AppResult.Success(Unit), repository.refreshCandidates(municipal, "81809", 11, force = true))
        assertEquals(3, routes.count(TestNetwork.API_PREFIX))
    }

    @Test
    fun whenTheOpenDataLayoutChangedThatErrorIsReportedAndThePrimaryIsAskedAgain() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }
        val changedLayout = OpenDataFixtures.zip("consulta_cand_2026_BR.csv" to "\"SQ_CANDIDATO\";\"OUTRA\"\r\n\"1\";\"x\"\r\n".toByteArray())
        routes.on(OpenDataFixtures.CANDIDATES_PATH) { TestNetwork.zip(changedLayout, OpenDataFixtures.CANDIDATES_ETAG) }

        assertEquals(AppResult.Failure(AppError.Parsing), repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1))
        clock.advance(Duration.ofMinutes(2))
        repository.refreshCandidates(TestElections.GENERAL_2026, "BR", 1)

        assertEquals("no 5 min pause without a working fallback", 4, routes.count(TestNetwork.API_PREFIX))
    }

    @Test
    fun detailsStayInMemoryOnly() = runTest {
        routes.on("${TestNetwork.API_PREFIX}candidatura/buscar/2026/SP/20322002026/candidato/250002539612") {
            TestNetwork.json(Fixtures.text("tse/candidato-buscar.json"))
        }
        repository.refreshCandidateDetail(TestElections.GENERAL_2026, "SP", 250002539612)
        assertNotNull(repository.observeCandidateDetail(TestElections.GENERAL_2026.id, 250002539612).first().value)

        assertTrue(db.fetchStateDao().getAll().none { it.fetchKey.startsWith("detail:") })
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertFalse(tables.toString(), tables.any { "detail" in it || "running" in it })
        val afterRestart = OfflineFirstCandidateRepository(db, selector, primary, CachePolicy(clock), CandidateDetailMemoryCache(), Dispatchers.Default)
        assertNull(afterRestart.observeCandidateDetail(TestElections.GENERAL_2026.id, 250002539612).first().value)
    }

    /** From now on the database may not grow: SQLite answers SQLITE_FULL, as on a full disk. */
    private fun fillTheDisk() {
        val sqlite = db.openHelper.writableDatabase
        val pages = sqlite.query("PRAGMA page_count").use { it.moveToFirst(); it.getLong(0) }
        sqlite.query("PRAGMA max_page_count = $pages").use { it.moveToFirst() }
    }

    /** The fixture list repeated with new ids: far more rows than fit in the free pages. */
    private fun presidentList(copies: Int): String {
        val fixture = Json.parseToJsonElement(Fixtures.text("tse/candidatos-listar.json")).jsonObject
        val items = fixture.getValue("candidatos").jsonArray
        val many = (0 until copies).flatMap { copy ->
            items.map { item ->
                val obj = item.jsonObject
                val id = obj.getValue("id").jsonPrimitive.long + copy * 1_000_000_000L
                JsonObject(obj + ("id" to JsonPrimitive(id)))
            }
        }
        return JsonObject(fixture + ("candidatos" to JsonArray(many))).toString()
    }

    private fun bigListEntities() = (1..2_000L).map { id ->
        CandidateEntity(
            electionId = TestElections.GENERAL_2026.id,
            id = id,
            columns = CandidateColumns("BR", 1, id.toInt(), "NOME $id", "NOME COMPLETO $id", "P", 10, null, null, "Deferido", null, null, null, null),
        )
    }

    private companion object {
        const val PRESIDENT_LIST = "/divulga/rest/v1/candidatura/listar/2026/BR/20322002026/1/candidatos"
    }
}
