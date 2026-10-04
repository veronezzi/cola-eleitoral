package com.veronezzi.colaeleitoral.data.mapper

import com.veronezzi.colaeleitoral.data.remote.RemoteOffice
import com.veronezzi.colaeleitoral.data.remote.dto.CandidatoDto
import com.veronezzi.colaeleitoral.data.remote.dto.CargoDto
import com.veronezzi.colaeleitoral.data.remote.dto.EleicaoDto
import com.veronezzi.colaeleitoral.data.remote.dto.UeDto
import com.veronezzi.colaeleitoral.data.remote.dto.ViceDto
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import com.veronezzi.colaeleitoral.domain.model.CandidateStatus
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.Party
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.RunningMate
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/*
 * DTO -> domain (ARCHITECTURE.md 2.3). Texts are kept verbatim (only trimmed); an item without
 * the fields the domain requires maps to null and is dropped by the caller.
 */

private const val ORDINARY_ELECTION = "O"
private const val GENERAL_SCOPE = "F"
private const val MUNICIPAL_SCOPE = "M"

/** `composicaoColigacao` placeholder for "no composition". */
private const val COMPOSITION_PLACEHOLDER = "**"

/** `situacaoVice` of a running mate who was replaced. */
private const val REPLACED_RUNNING_MATE = 3
private val PARTY_NUMBERS = 10..99
private val LAST_UPDATE = Regex("""^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})""")

internal fun String?.cleanText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

fun EleicaoDto.toElectionOrNull(): Election? {
    if (tipoEleicao.cleanText()?.uppercase() != ORDINARY_ELECTION) return null
    val id = id ?: return null
    val year = ano ?: return null
    val scope = when (tipoAbrangencia.cleanText()?.uppercase()) {
        GENERAL_SCOPE -> ElectionScope.GENERAL
        MUNICIPAL_SCOPE -> ElectionScope.MUNICIPAL
        else -> return null
    }
    val date = dataEleicao.cleanText()?.take(ISO_DATE_LENGTH)?.let { text ->
        try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            null
        }
    }
    val round = turno?.contentOrNull?.trim()?.toIntOrNull()?.let(Round::fromNumber)
    return Election(
        id = id,
        year = year,
        name = nomeEleicao.cleanText() ?: "Eleições $year",
        round = round,
        scope = scope,
        date = date,
    )
}

private const val ISO_DATE_LENGTH = 10

/** A municipality of E2. `sigla` is null there, so the UF comes from the query. */
fun UeDto.toMunicipalityOrNull(uf: String): ElectoralUnit? {
    val code = codigo.cleanText() ?: return null
    val name = nome.cleanText() ?: return null
    return ElectoralUnit(code = code, name = name, uf = sigla.cleanText() ?: uf, isMunicipality = true)
}

/** Votable offices only: vices and Senate alternates are elected with the head of the ticket. */
fun CargoDto.toRemoteOfficeOrNull(): RemoteOffice? {
    val code = codigo ?: return null
    if (!OfficeRules.isVotable(code)) return null
    val name = nome.cleanText() ?: OfficeRules.canonicalName(code) ?: return null
    return RemoteOffice(code = code, name = name, candidateCount = contagem?.takeIf { it >= 0 })
}

/**
 * A list item (E4). Dropped when it lacks id, number, ballot name or party, or belongs to another
 * office. Lists send `partido.numero = 0`, so the party number comes from the candidate number.
 */
fun CandidatoDto.toCandidateOrNull(electionId: Long, ueCode: String, officeCode: Int): Candidate? {
    val id = id ?: return null
    val number = numero?.takeIf { it > 0 } ?: return null
    val ballotName = nomeUrna.cleanText() ?: return null
    val acronym = partido?.sigla.cleanText() ?: return null
    val listedOffice = cargo?.codigo
    if (listedOffice != null && listedOffice != officeCode) return null
    return Candidate(
        id = id,
        electionId = electionId,
        ueCode = ueCode,
        officeCode = officeCode,
        number = number,
        ballotName = ballotName,
        fullName = nomeCompleto.cleanText(),
        party = Party(
            acronym = acronym,
            number = partido?.numero?.takeIf { it in PARTY_NUMBERS } ?: Party.numberFromCandidateNumber(number),
            name = partido?.nome.cleanText(),
        ),
        coalition = nomeColigacao.cleanText(),
        status = CandidateStatus(
            registration = descricaoSituacao.cleanText().orEmpty(),
            totalization = descricaoTotalizacao.cleanText(),
            onBallot = descricaoSituacaoCandidato.cleanText(),
            isFit = candidatoApto,
        ),
        photoUrl = photoUrlOrNull(electionId, id, ueCode),
    )
}

