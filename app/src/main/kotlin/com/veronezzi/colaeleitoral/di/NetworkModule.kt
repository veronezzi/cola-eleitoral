package com.veronezzi.colaeleitoral.di

import android.content.Context
import android.os.Build
import com.veronezzi.colaeleitoral.BuildConfig
import com.veronezzi.colaeleitoral.core.common.IoDispatcher
import com.veronezzi.colaeleitoral.core.network.BuildTypeNetworkInterceptors
import com.veronezzi.colaeleitoral.core.network.TseEndpoints
import com.veronezzi.colaeleitoral.core.network.TseHttpClients
import com.veronezzi.colaeleitoral.core.network.TseJson
import com.veronezzi.colaeleitoral.data.remote.TseCallExecutor
import com.veronezzi.colaeleitoral.data.remote.api.DivulgaCandContasApi
import com.veronezzi.colaeleitoral.data.remote.opendata.OpenDataFileStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** OkHttp client of the DivulgaCandContas JSON API (Accept JSON + refusal guard). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TseApiClient

/** OkHttp client of the TSE open-data downloads (longer call timeout). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenDataClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val OPEN_DATA_DIRECTORY = "tse-open-data"

    /**
     * Base client: app User-Agent, TSE host allowlist, no cookies, 4 requests per host, request
     * lines logged in debug builds only. Unqualified: the photo loader (Coil) uses it too, through
     * `ColaEleitoralApplication.newImageLoader`.
     */
    @Provides
    @Singleton
    fun baseOkHttpClient(): OkHttpClient = TseHttpClients.base(
        userAgent = TseEndpoints.userAgent(BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, BuildConfig.PRIVACY_POLICY_URL),
        allowedHosts = TseEndpoints.ALLOWED_HOSTS,
        debugInterceptors = BuildTypeNetworkInterceptors.create(),
    )

    @Provides
    @Singleton
    @TseApiClient
    fun apiOkHttpClient(base: OkHttpClient, clock: Clock): OkHttpClient = TseHttpClients.api(base, clock)

    @Provides
    @Singleton
    @OpenDataClient
    fun openDataOkHttpClient(base: OkHttpClient): OkHttpClient = TseHttpClients.openData(base)

    @Provides
    @Singleton
    fun retrofit(@TseApiClient client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(TseEndpoints.API_BASE_URL)
        .client(client)
        .addConverterFactory(TseJson.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun divulgaCandContasApi(retrofit: Retrofit): DivulgaCandContasApi = retrofit.create(DivulgaCandContasApi::class.java)

    @Provides
    fun tseCallExecutor(): TseCallExecutor = TseCallExecutor()

    @Provides
    @Singleton
    fun openDataFileStore(
        @OpenDataClient client: OkHttpClient,
        @ApplicationContext context: Context,
        clock: Clock,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): OpenDataFileStore = OpenDataFileStore(
        client = client,
        baseUrl = TseEndpoints.OPEN_DATA_BASE_URL.toHttpUrl(),
        directory = File(context.cacheDir, OPEN_DATA_DIRECTORY),
        executor = TseCallExecutor(),
        clock = clock,
        ioDispatcher = ioDispatcher,
    )
}
