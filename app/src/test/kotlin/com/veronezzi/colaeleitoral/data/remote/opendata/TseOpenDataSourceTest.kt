package com.veronezzi.colaeleitoral.data.remote.opendata

import com.veronezzi.colaeleitoral.data.testing.Fixtures
import com.veronezzi.colaeleitoral.data.testing.MutableClock
import com.veronezzi.colaeleitoral.data.testing.OpenDataFixtures
import com.veronezzi.colaeleitoral.data.testing.RoutingDispatcher
import com.veronezzi.colaeleitoral.data.testing.TestElections
import com.veronezzi.colaeleitoral.data.testing.TestNetwork
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
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
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis

class TseOpenDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val routes = RoutingDispatcher()
    private val clock = MutableClock(TestElections.ELECTION_DAY_MORNING)
    private lateinit var directory: File
    private lateinit var store: OpenDataFileStore
    private lateinit var source: TseOpenDataSource

    @Before
    fun setUp() {
        server.dispatcher = routes
        server.start()
        directory = temporaryFolder.newFolder("open-data")
        store = TestNetwork.openDataStore(server, clock, directory)
        source = TseOpenDataSource(store, Dispatchers.IO)
        routes.on(OpenDataFixtures.CANDIDATES_PATH) {
            TestNetwork.conditionalZip(it, OpenDataFixtures.candidatesZip, OpenDataFixtures.CANDIDATES_ETAG)
        }
        routes.on(OpenDataFixtures.STATUS_PATH) {
            TestNetwork.conditionalZip(it, OpenDataFixtures.statusZip, OpenDataFixtures.STATUS_ETAG)
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun downloadsOnceAndRevalidatesWithEtag() = runTest {
        val governors = source.fetchCandidates(TestElections.GENERAL_2026, "AL", 3)
        assertEquals(listOf(15, 80), (governors as AppResult.Success).value.map { it.number }.sorted())

        // Within the revalidation window the local copy is used as is.
        source.fetchCandidates(TestElections.GENERAL_2026, "AL", 5)
        assertEquals(1, routes.count(OpenDataFixtures.CANDIDATES_PATH))

        clock.advance(Duration.ofMinutes(10))
        val senators = source.fetchCandidates(TestElections.GENERAL_2026, "AL", 5)

        assertEquals(listOf(151, 156, 456), (senators as AppResult.Success).value.map { it.number }.sorted())
        val requests = routes.requests(OpenDataFixtures.CANDIDATES_PATH)
        assertEquals(2, requests.size)
        assertNull(requests[0].headers["If-None-Match"])
        assertEquals(OpenDataFixtures.CANDIDATES_ETAG, requests[1].headers["If-None-Match"])
        assertEquals(OpenDataFixtures.STATUS_ETAG, routes.requests(OpenDataFixtures.STATUS_PATH).last().headers["If-None-Match"])
    }

    @Test
    fun readsOnlyTheRequestedUnit() = runTest {
        // The ZIP also holds BR and a decoy "BRASIL" entry that would fail to parse if read.
        val presidents = source.fetchCandidates(TestElections.GENERAL_2026, "BR", 1)
        val alPresidents = source.fetchCandidates(TestElections.GENERAL_2026, "AL", 1)

        assertEquals(listOf(13, 22, 28, 28), (presidents as AppResult.Success).value.map { it.number }.sorted())
        assertEquals(AppResult.Success(emptyList<Any>()), alPresidents)
    }

    @Test
    fun unknownUnitIsNotFound() = runTest {
        assertEquals(AppResult.Failure(AppError.NotFound), source.fetchCandidates(TestElections.GENERAL_2026, "SP", 3))
    }

    @Test
    fun refusedDownloadIsBlockedAfterOneRetry() = runTest {
        routes.on(OpenDataFixtures.CANDIDATES_PATH) { TestNetwork.akamaiDenied() }

        assertEquals(AppResult.Failure(AppError.Blocked(403)), source.fetchCandidates(TestElections.GENERAL_2026, "AL", 3))
        assertEquals(2, routes.count(OpenDataFixtures.CANDIDATES_PATH))
    }

    @Test
    fun withoutTheComplementaryFileTheListStillLoads() = runTest {
        routes.on(OpenDataFixtures.STATUS_PATH) { MockResponse.Builder().code(404).build() }

        val governors = source.fetchCandidates(TestElections.GENERAL_2026, "AL", 3) as AppResult.Success

        assertEquals(2, governors.value.size)
        assertEquals(listOf("", ""), governors.value.map { it.status.registration })
    }

    @Test
    fun municipalElectionsAreNotServed() = runTest {
        assertEquals(AppResult.Failure(AppError.NotFound), source.fetchCandidates(TestElections.MUNICIPAL_2024, "81809", 11))
        assertEquals(0, routes.count("/"))
    }

    @Test
    fun refreshKeepsNoZipAndNoPersonalDataOnDisk() = runTest {
        source.fetchCandidates(TestElections.GENERAL_2026, "AL", 3)
        source.fetchCandidates(TestElections.GENERAL_2026, "BR", 1)

        val files = directory.walkTopDown().filter { it.isFile }.toList()
        assertTrue(files.isNotEmpty())
        assertTrue(files.toString(), files.none { it.name.endsWith(".zip") || it.name.endsWith(".part") || it.name.endsWith(".etag") })
        val derived = files.filter { it.name.endsWith(".gz") }
        assertEquals(
            setOf("consulta_cand_2026_al.csv.gz", "consulta_cand_2026_br.csv.gz", "consulta_cand_complementar_2026_al.csv.gz", "consulta_cand_complementar_2026_br.csv.gz"),
            derived.map { it.name }.toSet(),
        )
        val text = derived.joinToString("\n") { file -> GZIPInputStream(file.inputStream()).use { it.readBytes().decodeToString() } }
        val original = Fixtures.text("tse-opendata/consulta_cand_2026_AL.csv")
        for (column in listOf("NR_CPF_CANDIDATO", "NR_TITULO_ELEITORAL_CANDIDATO", "DS_EMAIL", "DT_NASCIMENTO", "DS_GENERO", "DS_COR_RACA")) {
            assertTrue("the TSE file has $column", original.contains(column))
            assertFalse(column, text.contains(column))
        }
        assertTrue("the derived files keep the public columns", text.contains("NM_URNA_CANDIDATO"))
    }

    @Test
    fun zipsKeptByOlderVersionsAreDeletedAtStartup() = runTest {
        source.fetchCandidates(TestElections.GENERAL_2026, "AL", 3)
        val legacy = listOf("consulta_cand_2026.zip", "consulta_cand_2026.zip.etag", "consulta_cand_2026.zip.part").map { File(directory, it) }
        legacy.forEach { it.writeText("old") }

        store.deleteLegacyFiles()

        assertTrue(legacy.none { it.exists() })
        assertTrue(source.fetchCandidates(TestElections.GENERAL_2026, "AL", 5) is AppResult.Success)
        assertEquals("the derived data is kept", 1, routes.count(OpenDataFixtures.CANDIDATES_PATH))
    }

    @Test
    fun clearDoesNotWaitForASlowDownload() = runBlocking {
        routes.on(OpenDataFixtures.CANDIDATES_PATH) {
            TestNetwork.zip(OpenDataFixtures.candidatesZip, OpenDataFixtures.CANDIDATES_ETAG).newBuilder()
                .bodyDelay(30, TimeUnit.SECONDS)
                .build()
        }
        val fetch = async(Dispatchers.IO) { source.fetchCandidates(TestElections.GENERAL_2026, "AL", 3) }
        withTimeout(10_000) { while (routes.count(OpenDataFixtures.CANDIDATES_PATH) == 0) delay(20) }
        delay(200)

        val clearTime = measureTimeMillis { store.clear() }

        assertTrue("clear took $clearTime ms", clearTime < 2_000)
        assertEquals(AppResult.Failure(AppError.Unknown(null)), withTimeout(5_000) { fetch.await() })
        assertTrue(directory.walkTopDown().none { it.isFile })
    }
}
