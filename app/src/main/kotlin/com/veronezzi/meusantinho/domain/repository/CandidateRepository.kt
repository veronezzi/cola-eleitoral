package com.veronezzi.meusantinho.domain.repository

import com.veronezzi.meusantinho.domain.model.AppResult
import com.veronezzi.meusantinho.domain.model.CachedData
import com.veronezzi.meusantinho.domain.model.Candidate
import com.veronezzi.meusantinho.domain.model.CandidateDetail
import com.veronezzi.meusantinho.domain.model.CandidateFilter
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.FilterOptions
import kotlinx.coroutines.flow.Flow

/** Candidates of one office in one electoral unit. Same cache-first contract as [ElectionRepository]. */
interface CandidateRepository {
    /** Every candidate the TSE lists for the office, after [filter] (default: all, by number). */
    fun observeCandidates(
        electionId: Long,
        ueCode: String,
        officeCode: Int,
        filter: CandidateFilter = CandidateFilter(),
    ): Flow<CachedData<List<Candidate>>>

    suspend fun refreshCandidates(
        election: Election,
        ueCode: String,
        officeCode: Int,
        force: Boolean = false,
    ): AppResult<Unit>

    fun observeFilterOptions(electionId: Long, ueCode: String, officeCode: Int): Flow<FilterOptions>

    /** Null value until the detail was downloaded once. */
    fun observeCandidateDetail(electionId: Long, candidateId: Long): Flow<CachedData<CandidateDetail?>>

    suspend fun refreshCandidateDetail(
        election: Election,
        ueCode: String,
        candidateId: Long,
        force: Boolean = false,
    ): AppResult<Unit>
}
