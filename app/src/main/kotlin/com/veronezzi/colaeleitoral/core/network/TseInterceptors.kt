package com.veronezzi.colaeleitoral.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The TSE refused the request: HTTP 403 (Akamai "Access Denied"), HTTP 429, or an HTML page in
 * place of data (anti-bot challenge, [httpCode] null). Extends [IOException] because OkHttp
 * interceptors may only throw that; the data layer maps it to `AppError.Blocked`.
 */
class TseBlockedException(val httpCode: Int?, val retryAfterSeconds: Long? = null) :
    IOException("TSE refused the request (HTTP ${httpCode ?: "200 text/html"})")

/** HTTP 2xx with an empty body: how the TSE answers for candidacies it did not publish. */
class TseEmptyBodyException : IOException("TSE answered with an empty body")

/** An HTTP status handled by its code. Holds the code only, never the body. */
class TseHttpStatusException(val httpCode: Int) : IOException("TSE answered HTTP $httpCode")

/** A response the app cannot use, such as a file over the size limit. Never retried. */
class TseUnusableResponseException(message: String) : IOException(message)

/** A request to a host outside [TseEndpoints.ALLOWED_HOSTS]; it never reaches the network. */
class DisallowedHostException(host: String) : IOException("Host not allowed: $host")

/** Replaces OkHttp's default User-Agent with the app's own ([TseEndpoints.userAgent]). */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
}

/**
 * Fails every request to a host outside [allowedHosts]. Installed as an application interceptor
 * (before any connection is made) and as a network interceptor (every redirect hop).
 */
class HostAllowlistInterceptor(private val allowedHosts: Set<String>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (host !in allowedHosts) throw DisallowedHostException(host)
        return chain.proceed(chain.request())
    }
}

/** Asks DivulgaCandContas for JSON. */
class AcceptJsonInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build())
}

/**
 * Turns the TSE's refusal signals into exceptions before any body is parsed (ARCHITECTURE.md
 * 2.6): 403 and 429 become [TseBlockedException] (with `Retry-After`), a 2xx HTML page becomes
 * [TseBlockedException] without a code, and a 2xx empty body becomes [TseEmptyBodyException].
 * Those bodies are closed unread.
 */
class TseResponseGuardInterceptor(private val clock: Clock) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val code = response.code
        if (code == HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS) {
            val retryAfter = parseRetryAfterSeconds(response.header("Retry-After"), clock.instant())
            response.close()
            throw TseBlockedException(code, retryAfter)
        }
        if (response.isSuccessful) {
            if (response.body.contentType()?.subtype?.contains("html", ignoreCase = true) == true) {
                response.close()
                throw TseBlockedException(httpCode = null)
            }
            if (response.peekBody(1).contentLength() == 0L) {
                response.close()
                throw TseEmptyBodyException()
            }
        }
        return response
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

/** `Retry-After` in seconds (delta-seconds or HTTP date), or null when absent or invalid. */
fun parseRetryAfterSeconds(value: String?, now: Instant): Long? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    text.toLongOrNull()?.let { return it.coerceAtLeast(0) }
    val date = try {
        ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    } catch (e: DateTimeParseException) {
        return null
    }
    return Duration.between(now, date).seconds.coerceAtLeast(0)
}
