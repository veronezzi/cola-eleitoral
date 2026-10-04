package com.veronezzi.meusantinho.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug builds only (the logging library is a `debugImplementation` dependency): logs request
 * and response lines and headers. Never bodies, which carry CPF and título of candidates.
 * The release source set has an empty counterpart.
 */
object BuildTypeNetworkInterceptors {
    private const val TAG = "MeuSantinhoHttp"

    fun create(): List<Interceptor> = listOf(
        HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        },
    )
}
