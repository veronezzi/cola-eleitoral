package com.veronezzi.colaeleitoral.domain.model

/**
 * What the TSE has published about the second round of one office in one unit. Between the rounds
 * the first-round result can take days to reach the TSE files (the open data keeps "#NULO" until
 * the totalization is loaded), so the app may only say there is no runoff after somebody was
 * marked as elected.
 */
enum class RunoffStatus {
    /** Somebody is marked "2º turno": the office is on the second-round ballot. */
    RUNOFF,

    /** Nobody is in the second round and somebody was elected in the first one. */
    DECIDED,

    /** The first-round result isn't published yet (empty, "#NULO", "Concorrendo"...). */
    PENDING,
}

/** Reads [RunoffStatus] from the full candidate list of an office, never from a filtered one. */
fun runoffStatusOf(candidates: List<Candidate>): RunoffStatus = when {
    candidates.any { it.status.isInSecondRound } -> RunoffStatus.RUNOFF
    candidates.any { it.status.isElected } -> RunoffStatus.DECIDED
    else -> RunoffStatus.PENDING
}
