package com.veronezzi.colaeleitoral.data.repository

import com.veronezzi.colaeleitoral.data.local.db.FetchStateEntity
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Candidate details and running mates of this process only (S2): never in Room, never on disk,
 * no `fetch_state` row. The voter opens a detail right before saving a pick, so a stored list of
 * opened details would reveal the picks to anyone who extracts the app files, without breaking
 * their encryption. An access-ordered LRU of [MAX_ENTRIES]; after a restart the detail is
 * downloaded again when its screen opens.
 */
@Singleton
class CandidateDetailMemoryCache @Inject constructor() {
    /** A downloaded detail (null while only failures were seen) and its freshness. */
    class Entry(val detail: CandidateDetail?, val state: FetchStateEntity)

    private val entries = object : LinkedHashMap<String, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_ENTRIES
    }
    private val version = MutableStateFlow(0L)

    fun get(key: String): Entry? = synchronized(entries) { entries[key] }

    fun put(key: String, entry: Entry) {
        synchronized(entries) { entries[key] = entry }
        version.update { it + 1 }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
        version.update { it + 1 }
    }

    /** The entry of [key] now and after each change of it. */
    fun observe(key: String): Flow<Entry?> = version.map { get(key) }.distinctUntilChanged()

    private companion object {
        const val MAX_ENTRIES = 50
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
