package com.veronezzi.meusantinho.domain.model

import java.time.Instant

/**
 * A cache-backed value. [fetchedAt] is null when nothing was ever downloaded ([value] is then
 * empty). [isStale] is true when the data is older than its TTL or the last refresh failed;
 * [lastError] tells why the last refresh failed, if it did.
 */
data class CachedData<out T>(
    val value: T,
    val fetchedAt: Instant?,
    val isStale: Boolean,
    val lastError: AppError? = null,
)
