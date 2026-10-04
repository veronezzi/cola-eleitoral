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
    val filtered = filter { candidate ->
        val matchesQuery = when {
            query.isEmpty() -> true
            isNumberQuery -> candidate.number.toString().startsWith(query)
            else -> normalizeForSearch(candidate.ballotName).contains(query) ||
                candidate.fullName?.let { normalizeForSearch(it).contains(query) } == true ||
                normalizeForSearch(candidate.party.acronym) == query
        }
        matchesQuery &&
            (filter.partyAcronyms.isEmpty() || candidate.party.acronym in filter.partyAcronyms) &&
            (filter.registrationStatuses.isEmpty() ||
                candidate.status.registration in filter.registrationStatuses) &&
            (!filter.onlySecondRound || candidate.status.isInSecondRound)
    }
    val comparator: Comparator<Candidate> = when (filter.sortOrder) {
        SortOrder.NUMBER -> compareBy<Candidate> { it.number }.thenBy { normalizeForSearch(it.ballotName) }
        SortOrder.BALLOT_NAME -> compareBy<Candidate> { normalizeForSearch(it.ballotName) }.thenBy { it.number }
        SortOrder.PARTY -> compareBy<Candidate> { normalizeForSearch(it.party.acronym) }.thenBy { it.number }
    }
    return filtered.sortedWith(comparator)
}
