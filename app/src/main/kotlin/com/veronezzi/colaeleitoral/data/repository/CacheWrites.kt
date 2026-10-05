package com.veronezzi.colaeleitoral.data.repository

import androidx.room.withTransaction
import com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase
import com.veronezzi.colaeleitoral.data.local.storageResult
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.normalizeForSearch

/**
 * Writes a refresh result in one transaction, unless the cache was cleared since [generation]
 * (the result then belongs to data the user deleted and is dropped). A full disk or another
 * storage failure becomes `AppError.Storage` instead of an exception; cancellation propagates.
 */
internal suspend fun PublicCacheDatabase.writeCache(
    policy: CachePolicy,
    generation: Long,
    block: suspend () -> Unit,
): AppResult<Unit> = storageResult {
    withTransaction { if (policy.isCurrent(generation)) block() }
}

/** The value of a successful local read, or null when it failed. */
internal fun <T> AppResult<T>.valueOrNull(): T? = (this as? AppResult.Success)?.value

/** Sorted by the accent-free, lowercase form of [text], computed once per element. */
internal inline fun <T> Iterable<T>.sortedByNormalized(crossinline text: (T) -> String): List<T> =
    map { it to normalizeForSearch(text(it)) }.sortedBy { it.second }.map { it.first }
