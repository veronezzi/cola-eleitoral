package com.veronezzi.colaeleitoral.data.local

import android.database.SQLException
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A local write failed (disk full, for example) in code that also reads from the network, where a
 * bare [IOException] would pass for a connection problem.
 */
class LocalStorageException(cause: IOException) : IOException("Local storage failed", cause)

/**
 * True for failures of local storage: a full disk (`SQLiteFullException`), I/O errors of files
 * and DataStore, and the other SQLite errors (`android.database.SQLException` is also what the
 * `androidx.sqlite` drivers throw on Android).
 */
fun Throwable.isStorageFailure(): Boolean = this is IOException || this is SQLException

/**
 * Runs a local write and turns a storage failure into `AppResult.Failure(AppError.Storage)`
 * instead of an exception that would crash the caller's scope. Cancellation always propagates,
 * and so does anything else (a programming error is not a storage failure).
 */
inline fun <T> storageResult(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    if (!e.isStorageFailure()) throw e
    AppResult.Failure(AppError.Storage)
}
