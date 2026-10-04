package com.veronezzi.meusantinho.ui.common

import com.veronezzi.meusantinho.domain.model.AppError
import com.veronezzi.meusantinho.domain.model.CachedData
import com.veronezzi.meusantinho.domain.model.DataSource
import java.time.Instant

/**
 * How fresh a cached TSE value is, as shown by the data-source indicator.
 *
 * @property fetchedAt last successful download; null when nothing was ever downloaded.
 * @property isStale older than its TTL or the last refresh failed.
 * @property lastError why the last refresh failed, if it did.
 */
data class Freshness(
    val fetchedAt: Instant?,
    val isStale: Boolean,
    val lastError: AppError?,
    val source: DataSource,
) {
    /** True when the last refresh failed for lack of connectivity: the app is showing its cache. */
    val isOffline: Boolean get() = lastError == AppError.Network

    val hasData: Boolean get() = fetchedAt != null
}

fun CachedData<*>.toFreshness(): Freshness = Freshness(
    fetchedAt = fetchedAt,
    isStale = isStale,
    lastError = lastError,
    source = source,
)

/** What a cache-first screen can show. */
sealed interface LoadState {
    data object Loading : LoadState

    data class Failed(val error: AppError) : LoadState

    data object Loaded : LoadState
}

/**
 * Data wins over errors (an error with data only marks it stale, see [Freshness]); without data,
 * a refresh in progress shows loading and a known error shows the error screen.
 */
fun loadStateOf(hasData: Boolean, error: AppError?, isRefreshing: Boolean): LoadState = when {
    hasData -> LoadState.Loaded
    isRefreshing -> LoadState.Loading
    error != null -> LoadState.Failed(error)
    else -> LoadState.Loading
}
