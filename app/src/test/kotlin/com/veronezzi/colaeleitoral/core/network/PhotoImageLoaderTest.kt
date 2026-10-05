package com.veronezzi.colaeleitoral.core.network

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.veronezzi.colaeleitoral.ColaEleitoralApplication
import com.veronezzi.colaeleitoral.data.testing.TestNetwork
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Candidate photos use the app's OkHttp (A4, S4) and never a disk cache (S2). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PhotoImageLoaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** The loader the app really builds: ColaEleitoralApplication.newImageLoader with its injected client. */
    private fun appImageLoader() = ColaEleitoralApplication()
        .apply { okHttpClient = dagger.Lazy { TestNetwork.baseClient(server) } }
        .newImageLoader(context)

    @Test
    fun photoRequestGoesThroughTheAppClientWithTheAppUserAgent() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").body(Buffer().write(PNG)).build())
        val loader = appImageLoader()

        loader.execute(ImageRequest.Builder(context).data(server.url("/divulga/rest/arquivo/img/20322002026/1/SP").toString()).build())

        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "the photo was not requested" }
        assertEquals(TestNetwork.USER_AGENT, request.headers["User-Agent"])
        assertNull("no Cookie jar", request.headers["Cookie"])
    }

    @Test
    fun photosAreCachedInMemoryOnly() {
        val loader = appImageLoader()

        assertNull(loader.diskCache)
        assertNotNull(loader.memoryCache)
    }

    @Test
    fun hostsOutsideTheTseAreNeverContacted() = runTest {
        val result = appImageLoader().execute(ImageRequest.Builder(context).data("https://example.com/photo.png").build())

        assertTrue(result is ErrorResult && result.throwable is DisallowedHostException)
        assertEquals(0, server.requestCount)
    }

    private companion object {
        /** A 1x1 transparent PNG. */
        val PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
        )
    }
}
