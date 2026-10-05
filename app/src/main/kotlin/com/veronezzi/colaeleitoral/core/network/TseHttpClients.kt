package com.veronezzi.colaeleitoral.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * OkHttp clients (ARCHITECTURE.md 2.7). One base client, whose connection pool and dispatcher
 * (at most [MAX_REQUESTS_PER_HOST] parallel requests per host) are shared by the derived API and
 * open-data clients and by Coil ([PhotoImageLoader]). No cookies, no HTTP cache (Room is the cache) and
 * no response bodies in any log.
 */
object TseHttpClients {
    const val MAX_REQUESTS_PER_HOST = 4
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 60L

    /** The open-data ZIP of a general election has ~3 MB: slow mobile links need more than 60 s. */
    private const val DOWNLOAD_CALL_TIMEOUT_SECONDS = 180L

    /**
     * @param debugInterceptors network interceptors of the build type: BASIC logging (method, URL,
     * status) in debug builds, none in release (`BuildTypeNetworkInterceptors`).
     */
    fun base(userAgent: String, allowedHosts: Set<String>, debugInterceptors: List<Interceptor>): OkHttpClient {
        val allowlist = HostAllowlistInterceptor(allowedHosts)
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor(allowlist)
            .addInterceptor(UserAgentInterceptor(userAgent))
            .addNetworkInterceptor(allowlist)
        debugInterceptors.forEach { builder.addNetworkInterceptor(it) }
        return builder.build()
    }

    /** DivulgaCandContas JSON API: asks for JSON and maps refusals before any parsing. */
    fun api(base: OkHttpClient, clock: Clock): OkHttpClient = base.newBuilder()
        .addInterceptor(AcceptJsonInterceptor())
        .addInterceptor(TseResponseGuardInterceptor(clock))
        .build()

    /** TSE open-data downloads (cdn.tse.jus.br). */
    fun openData(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .callTimeout(DOWNLOAD_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

/** Suspends until the response headers arrive. Cancelling the coroutine cancels the call. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        },
    )
}
