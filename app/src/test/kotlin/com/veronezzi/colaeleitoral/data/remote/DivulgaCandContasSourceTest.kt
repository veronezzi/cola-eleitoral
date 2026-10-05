package com.veronezzi.colaeleitoral.data.remote

import com.veronezzi.colaeleitoral.BuildConfig
import com.veronezzi.colaeleitoral.core.network.parseRetryAfterSeconds
import com.veronezzi.colaeleitoral.data.testing.Fixtures
import com.veronezzi.colaeleitoral.data.testing.MutableClock
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** The real OkHttp + Retrofit stack of the app against MockWebServer. */
class DivulgaCandContasSourceTest {
    private val server = MockWebServer()
    private val routes = RoutingDispatcher()
    private val clock = MutableClock(TestElections.ELECTION_DAY_MORNING)
    private lateinit var source: DivulgaCandContasSource

    @Before
    fun setUp() {
        server.dispatcher = routes
        server.start()
        source = sourceFor(allowedHosts = setOf(server.hostName))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun sourceFor(allowedHosts: Set<String>) = DivulgaCandContasSource(
        api = TestNetwork.api(server, clock, readTimeoutMillis = 300, allowedHosts = allowedHosts),
        executor = TseCallExecutor(random = Random(0)),
        defaultDispatcher = Dispatchers.Default,
    )

    @Test
    fun electionsAreFetchedWithAnHonestUserAgentAndNoCookies() = runTest {
        routes.on(ELECTIONS) {
            TestNetwork.json(Fixtures.text("tse/eleicoes-ordinarias.json")).newBuilder()
                .addHeader("Set-Cookie", "ak_bmsc=tracking; Path=/")
                .build()
        }

        val first = source.fetchElections()
        source.fetchElections()

        assertEquals(13, (first as AppResult.Success).value.size)
        val requests = routes.requests(ELECTIONS)
        assertEquals(
            "ColaEleitoral/${BuildConfig.VERSION_NAME} (Android 14; +${BuildConfig.PRIVACY_POLICY_URL})",
            requests[0].headers["User-Agent"],
        )
        assertEquals("application/json", requests[0].headers["Accept"])
        assertNull("no cookie jar", requests[1].headers["Cookie"])
        assertNull(requests[0].headers["Referer"])
    }

    @Test
    fun candidateListMapsTheRealFixture() = runTest {
        routes.on(PRESIDENT_LIST) { TestNetwork.json(Fixtures.text("tse/candidatos-listar.json")) }

        val result = source.fetchCandidates(TestElections.GENERAL_2026, "BR", 1)

        assertEquals(13, (result as AppResult.Success).value.size)
        assertEquals(PRESIDENT_LIST, routes.requests(PRESIDENT_LIST).single().url.encodedPath)
    }

    @Test
    fun akamaiAccessDeniedIsBlockedAfterASingleRetry() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.akamaiDenied() }

