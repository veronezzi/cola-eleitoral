package com.veronezzi.colaeleitoral.domain.model

import java.text.Normalizer
import java.util.Locale

enum class SortOrder {
    /** Default: by urna number. */
    NUMBER,
    BALLOT_NAME,
    PARTY,
}

/**
 * User-chosen filters. The default shows every candidate the TSE lists, ordered by number:
 * the app never ranks, highlights or recommends.
 *
 * @property query name, number or party acronym. Digits match the start of the number; text
 * matches ballot name, full name or party acronym, ignoring case and accents.
 * @property registrationStatuses verbatim TSE status texts to keep; empty keeps all.
 */
data class CandidateFilter(
    val query: String = "",
    val partyAcronyms: Set<String> = emptySet(),
    val registrationStatuses: Set<String> = emptySet(),
    val onlySecondRound: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NUMBER,
)

/** Parties and status texts present in a cached list, for the filter chips. */
data class FilterOptions(
    val parties: List<Party>,
    val registrationStatuses: List<String>,
)

private val DIACRITICS = Regex("""\p{Mn}+""")

/** Lowercase, accent-free and trimmed text for search comparisons. */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .trim()

fun List<Candidate>.filteredBy(filter: CandidateFilter): List<Candidate> {
    val query = normalizeForSearch(filter.query)
    val isNumberQuery = query.isNotEmpty() && query.all { it.isDigit() }
    // Each normalized text is computed at most once per candidate, not once per comparison.
    val filtered = map(::SearchKeys).filter { keys ->
        val candidate = keys.candidate
        val matchesQuery = when {
            query.isEmpty() -> true
            isNumberQuery -> candidate.number.toString().startsWith(query)
            else -> keys.ballotName.contains(query) ||
                keys.fullName?.contains(query) == true ||
                keys.party == query
        }
        matchesQuery &&
            (filter.partyAcronyms.isEmpty() || candidate.party.acronym in filter.partyAcronyms) &&
            (filter.registrationStatuses.isEmpty() ||
                candidate.status.registration in filter.registrationStatuses) &&
            (!filter.onlySecondRound || candidate.status.isInSecondRound)
    }
    val comparator: Comparator<SearchKeys> = when (filter.sortOrder) {
        SortOrder.NUMBER -> compareBy<SearchKeys> { it.candidate.number }.thenBy { it.ballotName }
        SortOrder.BALLOT_NAME -> compareBy<SearchKeys> { it.ballotName }.thenBy { it.candidate.number }
        SortOrder.PARTY -> compareBy<SearchKeys> { it.party }.thenBy { it.candidate.number }
    }
    return filtered.sortedWith(comparator).map { it.candidate }
}

/** Normalized texts of one candidate, each computed on first use. */
private class SearchKeys(val candidate: Candidate) {
    val ballotName: String by lazy(LazyThreadSafetyMode.NONE) { normalizeForSearch(candidate.ballotName) }
    val fullName: String? by lazy(LazyThreadSafetyMode.NONE) { candidate.fullName?.let(::normalizeForSearch) }
    val party: String by lazy(LazyThreadSafetyMode.NONE) { normalizeForSearch(candidate.party.acronym) }
}
