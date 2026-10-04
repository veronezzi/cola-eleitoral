package com.veronezzi.colaeleitoral.data.testing

import com.veronezzi.colaeleitoral.BuildConfig
import com.veronezzi.colaeleitoral.core.network.TseEndpoints
import com.veronezzi.colaeleitoral.core.network.TseHttpClients
import com.veronezzi.colaeleitoral.core.network.TseJson
import com.veronezzi.colaeleitoral.data.local.secure.BallotCipher
import com.veronezzi.colaeleitoral.data.local.secure.BallotKeyInvalidatedException
import com.veronezzi.colaeleitoral.data.remote.TseCallExecutor
import com.veronezzi.colaeleitoral.data.remote.api.DivulgaCandContasApi
import com.veronezzi.colaeleitoral.data.remote.opendata.OpenDataFileStore
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

/** Test resources (real TSE responses, see the READMEs next to them). */
object Fixtures {
    fun bytes(path: String): ByteArray =
        requireNotNull(Fixtures::class.java.classLoader?.getResourceAsStream(path)) { "Missing test resource $path" }
            .use { it.readBytes() }

    fun text(path: String): String = bytes(path).decodeToString()
}

/** A clock the test moves by hand. Zoned views follow it. */
class MutableClock(private var current: Instant, private val zoneId: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = ZonedView(zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    fun set(instant: Instant) {
        current = instant
    }

    private inner class ZonedView(private val viewZone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = viewZone

        override fun withZone(zone: ZoneId): Clock = this@MutableClock.withZone(zone)

        override fun instant(): Instant = this@MutableClock.instant()
    }
}

object TestElections {
    /** As listed by `eleicao/ordinarias` (fixture). */
    val GENERAL_2026 = Election(20322002026, 2026, "Eleição Geral Federal 2026", null, ElectionScope.GENERAL, LocalDate.of(2026, 10, 4))
    val MUNICIPAL_2024 = Election(2045202024, 2024, "Eleições Municipais 2024", null, ElectionScope.MUNICIPAL, LocalDate.of(2024, 10, 6))

    /** 10:00 in Brasília on the 2026 first round: election week, so lists have a 1 h TTL. */
    val ELECTION_DAY_MORNING: Instant = Instant.parse("2026-10-04T13:00:00Z")
}

/** ZIPs in the layout of cdn.tse.jus.br, built from the real CSV excerpts in tse-opendata/. */
object OpenDataFixtures {
    const val CANDIDATES_PATH = "/estatistica/sead/odsele/consulta_cand/consulta_cand_2026.zip"
    const val STATUS_PATH = "/estatistica/sead/odsele/consulta_cand_complementar/consulta_cand_complementar_2026.zip"
    const val CANDIDATES_ETAG = "\"30f0c0-65cf74703f687\""
    const val STATUS_ETAG = "\"13d7d9-65cf746bf8356\""

    /** Wrong layout on purpose: if the source ever read this entry, parsing would fail. */
    private val DECOY_ALL_UFS = "\"NOT_THE_EXPECTED_LAYOUT\"\r\n\"x\"\r\n".toByteArray()

    val candidatesZip: ByteArray by lazy {
        zip(
            "leiame.pdf" to "placeholder".toByteArray(),
            "consulta_cand_2026_AL.csv" to Fixtures.bytes("tse-opendata/consulta_cand_2026_AL.csv"),
            "consulta_cand_2026_BR.csv" to Fixtures.bytes("tse-opendata/consulta_cand_2026_BR.csv"),
            "consulta_cand_2026_BRASIL.csv" to DECOY_ALL_UFS,
        )
    }

    val statusZip: ByteArray by lazy {
        zip(
            "consulta_cand_complementar_2026_AL.csv" to Fixtures.bytes("tse-opendata/consulta_cand_complementar_2026_AL.csv"),
            "consulta_cand_complementar_2026_BR.csv" to Fixtures.bytes("tse-opendata/consulta_cand_complementar_2026_BR.csv"),
            "consulta_cand_complementar_2026_BRASIL.csv" to DECOY_ALL_UFS,
        )
    }

    fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}

/** MockWebServer dispatcher with prefix routes (the longest prefix wins) and per-route request logs. */
class RoutingDispatcher : Dispatcher() {
    private val routes = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()
    private val log = CopyOnWriteArrayList<RecordedRequest>()

    fun on(pathPrefix: String, response: (RecordedRequest) -> MockResponse) {
        routes[pathPrefix] = response
    }

    fun requests(pathPrefix: String): List<RecordedRequest> = log.filter { it.url.encodedPath.startsWith(pathPrefix) }

    fun count(pathPrefix: String): Int = requests(pathPrefix).size

    override fun dispatch(request: RecordedRequest): MockResponse {
        log += request
        val path = request.url.encodedPath
        val route = routes.entries.filter { path.startsWith(it.key) }.maxByOrNull { it.key.length }
        return route?.value?.invoke(request) ?: MockResponse.Builder().code(404).build()
    }
}

/** Real OkHttp/Retrofit stack of the app, pointed at a MockWebServer. */
object TestNetwork {
    const val API_PREFIX = "/divulga/rest/v1/"

    /** Shape of the Akamai refusal page the TSE sends to blocked networks. */
    const val AKAMAI_ACCESS_DENIED = "<HTML><HEAD>\n<TITLE>Access Denied</TITLE>\n</HEAD><BODY>\n<H1>Access Denied</H1>\n \n" +
        "You don't have permission to access \"http&#58;&#47;&#47;divulgacandcontas&#46;tse&#46;jus&#46;br&#47;\" " +
        "on this server.<P>\nReference&#32;&#35;18&#46;0&#46;0&#46;0\n</BODY>\n</HTML>\n"

    fun baseClient(server: MockWebServer, allowedHosts: Set<String> = setOf(server.hostName)): OkHttpClient =
        TseHttpClients.base(TseEndpoints.userAgent(BuildConfig.VERSION_NAME), allowedHosts, debugInterceptors = emptyList())

    fun api(
        server: MockWebServer,
        clock: Clock,
        readTimeoutMillis: Long = 5_000,
        allowedHosts: Set<String> = setOf(server.hostName),
    ): DivulgaCandContasApi {
        val client = TseHttpClients.api(baseClient(server, allowedHosts), clock).newBuilder()
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(server.url(API_PREFIX))
            .client(client)
            .addConverterFactory(TseJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DivulgaCandContasApi::class.java)
    }

    fun openDataStore(server: MockWebServer, clock: Clock, directory: File): OpenDataFileStore = OpenDataFileStore(
        client = TseHttpClients.openData(baseClient(server)),
        baseUrl = server.url("/estatistica/sead/odsele/"),
        directory = directory,
        executor = TseCallExecutor(random = Random(1)),
        clock = clock,
        ioDispatcher = Dispatchers.IO,
    )

    fun json(body: String, code: Int = 200): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json;charset=UTF-8")
        .body(body)
        .build()

    fun akamaiDenied(): MockResponse = MockResponse.Builder()
        .code(403)
        .addHeader("Content-Type", "text/html")
        .addHeader("Server", "AkamaiGHost")
        .body(AKAMAI_ACCESS_DENIED)
        .build()

    fun zip(bytes: ByteArray, etag: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/zip")
        .addHeader("ETag", etag)
        .body(Buffer().write(bytes))
        .build()

    /** 304 when the request carries [etag], the ZIP otherwise. */
    fun conditionalZip(request: RecordedRequest, bytes: ByteArray, etag: String): MockResponse =
        if (request.headers["If-None-Match"] == etag) MockResponse.Builder().code(304).build() else zip(bytes, etag)
}

/**
 * Software AES-256-GCM standing in for the AndroidKeyStore on the JVM. [loseKey] simulates a
 * key the Keystore dropped, [invalidate] a permanently invalidated one, [failEncryption] a
 * Keystore that cannot encrypt at all.
 */
class FakeBallotCipher : BallotCipher {
    private var key: SecretKey? = null
    private var invalidated = false
    var failEncryption = false
    val hasKey: Boolean get() = key != null

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        if (failEncryption) throw GeneralSecurityException("Keystore unavailable")
        if (invalidated) throw BallotKeyInvalidatedException()
        val secret = key ?: newKey().also { key = it }
        val iv = ByteArray(BallotCipher.IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(BallotCipher.TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(sealed: ByteArray, associatedData: ByteArray): ByteArray {
        if (invalidated) throw BallotKeyInvalidatedException()
        val secret = key ?: throw BallotKeyInvalidatedException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(BallotCipher.TAG_BITS, sealed, 0, BallotCipher.IV_SIZE))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(sealed, BallotCipher.IV_SIZE, sealed.size - BallotCipher.IV_SIZE)
    }

    override fun deleteKey() {
        key = null
        invalidated = false
    }

    fun loseKey() {
        key = null
    }

    fun invalidate() {
        invalidated = true
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}

/** In-memory [SettingsRepository]. */
class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings = state

    override suspend fun setLocation(location: VoterLocation) = state.update { it.copy(location = location) }

    override suspend fun completeOnboarding(version: Int) =
        state.update { it.copy(onboardingCompleted = true, acceptedDisclaimerVersion = version) }

    override suspend fun setReminderEnabled(enabled: Boolean) = state.update { it.copy(reminderEnabled = enabled) }

    override suspend fun setAppLockEnabled(enabled: Boolean) = state.update { it.copy(appLockEnabled = enabled) }

    override suspend fun setSecureScreens(enabled: Boolean) = state.update { it.copy(secureScreens = enabled) }

    override suspend fun clear() = state.update { UserSettings() }
}
