package com.veronezzi.colaeleitoral.domain.model

/**
 * @property number party number, or null if unknown. TSE list responses send `partido.numero = 0`;
 * use [numberFromCandidateNumber], since a candidate number always starts with the party number
 * (Lei 9.504/97 art. 15; Res. TSE 23.609/2019 art. 14).
 */
data class Party(
    val acronym: String,
    val number: Int?,
    val name: String? = null,
) {
    companion object {
        fun numberFromCandidateNumber(candidateNumber: Int): Int? =
            candidateNumber.toString().take(2).toIntOrNull()?.takeIf { it in 10..99 }
    }
}
