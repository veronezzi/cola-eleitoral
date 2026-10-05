package com.veronezzi.colaeleitoral.data.remote

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.data.remote.opendata.TseOpenDataSource
import com.veronezzi.colaeleitoral.data.repository.CachePolicy
import com.veronezzi.colaeleitoral.data.testing.MutableClock
import com.veronezzi.colaeleitoral.data.testing.TestElections
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.DataSource
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import java.time.Duration

/** A new default network lifts the refresh limit of failed keys (A3). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NetworkMonitorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = MutableClock(TestElections.ELECTION_DAY_MORNING)
    private val policy = CachePolicy(clock)
    private val selector = CandidateSourceSelector(mockk<DivulgaCandContasSource>(), mockk<TseOpenDataSource>(), clock)
    private val monitor = NetworkMonitor(context, policy, selector)

    @Test
    fun registersForTheDefaultNetworkOnce() {
        monitor.start()
        monitor.start()

        val connectivity = shadowOf(context.getSystemService(ConnectivityManager::class.java))
        assertEquals(1, connectivity.networkCallbacks.count { it === monitor.callback })
    }

    @Test
    fun switchingNetworksLetsAFailedKeyBeFetchedAgain() {
        val ttl = Duration.ofHours(1)
        // Registering reports the current network first.
        monitor.callback.onAvailable(ShadowNetwork.newInstance(1))
        clock.advance(Duration.ofSeconds(1))
        val refused = policy.failure(KEY, previous = null, error = AppError.Blocked(403))
        assertEquals(AppResult.Failure(AppError.Blocked(403)), policy.resultWithoutFetch(refused, ttl, force = true))

        monitor.callback.onAvailable(ShadowNetwork.newInstance(1))
        assertEquals("the same network is no change", AppResult.Failure(AppError.Blocked(403)), policy.resultWithoutFetch(refused, ttl, force = true))

        clock.advance(Duration.ofSeconds(5))
        monitor.callback.onAvailable(ShadowNetwork.newInstance(2))

        assertNull(policy.resultWithoutFetch(refused, ttl, force = true))
        val fresh = policy.success(KEY, DataSource.DIVULGA_CAND_CONTAS)
        assertTrue(policy.resultWithoutFetch(fresh, ttl, force = true) is AppResult.Success)
    }

    private companion object {
        const val KEY = "candidates:20322002026:BR:1"
    }
}
