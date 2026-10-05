package com.veronezzi.colaeleitoral.ui.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a write to the device's own storage (picks, preferences, caches) from a ViewModel and
 * returns its value, or null when it failed. The repositories already turn the usual failures into
 * `AppError.Storage`; this guard covers the writes that return nothing, so a full disk, an I/O
 * error or a Keystore hiccup becomes a message on screen instead of a crash on election day.
 * The data is left as it was. Cancellation always propagates.
 */
suspend fun <T : Any> tryLocalWrite(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    // Never logged: the exception may carry pick data in its message.
    null
}
