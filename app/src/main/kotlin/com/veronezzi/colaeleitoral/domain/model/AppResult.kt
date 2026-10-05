package com.veronezzi.colaeleitoral.domain.model

/** Errors the UI maps to messages. Never carries response bodies (they hold personal data). */
sealed interface AppError {
    /** No connectivity, DNS failure or timeout. */
    data object Network : AppError

    /**
     * The TSE refused the request: HTTP 403 or 429, or an anti-bot HTML page instead of JSON
     * (Akamai). Typical outside Brazil, behind VPNs and on datacenter networks.
     */
    data class Blocked(val httpCode: Int?) : AppError

    /** HTTP 404, or HTTP 200 with an empty body (how the TSE answers for unpublished candidacies). */
    data object NotFound : AppError

    /** HTTP 5xx, or a transient HTTP 400, still failing after retries. */
    data class Server(val httpCode: Int) : AppError

    /** The body is not the expected JSON. */
    data object Parsing : AppError

    /**
     * Writing or reading local storage failed: disk full (`SQLiteFullException`), I/O error, or a
     * file that can't be read right now. Nothing reached the network. UI message: "Pouco espaço
     * ou erro de armazenamento no aparelho".
     */
    data object Storage : AppError

    data class Unknown(val cause: Throwable? = null) : AppError
}

sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}
