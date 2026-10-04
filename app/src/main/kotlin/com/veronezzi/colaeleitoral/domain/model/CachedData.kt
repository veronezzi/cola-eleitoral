package com.veronezzi.colaeleitoral.domain.model

import java.time.Instant

/**
 * A cache-backed value. [fetchedAt] is null when nothing was ever downloaded ([value] is then
 * empty). [isStale] is true when the data is older than its TTL or the last refresh failed;
 * [lastError] tells why the last refresh failed, if it did. [source] is the TSE system that served
 * [value]; the UI names it next to the data ("Fonte: dados abertos do TSE").
 */
data class CachedData<out T>(
    val value: T,
    val fetchedAt: Instant?,
    val isStale: Boolean,
    val lastError: AppError? = null,
    val source: DataSource = DataSource.DIVULGA_CAND_CONTAS,
)

/** Official TSE system a cached value came from. */
enum class DataSource {
    /** DivulgaCandContas JSON API, the primary source. */
    DIVULGA_CAND_CONTAS,

    /**
     * TSE open-data files (dadosabertos.tse.jus.br), the automatic fallback for candidate lists of
     * general elections when DivulgaCandContas answers [AppError.Blocked]. No photos, no detail.
     */
    TSE_OPEN_DATA,
}
