package com.veronezzi.colaeleitoral.data.remote.opendata

import com.veronezzi.colaeleitoral.data.testing.MutableClock
import com.veronezzi.colaeleitoral.data.testing.OpenDataFixtures
import com.veronezzi.colaeleitoral.data.testing.RoutingDispatcher
import com.veronezzi.colaeleitoral.data.testing.TestElections
import com.veronezzi.colaeleitoral.data.testing.TestNetwork
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration

class TseOpenDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val routes = RoutingDispatcher()
    private val clock = MutableClock(TestElections.ELECTION_DAY_MORNING)
    private lateinit var source: TseOpenDataSource

    @Before
    fun setUp() {
        server.dispatcher = routes
        server.start()
        source = TseOpenDataSource(TestNetwork.openDataStore(server, clock, temporaryFolder.newFolder("open-data")), Dispatchers.IO)
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
}
