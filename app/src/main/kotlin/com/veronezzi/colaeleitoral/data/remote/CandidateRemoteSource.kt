package com.veronezzi.colaeleitoral.data.remote

import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election

/**
 * A TSE system that lists the candidates of one office in one electoral unit:
 * [DivulgaCandContasSource] (primary) or [com.veronezzi.colaeleitoral.data.remote.opendata.TseOpenDataSource]
 * (fallback for general elections). [CandidateSourceSelector] picks between them.
 */
interface CandidateRemoteSource {
    val source: DataSource

    /** False when this source cannot serve [election] at all. */
    fun supports(election: Election): Boolean

    /** Every candidacy listed for [officeCode] in [ueCode], in no particular order. */
    suspend fun fetchCandidates(election: Election, ueCode: String, officeCode: Int): AppResult<List<Candidate>>
}

/** A votable office as listed by the TSE for one electoral unit. */
data class RemoteOffice(
    val code: Int,
    val name: String,
    val candidateCount: Int?,
)

/** A fetch result and the TSE system that produced it. */
data class Sourced<out T>(
    val result: AppResult<T>,
    val source: DataSource,
)
