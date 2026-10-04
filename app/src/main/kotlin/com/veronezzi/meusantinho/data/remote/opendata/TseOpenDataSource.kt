package com.veronezzi.meusantinho.data.remote.opendata

import com.veronezzi.meusantinho.core.common.IoDispatcher
import com.veronezzi.meusantinho.core.network.TseUnusableResponseException
import com.veronezzi.meusantinho.data.remote.CandidateRemoteSource
import com.veronezzi.meusantinho.domain.model.AppError
import com.veronezzi.meusantinho.domain.model.AppResult
import com.veronezzi.meusantinho.domain.model.Candidate
import com.veronezzi.meusantinho.domain.model.DataSource
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.ElectionScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback source: the TSE open data (`consulta_cand_{year}.zip` plus
 * `consulta_cand_complementar_{year}.zip` for the judgement status, CC BY), used when
 * DivulgaCandContas refuses the request. General elections only: the municipal file has 64 MB
 * and is out of v1. Only the entry of the requested UE (a UF or "BR") is read from each ZIP, and
 * personal data columns are dropped while parsing ([OpenDataParser]).
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
        val candidatesPath = candidatesPath(election.year)
        val statusPath = statusPath(election.year)
        val candidatesZip = when (val result = files.fetch(candidatesPath)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        // Without the complementary file the list still works; the status then falls back to
        // DS_SITUACAO_CANDIDATURA. Any other failure (offline, blocked) fails the request.
        val statusZip = when (val result = files.fetch(statusPath)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> if (result.error == AppError.NotFound) null else return result
        }
        val ue = ueCode.uppercase()
        val key = ParsedUnit.Key(election.id, ue, candidatesZip.version(), statusZip?.version())
        return parseMutex.withLock {
            val unit = lastUnit?.takeIf { it.key == key } ?: try {
                withContext(ioDispatcher) { parse(election, ue, candidatesZip, statusZip) }
                    ?.let { ParsedUnit(key, it) }
                    ?.also { lastUnit = it }
            } catch (e: ZipException) {
                files.invalidate(candidatesPath)
                files.invalidate(statusPath)
                return@withLock AppResult.Failure(AppError.Parsing)
            } catch (e: IOException) {
                return@withLock AppResult.Failure(AppError.Parsing)
            }
            if (unit == null) AppResult.Failure(AppError.NotFound) else AppResult.Success(unit.byOffice[officeCode].orEmpty())
        }
    }

    /** Null when the ZIP has no entry for [ue]. */
    private fun parse(election: Election, ue: String, candidatesZip: File, statusZip: File?): Map<Int, List<Candidate>>? {
        val rows = ZipFile(candidatesZip).use { zip ->
            val entry = zip.findEntry("consulta_cand_${election.year}_$ue.csv") ?: return null
            zip.getInputStream(entry).limited().use(OpenDataParser::readCandidates)
        }
        val wanted = rows.mapTo(HashSet()) { it.candidateId }
        val statuses = statusZip?.let { file ->
            ZipFile(file).use { zip ->
                zip.findEntry("consulta_cand_complementar_${election.year}_$ue.csv")?.let { entry ->
                    zip.getInputStream(entry).limited().use { OpenDataParser.readStatuses(it, wanted) }
                }
            }
        }.orEmpty()
        return OpenDataMapper.candidatesByOffice(election, ue, rows, statuses)
    }

    private fun ZipFile.findEntry(name: String) = entries().asSequence().firstOrNull { entry ->
        !entry.isDirectory && entry.name.substringAfterLast('/').equals(name, ignoreCase = true)
    }

    private fun File.version(): String = "${lastModified()}:${length()}"

    private class ParsedUnit(val key: Key, val byOffice: Map<Int, List<Candidate>>) {
        data class Key(val electionId: Long, val ueCode: String, val candidatesVersion: String, val statusVersion: String?)
    }

    companion object {
        /** Uncompressed size limit per CSV entry (the largest, SP, has 1.4 MB). */
        private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

        fun candidatesPath(year: Int) = "consulta_cand/consulta_cand_$year.zip"

        fun statusPath(year: Int) = "consulta_cand_complementar/consulta_cand_complementar_$year.zip"

        private fun InputStream.limited(): InputStream = object : FilterInputStream(this) {
            private var total = 0L

            override fun read(): Int = super.read().also { if (it != -1) count(1) }

            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it) }

            private fun count(bytes: Int) {
                total += bytes
                if (total > MAX_ENTRY_BYTES) throw TseUnusableResponseException("Open-data entry too large")
            }
        }
    }
}
