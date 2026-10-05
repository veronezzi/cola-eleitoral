package com.veronezzi.colaeleitoral.data.remote.opendata

import com.veronezzi.colaeleitoral.core.common.IoDispatcher
import com.veronezzi.colaeleitoral.data.remote.CandidateRemoteSource
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback source: the TSE open data (`consulta_cand_{year}.zip` plus
 * `consulta_cand_complementar_{year}.zip` for the judgement status, CC BY), used when
 * DivulgaCandContas refuses the request. General elections only: the municipal file has 64 MB
 * and is out of v1. [OpenDataFileStore] streams each ZIP once and keeps, per unit (a UF or "BR"),
 * only the public candidacy columns ([OpenDataParser]); the ZIPs and the personal data columns
 * never reach the disk (S5).
 */
@Singleton
class TseOpenDataSource @Inject constructor(
    private val files: OpenDataFileStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CandidateRemoteSource {
    override val source: DataSource = DataSource.TSE_OPEN_DATA

    private val parseMutex = Mutex()
    private var lastUnit: ParsedUnit? = null

    override fun supports(election: Election): Boolean = election.scope == ElectionScope.GENERAL

    override suspend fun fetchCandidates(
        election: Election,
        ueCode: String,
        officeCode: Int,
    ): AppResult<List<Candidate>> {
        if (!supports(election)) return AppResult.Failure(AppError.NotFound)
        val year = election.year
        val candidates = when (val result = files.fetch(candidatesPath(year), unitEntries(CANDIDATES_PREFIX, year), CANDIDATES)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        // Without the complementary file the list still works; the status then falls back to
        // DS_SITUACAO_CANDIDATURA. Any other failure (offline, blocked) fails the request.
        val statuses = when (val result = files.fetch(statusPath(year), unitEntries(STATUS_PREFIX, year), STATUSES)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> if (result.error == AppError.NotFound) null else return result
        }
        val ue = ueCode.uppercase()
        val key = ParsedUnit.Key(election.id, ue, candidates.version, statuses?.version)
        return parseMutex.withLock {
            val unit = lastUnit?.takeIf { it.key == key } ?: try {
                withContext(ioDispatcher) { parse(election, ue, candidates, statuses) }
                    ?.let { ParsedUnit(key, it) }
                    ?.also { lastUnit = it }
            } catch (e: IOException) {
                // A derived file that does not read back: download it again next time.
                files.invalidate(candidatesPath(year))
                files.invalidate(statusPath(year))
                return@withLock AppResult.Failure(AppError.Parsing)
            }
            if (unit == null) AppResult.Failure(AppError.NotFound) else AppResult.Success(unit.byOffice[officeCode].orEmpty())
        }
    }

    /** Null when the open data has no entry for [ue]. */
    private fun parse(election: Election, ue: String, candidates: OpenDataSet, statuses: OpenDataSet?): Map<Int, List<Candidate>>? {
        val rows = candidates.open(unitEntry(CANDIDATES_PREFIX, election.year, ue))
            ?.use { OpenDataParser.readCandidates(it, OpenDataParser.DERIVED_CHARSET) }
            ?: return null
        val wanted = rows.mapTo(HashSet()) { it.candidateId }
        val statusRows = statuses?.open(unitEntry(STATUS_PREFIX, election.year, ue))
            ?.use { OpenDataParser.readStatuses(it, wanted, OpenDataParser.DERIVED_CHARSET) }
            .orEmpty()
        return OpenDataMapper.candidatesByOffice(election, ue, rows, statusRows)
    }

    private class ParsedUnit(val key: Key, val byOffice: Map<Int, List<Candidate>>) {
        data class Key(val electionId: Long, val ueCode: String, val candidatesVersion: String, val statusVersion: String?)
    }

    companion object {
        private const val CANDIDATES_PREFIX = "consulta_cand"
        private const val STATUS_PREFIX = "consulta_cand_complementar"
        private val CANDIDATES = OpenDataReducer(OpenDataParser::reduceCandidates)
        private val STATUSES = OpenDataReducer(OpenDataParser::reduceStatuses)

        fun candidatesPath(year: Int) = "consulta_cand/consulta_cand_$year.zip"

        fun statusPath(year: Int) = "consulta_cand_complementar/consulta_cand_complementar_$year.zip"

        private fun unitEntry(prefix: String, year: Int, ue: String) = "${prefix}_${year}_$ue.csv"

        /**
         * One CSV per unit: the 27 UFs, "BR" and "ZZ" (abroad). The aggregate `_BRASIL.csv`
         * repeats every UF and is skipped.
         */
        private fun unitEntries(prefix: String, year: Int): (String) -> Boolean {
            val pattern = Regex("""${Regex.escape(prefix)}_${year}_[A-Z]{2}\.csv""", RegexOption.IGNORE_CASE)
            return { name -> pattern.matches(name) }
        }
    }
}
