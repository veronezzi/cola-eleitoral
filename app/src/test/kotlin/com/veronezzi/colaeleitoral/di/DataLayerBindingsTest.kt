package com.veronezzi.colaeleitoral.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.BuildConfig
import com.veronezzi.colaeleitoral.core.network.TseHttpClients
import com.veronezzi.colaeleitoral.data.local.secure.EncryptedBallotRepository
import com.veronezzi.colaeleitoral.data.local.settings.DataStoreSettingsRepository
import com.veronezzi.colaeleitoral.data.repository.OfflineFirstCandidateRepository
import com.veronezzi.colaeleitoral.data.repository.OfflineFirstElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import com.veronezzi.colaeleitoral.domain.repository.CandidateRepository
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.ReminderScheduler
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import com.veronezzi.colaeleitoral.work.WorkManagerReminderScheduler
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/** Every domain contract the UI injects resolves to the data-layer implementation. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class DataLayerBindingsTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var electionRepository: ElectionRepository

    @Inject
    lateinit var candidateRepository: CandidateRepository

    @Inject
    lateinit var ballotRepository: BallotRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    @TseApiClient
    lateinit var apiClient: OkHttpClient

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun everyContractIsBound() {
        assertTrue(electionRepository is OfflineFirstElectionRepository)
        assertTrue(candidateRepository is OfflineFirstCandidateRepository)
        assertTrue(ballotRepository is EncryptedBallotRepository)
        assertTrue(settingsRepository is DataStoreSettingsRepository)
        assertTrue(reminderScheduler is WorkManagerReminderScheduler)
    }

    @Test
    fun sharedClientFollowsTheNetworkEtiquette() {
        assertEquals(TseHttpClients.MAX_REQUESTS_PER_HOST, okHttpClient.dispatcher.maxRequestsPerHost)
        assertSame(CookieJar.NO_COOKIES, okHttpClient.cookieJar)
        assertSame("one connection pool for API, open data and photos", okHttpClient.connectionPool, apiClient.connectionPool)
        assertEquals(BuildConfig.DEBUG, okHttpClient.networkInterceptors.size > 1)
    }
}