        assertEquals(AppResult.Failure(AppError.Blocked(403)), source.fetchCandidates(TestElections.GENERAL_2026, "BR", 1))
        assertEquals(2, routes.count(TestNetwork.API_PREFIX))
    }

    @Test
    fun htmlChallengeWithStatus200IsBlocked() = runTest {
        routes.on(ELECTIONS) {
            MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body("<html>challenge</html>").build()
        }

        assertEquals(AppResult.Failure(AppError.Blocked(null)), source.fetchElections())
    }

    @Test
    fun notFoundIsNotRetried() = runTest {
        routes.on(TestNetwork.API_PREFIX) { MockResponse.Builder().code(404).build() }

        assertEquals(AppResult.Failure(AppError.NotFound), source.fetchCandidates(TestElections.GENERAL_2026, "BR", 1))
        assertEquals(1, routes.count(TestNetwork.API_PREFIX))
    }

    @Test
    fun unpublishedCandidacyAnswersEmptyAndIsNotFound() = runTest {
        routes.on(TestNetwork.API_PREFIX) { MockResponse.Builder().code(200).body("").build() }

        val result = source.fetchCandidateDetail(TestElections.GENERAL_2026, "SP", 250002539612, officeCodeHint = 6)

        assertEquals(AppResult.Failure(AppError.NotFound), result)
    }

    @Test
    fun detailMapsTheRealFixture() = runTest {
        routes.on("${TestNetwork.API_PREFIX}candidatura/buscar/2026/SP/20322002026/candidato/250002539612") {
            TestNetwork.json(Fixtures.text("tse/candidato-buscar.json"))
        }

        val detail = source.fetchCandidateDetail(TestElections.GENERAL_2026, "SP", 250002539612, officeCodeHint = null)

        assertEquals("ERIKA HILTON", (detail as AppResult.Success).value.candidate.ballotName)
    }

    @Test
    fun malformedJsonIsAParsingError() = runTest {
        routes.on(TestNetwork.API_PREFIX) { TestNetwork.json("""{"unidadeEleitoral":{"codigo":"BR"},"candidatos":[{"id":""") }

        assertEquals(AppResult.Failure(AppError.Parsing), source.fetchCandidates(TestElections.GENERAL_2026, "BR", 1))
        assertEquals(1, routes.count(TestNetwork.API_PREFIX))
    }

    @Test
    fun timeoutsAreNetworkErrorsAfterThreeAttempts() = runTest {
        routes.on(TestNetwork.API_PREFIX) {
            TestNetwork.json("{}").newBuilder().headersDelay(2, TimeUnit.SECONDS).build()
        }

        assertEquals(AppResult.Failure(AppError.Network), source.fetchElections())
        assertEquals(3, routes.count(TestNetwork.API_PREFIX))
    }

    @Test
    fun serverErrorsAreRetriedWithBackoff() = runTest {
        val calls = AtomicInteger()
        routes.on(ELECTIONS) {
            if (calls.getAndIncrement() == 0) {
                MockResponse.Builder().code(503).build()
            } else {
                TestNetwork.json(Fixtures.text("tse/eleicoes-ordinarias.json"))
            }
        }

        assertTrue(source.fetchElections() is AppResult.Success)
        assertEquals(2, routes.count(ELECTIONS))
    }

    @Test
    fun persistentServerErrorsBecomeServerError() = runTest {
        routes.on(TestNetwork.API_PREFIX) { MockResponse.Builder().code(502).build() }

        assertEquals(AppResult.Failure(AppError.Server(502)), source.fetchElections())
        assertEquals(3, routes.count(TestNetwork.API_PREFIX))
    }

    @Test
    fun rateLimitBeyondTenSecondsIsNotWaitedFor() = runTest {
        routes.on(TestNetwork.API_PREFIX) { MockResponse.Builder().code(429).addHeader("Retry-After", "120").build() }

        assertEquals(AppResult.Failure(AppError.Blocked(429)), source.fetchElections())
        assertEquals(1, routes.count(TestNetwork.API_PREFIX))
    }

    @Test
    fun hostsOutsideTheAllowlistNeverReachTheNetwork() = runTest {
        val restricted = sourceFor(allowedHosts = setOf("divulgacandcontas.tse.jus.br"))

        assertEquals(AppResult.Failure(AppError.Unknown(null)), restricted.fetchElections())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun retryAfterAcceptsSecondsAndHttpDates() {
        val now = Instant.parse("2026-10-04T13:00:00Z")

        assertEquals(5L, parseRetryAfterSeconds("5", now))
        assertEquals(30L, parseRetryAfterSeconds("Sun, 04 Oct 2026 13:00:30 GMT", now))
        assertNull(parseRetryAfterSeconds("soon", now))
    }

    private companion object {
        const val ELECTIONS = "/divulga/rest/v1/eleicao/ordinarias"
        const val PRESIDENT_LIST = "/divulga/rest/v1/candidatura/listar/2026/BR/20322002026/1/candidatos"
    }
}
