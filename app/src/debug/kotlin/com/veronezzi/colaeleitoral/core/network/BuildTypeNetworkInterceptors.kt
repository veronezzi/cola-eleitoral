package com.veronezzi.colaeleitoral.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug builds only (the logging library is a `debugImplementation` dependency): logs the
 * request and response lines (ARCHITECTURE.md 2.7, BASIC). No headers and never bodies, which
 * carry CPF and título of candidates. The release source set has an empty counterpart.
 */
object BuildTypeNetworkInterceptors {
    private const val TAG = "ColaEleitoralHttp"

    fun create(): List<Interceptor> = listOf(
        HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        },
    )
}
