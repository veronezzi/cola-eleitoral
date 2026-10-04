package com.veronezzi.meusantinho.data.remote.opendata

import com.veronezzi.meusantinho.domain.model.Candidate
import com.veronezzi.meusantinho.domain.model.CandidateStatus
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.OfficeRules
import com.veronezzi.meusantinho.domain.model.Party
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException

/** The open-data file does not have the columns the app needs (the TSE changed the layout). */
class OpenDataFormatException(message: String) : IOException(message)

/** One `consulta_cand` row, reduced to public candidacy fields. */
internal data class OpenDataCandidateRow(
    val candidateId: Long,
    val round: Int?,
    val ueCode: String?,
    val officeCode: Int,
    val number: Int,
    val ballotName: String,
    val civilName: String?,
    val socialName: String?,
    val partyAcronym: String,
    val partyNumber: Int?,
    val partyName: String?,
    val grouping: String?,
    val federation: String?,
    val coalition: String?,
    val totalization: String?,
    val candidacyStatus: String?,
)

/** Status columns of `consulta_cand_complementar` for one candidacy. */
internal data class OpenDataStatusRow(
    val judgement: String?,
    val totalizationStatus: String?,
)

/**
 * Parses the TSE open-data CSVs (ARCHITECTURE.md 2.10). Only the columns listed here are read;
 * personal data columns (`NR_CPF_CANDIDATO`, `NR_TITULO_ELEITORAL_CANDIDATO`, `DS_EMAIL`,
 * `DT_NASCIMENTO`, gender, race, education...) are skipped while reading and never stored.
 */
internal object OpenDataParser {
    /** The files are Latin-1; windows-1252 is a superset that also decodes typographic marks. */
    val CHARSET: Charset = try {
        Charset.forName("windows-1252")
    } catch (e: UnsupportedCharsetException) {
        Charsets.ISO_8859_1
    }

    private const val SQ_CANDIDATO = "SQ_CANDIDATO"
    private const val NR_TURNO = "NR_TURNO"
    private const val SG_UE = "SG_UE"
    private const val CD_CARGO = "CD_CARGO"
    private const val NR_CANDIDATO = "NR_CANDIDATO"
    private const val NM_URNA_CANDIDATO = "NM_URNA_CANDIDATO"
    private const val NM_CANDIDATO = "NM_CANDIDATO"
    private const val NM_SOCIAL_CANDIDATO = "NM_SOCIAL_CANDIDATO"
    private const val SG_PARTIDO = "SG_PARTIDO"
    private const val NR_PARTIDO = "NR_PARTIDO"
    private const val NM_PARTIDO = "NM_PARTIDO"
    private const val TP_AGREMIACAO = "TP_AGREMIACAO"
    private const val NM_FEDERACAO = "NM_FEDERACAO"
    private const val NM_COLIGACAO = "NM_COLIGACAO"
    private const val DS_SIT_TOT_TURNO = "DS_SIT_TOT_TURNO"
    private const val DS_SITUACAO_CANDIDATURA = "DS_SITUACAO_CANDIDATURA"
    private const val DS_SITUACAO_JULGAMENTO = "DS_SITUACAO_JULGAMENTO"
    private const val DS_SITUACAO_CANDIDATO_TOT = "DS_SITUACAO_CANDIDATO_TOT"

    private val CANDIDATE_COLUMNS = setOf(
        SQ_CANDIDATO, NR_TURNO, SG_UE, CD_CARGO, NR_CANDIDATO, NM_URNA_CANDIDATO, NM_CANDIDATO,
        NM_SOCIAL_CANDIDATO, SG_PARTIDO, NR_PARTIDO, NM_PARTIDO, TP_AGREMIACAO, NM_FEDERACAO,
        NM_COLIGACAO, DS_SIT_TOT_TURNO, DS_SITUACAO_CANDIDATURA,
    )
    private val REQUIRED_CANDIDATE_COLUMNS = setOf(SQ_CANDIDATO, CD_CARGO, NR_CANDIDATO, NM_URNA_CANDIDATO, SG_PARTIDO)
    private val STATUS_COLUMNS = setOf(SQ_CANDIDATO, DS_SITUACAO_JULGAMENTO, DS_SITUACAO_CANDIDATO_TOT)

    /** Markers the TSE uses for "no value". */
    private val MISSING = setOf("", "#NULO", "#NE", "#NULO#")

    /** Every row of one `consulta_cand_{year}_{UE}.csv`. Rows without the required fields are dropped. */
    fun readCandidates(input: InputStream): List<OpenDataCandidateRow> {
        val rows = mutableListOf<OpenDataCandidateRow>()
        readTable(input, CANDIDATE_COLUMNS, REQUIRED_CANDIDATE_COLUMNS) { value ->
            val id = value(SQ_CANDIDATO)?.toLongOrNull()
            val office = value(CD_CARGO)?.toIntOrNull()
            val number = value(NR_CANDIDATO)?.toIntOrNull()?.takeIf { it > 0 }
            val ballotName = value(NM_URNA_CANDIDATO)
            val party = value(SG_PARTIDO)
            if (id != null && office != null && number != null && ballotName != null && party != null) {
                rows += OpenDataCandidateRow(
                    candidateId = id,
                    round = value(NR_TURNO)?.toIntOrNull(),
                    ueCode = value(SG_UE),
                    officeCode = office,
                    number = number,
                    ballotName = ballotName,
                    civilName = value(NM_CANDIDATO),
                    socialName = value(NM_SOCIAL_CANDIDATO),
                    partyAcronym = party,
                    partyNumber = value(NR_PARTIDO)?.toIntOrNull(),
                    partyName = value(NM_PARTIDO),
                    grouping = value(TP_AGREMIACAO),
                    federation = value(NM_FEDERACAO),
                    coalition = value(NM_COLIGACAO),
                    totalization = value(DS_SIT_TOT_TURNO),
                    candidacyStatus = value(DS_SITUACAO_CANDIDATURA),
                )
            }
        }
        return rows
    }

