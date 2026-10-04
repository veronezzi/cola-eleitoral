package com.veronezzi.colaeleitoral.core.network

import okhttp3.Interceptor

/** Release builds log no network traffic at all. Debug builds log headers (src/debug). */
object BuildTypeNetworkInterceptors {
    fun create(): List<Interceptor> = emptyList()
}
