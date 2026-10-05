package com.veronezzi.colaeleitoral.data.mapper

import com.veronezzi.colaeleitoral.data.local.db.CandidateColumns
import com.veronezzi.colaeleitoral.data.local.db.CandidateEntity
import com.veronezzi.colaeleitoral.data.local.db.ElectionEntity
import com.veronezzi.colaeleitoral.data.local.db.MunicipalityEntity
import com.veronezzi.colaeleitoral.data.local.db.OfficeEntity
import com.veronezzi.colaeleitoral.data.remote.RemoteOffice
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.CandidateStatus
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.Party
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.normalizeForSearch
import java.time.LocalDate
import java.time.format.DateTimeParseException

fun Election.toEntity() = ElectionEntity(
    id = id,
    year = year,
    name = name,
    scope = scope.name,
    round = round?.number,
    date = date?.toString(),
)

fun ElectionEntity.toDomainOrNull(): Election? {
    val scope = ElectionScope.entries.firstOrNull { it.name == scope } ?: return null
    return Election(
        id = id,
        year = year,
        name = name,
        round = round?.let(Round::fromNumber),
        scope = scope,
        date = date?.let(::parseIsoDate),
    )
}

fun ElectoralUnit.toMunicipalityEntity(sourceElectionId: Long) = MunicipalityEntity(
    code = code,
    uf = uf,
    name = name,
    sourceElectionId = sourceElectionId,
    searchKey = normalizeForSearch(name),
)

fun MunicipalityEntity.toDomain() = ElectoralUnit(code = code, name = name, uf = uf, isMunicipality = true)

fun RemoteOffice.toEntity(electionId: Long, ueCode: String) =
    OfficeEntity(electionId = electionId, ueCode = ueCode, code = code, name = name, candidateCount = candidateCount)

fun Candidate.toColumns() = CandidateColumns(
    ueCode = ueCode,
    officeCode = officeCode,
    number = number,
    ballotName = ballotName,
    fullName = fullName,
    partyAcronym = party.acronym,
    partyNumber = party.number,
    partyName = party.name,
    coalition = coalition,
    registrationStatus = status.registration,
    totalizationStatus = status.totalization,
    onBallotStatus = status.onBallot,
    isFit = status.isFit,
    photoUrl = photoUrl,
)

fun Candidate.toEntity() = CandidateEntity(electionId = electionId, id = id, columns = toColumns())

fun CandidateColumns.toCandidate(electionId: Long, id: Long) = Candidate(
    id = id,
    electionId = electionId,
    ueCode = ueCode,
    officeCode = officeCode,
    number = number,
    ballotName = ballotName,
    fullName = fullName,
    party = Party(acronym = partyAcronym, number = partyNumber, name = partyName),
    coalition = coalition,
    status = CandidateStatus(
        registration = registrationStatus,
        totalization = totalizationStatus,
        onBallot = onBallotStatus,
        isFit = isFit,
    ),
    photoUrl = photoUrl,
)

fun CandidateEntity.toDomain() = columns.toCandidate(electionId, id)

/** Stored source name back to the enum; unknown values (older app versions) are the primary. */
fun dataSourceOf(name: String?): DataSource =
    DataSource.entries.firstOrNull { it.name == name } ?: DataSource.DIVULGA_CAND_CONTAS

private fun parseIsoDate(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: DateTimeParseException) {
    null
}

/** [AppError] as a short code in `fetch_state.lastError`. Never a message or a body. */
object AppErrorCodec {
    private const val NETWORK = "network"
    private const val BLOCKED = "blocked:"
    private const val NOT_FOUND = "not_found"
    private const val SERVER = "server:"
    private const val PARSING = "parsing"
    private const val STORAGE = "storage"
    private const val UNKNOWN = "unknown"
    private const val DEFAULT_SERVER_CODE = 500

    fun encode(error: AppError): String = when (error) {
        AppError.Network -> NETWORK
        is AppError.Blocked -> BLOCKED + (error.httpCode?.toString() ?: "")
        AppError.NotFound -> NOT_FOUND
        is AppError.Server -> SERVER + error.httpCode
        AppError.Parsing -> PARSING
        AppError.Storage -> STORAGE
        is AppError.Unknown -> UNKNOWN
    }

    fun decode(code: String): AppError = when {
        code == NETWORK -> AppError.Network
        code.startsWith(BLOCKED) -> AppError.Blocked(code.removePrefix(BLOCKED).toIntOrNull())
        code == NOT_FOUND -> AppError.NotFound
        code.startsWith(SERVER) -> AppError.Server(code.removePrefix(SERVER).toIntOrNull() ?: DEFAULT_SERVER_CODE)
        code == PARSING -> AppError.Parsing
        code == STORAGE -> AppError.Storage
        else -> AppError.Unknown(null)
    }
}
