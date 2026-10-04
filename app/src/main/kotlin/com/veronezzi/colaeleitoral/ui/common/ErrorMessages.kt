package com.veronezzi.colaeleitoral.ui.common

import androidx.annotation.StringRes
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.AppError

/**
 * Text and recovery actions for an [AppError] (ARCHITECTURE.md 2.6).
 *
 * @property canRetry offer "Tentar de novo".
 * @property showOfficialSite offer "Abrir site do TSE": the official site is the way out when the
 * TSE refuses this network or did not publish the data.
 */
data class ErrorPresentation(
    @StringRes val title: Int,
    @StringRes val message: Int,
    val canRetry: Boolean,
    val showOfficialSite: Boolean,
)

/**
 * @param hasCache whether cached data is still on screen (changes the network message).
 * @param aboutCandidate the request was a candidate detail (changes the "not published" message).
 */
fun AppError.presentation(hasCache: Boolean = false, aboutCandidate: Boolean = false): ErrorPresentation =
    when (this) {
        AppError.Network -> ErrorPresentation(
            title = R.string.error_network_title,
            message = if (hasCache) R.string.error_network_with_cache else R.string.error_network_no_cache,
            canRetry = true,
            showOfficialSite = false,
        )
        is AppError.Blocked -> if (httpCode == HTTP_TOO_MANY_REQUESTS) {
            ErrorPresentation(
                title = R.string.error_rate_limited_title,
                message = R.string.error_rate_limited_message,
                canRetry = true,
                showOfficialSite = false,
            )
        } else {
            ErrorPresentation(
                title = R.string.error_blocked_title,
                message = R.string.error_blocked_message,
                canRetry = true,
                showOfficialSite = true,
            )
        }
        AppError.NotFound -> ErrorPresentation(
            title = R.string.error_not_found_title,
            message = if (aboutCandidate) R.string.error_not_found_candidate else R.string.error_not_found_message,
            canRetry = false,
            showOfficialSite = true,
        )
        is AppError.Server -> ErrorPresentation(
            title = R.string.error_server_title,
            message = R.string.error_server_message,
            canRetry = true,
            showOfficialSite = true,
        )
        AppError.Parsing -> ErrorPresentation(
            title = R.string.error_parsing_title,
            message = R.string.error_parsing_message,
            canRetry = true,
            showOfficialSite = true,
        )
        is AppError.Unknown -> ErrorPresentation(
            title = R.string.error_unknown_title,
            message = R.string.error_unknown_message,
            canRetry = true,
            showOfficialSite = false,
        )
    }

/** One-line reason for the "may be outdated" banner shown over cached data. */
@StringRes
fun AppError.staleReason(): Int = when (this) {
    AppError.Network -> R.string.stale_reason_network
    is AppError.Blocked -> if (httpCode == HTTP_TOO_MANY_REQUESTS) {
        R.string.stale_reason_rate_limited
    } else {
        R.string.stale_reason_blocked
    }
    AppError.NotFound -> R.string.stale_reason_not_found
    is AppError.Server -> R.string.stale_reason_server
    AppError.Parsing -> R.string.stale_reason_parsing
    is AppError.Unknown -> R.string.stale_reason_unknown
}

private const val HTTP_TOO_MANY_REQUESTS = 429