/** Lists have `fotoUrl = null`: the app builds the E6 URL. Never a photo marked not publishable. */
private fun CandidatoDto.photoUrlOrNull(electionId: Long, id: Long, ueCode: String): String? {
    if (fotoUrlPublicavel == false) return null
    return fotoUrl.cleanText()?.takeIf(TseLinks::isTseUrl) ?: TseLinks.photoUrl(electionId, id, ueCode)
}

/**
 * The detail (E5). [officeCodeHint] fills in a missing `cargo`. Replaced running mates
 * (`situacaoVice = 3`) are not current and are left out.
 */
fun CandidatoDto.toCandidateDetailOrNull(election: Election, ueCode: String, officeCodeHint: Int?): CandidateDetail? {
    val officeCode = cargo?.codigo ?: officeCodeHint ?: return null
    val candidate = toCandidateOrNull(election.id, ueCode, officeCode) ?: return null
    // Municipal UEs are numeric: the page link needs the state, sent as ufSuperiorCandidatura.
    val uf = if (ueCode.all(Char::isDigit)) ufSuperiorCandidatura.cleanText() else ueCode
    return CandidateDetail(
        candidate = candidate,
        coalitionType = descricaoTipoDrap.cleanText(),
        coalitionComposition = composicaoColigacao.cleanText()?.takeUnless { it == COMPOSITION_PLACEHOLDER },
        runningMates = vices
            .filter { it.situacaoVice != REPLACED_RUNNING_MATE }
            .mapNotNull { it.toRunningMateOrNull(officeCode) },
        officialPageUrl = TseLinks.officialPage(
            year = election.year,
            electionId = election.id,
            candidateId = candidate.id,
            ueCode = ueCode,
            uf = uf,
            links = eleicoesAnteriores.mapNotNull { link -> link.id.cleanText()?.let { it to link.txLink } },
        ),
        photoPublishable = fotoUrlPublicavel,
        lastUpdate = dataUltimaAtualizacao?.contentOrNull?.let(::parseLastUpdate),
    )
}

private fun ViceDto.toRunningMateOrNull(headOfficeCode: Int): RunningMate? {
    val name = nmUrna.cleanText() ?: return null
    return RunningMate(
        id = sqCandidato,
        role = dsCargo.cleanText() ?: defaultRunningMateRole(headOfficeCode),
        ballotName = name,
        fullName = nmCandidato.cleanText(),
        partyAcronym = sgPartido.cleanText(),
        photoUrl = urlFoto.cleanText()?.takeIf { urlFotoPublicavel != false && TseLinks.isTseUrl(it) },
        status = situacaoCandidato.cleanText(),
    )
}

/** Official office names, used only when the TSE omits `ds_CARGO`. */
private fun defaultRunningMateRole(headOfficeCode: Int): String = when (headOfficeCode) {
    OfficeRules.PRESIDENT -> "Vice-presidente"
    OfficeRules.GOVERNOR -> "Vice-governador"
    OfficeRules.MAYOR -> "Vice-prefeito"
    OfficeRules.SENATOR -> "Suplente"
    else -> ""
}

/** `dataUltimaAtualizacao` as "yyyy-MM-dd HH:mm" (Brasília time); any other format is null. */
internal fun parseLastUpdate(text: String): LocalDateTime? {
    val match = LAST_UPDATE.find(text.trim()) ?: return null
    return try {
        LocalDateTime.parse("${match.groupValues[1]}T${match.groupValues[2]}")
    } catch (e: DateTimeParseException) {
        null
    }
}