    /**
     * Status columns of one `consulta_cand_complementar_{year}_{UE}.csv`, for the candidacies in
     * [wanted]. A candidacy listed twice (one row per round) keeps the first value of each column.
     */
    fun readStatuses(input: InputStream, wanted: Set<Long>): Map<Long, OpenDataStatusRow> {
        val statuses = HashMap<Long, OpenDataStatusRow>()
        readTable(input, STATUS_COLUMNS, setOf(SQ_CANDIDATO)) { value ->
            val id = value(SQ_CANDIDATO)?.toLongOrNull()
            if (id != null && id in wanted) {
                val row = OpenDataStatusRow(value(DS_SITUACAO_JULGAMENTO), value(DS_SITUACAO_CANDIDATO_TOT))
                val known = statuses[id]
                statuses[id] = if (known == null) {
                    row
                } else {
                    OpenDataStatusRow(
                        judgement = known.judgement ?: row.judgement,
                        totalizationStatus = known.totalizationStatus ?: row.totalizationStatus,
                    )
                }
            }
        }
        return statuses
    }

    private inline fun readTable(
        input: InputStream,
        columns: Set<String>,
        required: Set<String>,
        onRow: ((String) -> String?) -> Unit,
    ) {
        val reader = SemicolonCsvReader(InputStreamReader(input, CHARSET))
        val header = reader.readHeader() ?: return
        val indexOf = HashMap<String, Int>()
        header.forEachIndexed { index, name -> if (name in columns) indexOf.putIfAbsent(name, index) }
        val missing = required - indexOf.keys
        if (missing.isNotEmpty()) throw OpenDataFormatException("Missing open-data columns: $missing")
        val keep = BooleanArray(header.size) { it in indexOf.values }
        val values = arrayOfNulls<String>(header.size)
        val value: (String) -> String? = { name -> indexOf[name]?.let { values[it] }?.trim()?.takeUnless { it in MISSING } }
        while (reader.readRecord({ it < keep.size && keep[it] }) { index, text -> values[index] = text }) {
            onRow(value)
            values.fill(null)
        }
    }
}

/** Maps open-data rows to domain candidates, keeping the TSE texts verbatim. */
internal object OpenDataMapper {
    /** `NM_COLIGACAO` repeats the grouping type when there is no coalition name. */
    private val GROUPING_PLACEHOLDERS = setOf("PARTIDO ISOLADO", "FEDERAÇÃO", "COLIGAÇÃO")
    private const val FEDERATION = "FEDERAÇÃO"
    private const val COALITION = "COLIGAÇÃO"
    private const val FIT = "APTO"
    private const val UNFIT = "INAPTO"
    private val PARTY_NUMBERS = 10..99

    /**
     * Votable candidacies of [ueCode] grouped by office code. A candidacy listed once per round
     * becomes one candidate whose totalization text is the latest one published. Vices and
     * Senate alternates (offices 2, 4, 9, 10, 12) are not ballot entries and are left out.
     */
    fun candidatesByOffice(
        election: Election,
        ueCode: String,
        rows: List<OpenDataCandidateRow>,
        statuses: Map<Long, OpenDataStatusRow>,
    ): Map<Int, List<Candidate>> = rows
        .filter { OfficeRules.isVotable(it.officeCode) && (it.ueCode == null || it.ueCode.equals(ueCode, ignoreCase = true)) }
        .groupBy { it.candidateId }
        .values
        .map { sameCandidacy -> toCandidate(election, ueCode, sameCandidacy, statuses[sameCandidacy.first().candidateId]) }
        .groupBy { it.officeCode }

    private fun toCandidate(
        election: Election,
        ueCode: String,
        rows: List<OpenDataCandidateRow>,
        status: OpenDataStatusRow?,
    ): Candidate {
        val row = rows.minBy { it.round ?: Int.MAX_VALUE }
        val totalization = rows.sortedByDescending { it.round ?: 0 }.firstNotNullOfOrNull { it.totalization }
        return Candidate(
            id = row.candidateId,
            electionId = election.id,
            ueCode = ueCode,
            officeCode = row.officeCode,
            number = row.number,
            ballotName = row.ballotName,
            // The social name, when registered, is the candidate's name (Res. TSE 23.609/2019).
            fullName = row.socialName ?: row.civilName,
            party = Party(
                acronym = row.partyAcronym,
                number = row.partyNumber?.takeIf { it in PARTY_NUMBERS } ?: Party.numberFromCandidateNumber(row.number),
                name = row.partyName,
            ),
            coalition = coalitionOf(row),
            status = CandidateStatus(
                registration = status?.judgement ?: status?.totalizationStatus ?: row.candidacyStatus.orEmpty(),
                totalization = totalization,
                onBallot = null,
                isFit = when (row.candidacyStatus?.uppercase()) {
                    FIT -> true
                    UNFIT -> false
                    else -> null
                },
            ),
            // No photos from open data: the photo host is the one that refused the request.
            photoUrl = null,
        )
    }

    private fun coalitionOf(row: OpenDataCandidateRow): String? {
        val coalition = row.coalition?.takeUnless { it.uppercase() in GROUPING_PLACEHOLDERS }
        return when (row.grouping?.uppercase()) {
            FEDERATION -> row.federation ?: coalition
            COALITION -> coalition ?: row.federation
            else -> coalition
        }
    }
}
