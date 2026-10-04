package com.veronezzi.meusantinho.domain.model

import java.time.LocalDateTime

/**
 * Official status texts, exactly as the TSE publishes them. The UI shows them verbatim (no
 * translation, no "simplified" labels, no colors that suggest good or bad); normalization is only
 * used for filtering.
 *
 * @property registration `descricaoSituacao` ("Deferido", "Indeferido", "Aguardando julgamento"...).
 * @property totalization `descricaoTotalizacao` ("Concorrendo", "2º turno", "Eleito"...).
 * @property onBallot `descricaoSituacaoCandidato` ("Consta da urna"...), only in the detail.
 * @property isFit `candidatoApto`, when sent.
 */
data class CandidateStatus(
    val registration: String,
    val totalization: String? = null,
    val onBallot: String? = null,
    val isFit: Boolean? = null,
) {
    /** True when the TSE marks the candidate as running in the second round. */
    val isInSecondRound: Boolean
        get() = totalization?.let { isSecondRoundText(it) } == true

    companion object {
        private val SECOND_ROUND = Regex("""^\s*2\s*[º°o]?\s*turno\s*$""", RegexOption.IGNORE_CASE)

        fun isSecondRoundText(text: String): Boolean = SECOND_ROUND.matches(text)
    }
}

/** A candidacy as shown in lists. [id] is the TSE `id` (SQ_CANDIDATO), unique within an election. */
data class Candidate(
    val id: Long,
    val electionId: Long,
    val ueCode: String,
    val officeCode: Int,
    val number: Int,
    val ballotName: String,
    val fullName: String?,
    val party: Party,
    val coalition: String?,
    val status: CandidateStatus,
    val photoUrl: String?,
)

/** Vice or Senate alternate (TSE `vices`). Texts are verbatim. */
data class RunningMate(
    val id: Long?,
    val role: String,
    val ballotName: String,
    val fullName: String?,
    val partyAcronym: String?,
    val photoUrl: String?,
    val status: String?,
)

/**
 * @property coalitionType `descricaoTipoDrap` ("Federação", "Coligação", "Partido Isolado").
 * @property coalitionComposition `composicaoColigacao`; null when the TSE sends the "**" placeholder.
 * @property officialPageUrl candidate page on DivulgaCandContas: always linked, it is the source.
 * @property photoPublishable `fotoUrlPublicavel`; when false the photo must not be shown.
 * @property lastUpdate `dataUltimaAtualizacao` ("yyyy-MM-dd HH:mm", Brasília time).
 */
data class CandidateDetail(
    val candidate: Candidate,
    val coalitionType: String?,
    val coalitionComposition: String?,
    val runningMates: List<RunningMate>,
    val officialPageUrl: String,
    val photoPublishable: Boolean?,
    val lastUpdate: LocalDateTime?,
)
