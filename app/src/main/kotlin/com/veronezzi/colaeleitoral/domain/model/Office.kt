package com.veronezzi.colaeleitoral.domain.model

/**
 * An office (cargo) the voter types a number for, enriched with the urna rules.
 *
 * @property digitCount digits typed on the urna (Lei 9.504/97 art. 15; Res. TSE 23.609/2019 art. 14).
 * @property urnaOrder 1-based position on the urna (Lei 9.504/97 art. 59, § 3º).
 * @property maxPicks votes for this office in this round (2 for the Senate when two seats are open).
 * @property ueCode electoral unit the candidates are listed under ("BR" for President).
 * @property candidateCount TSE `contagem`, when known.
 */
data class Office(
    val code: Int,
    val name: String,
    val digitCount: Int,
    val urnaOrder: Int,
    val maxPicks: Int,
    val ueCode: String,
    val candidateCount: Int? = null,
)

/** Urna rules per TSE office code (codes checked against TSE data for 2024 and 2026). */
object OfficeRules {
    const val PRESIDENT = 1
    const val VICE_PRESIDENT = 2
    const val GOVERNOR = 3
    const val VICE_GOVERNOR = 4
    const val SENATOR = 5
    const val FEDERAL_DEPUTY = 6
    const val STATE_DEPUTY = 7
    const val DISTRICT_DEPUTY = 8
    const val FIRST_ALTERNATE = 9
    const val SECOND_ALTERNATE = 10
    const val MAYOR = 11
    const val VICE_MAYOR = 12
    const val COUNCILOR = 13

    private class Rule(val digits: Int, val urnaOrder: Int, val canonicalName: String)

    // Vices and Senate alternates are elected with the head of the ticket and have no own vote.
    private val rules: Map<Int, Rule> = mapOf(
        FEDERAL_DEPUTY to Rule(4, 1, "Deputado Federal"),
        STATE_DEPUTY to Rule(5, 2, "Deputado Estadual"),
        DISTRICT_DEPUTY to Rule(5, 2, "Deputado Distrital"),
        SENATOR to Rule(3, 3, "Senador"),
        GOVERNOR to Rule(2, 4, "Governador"),
        PRESIDENT to Rule(2, 5, "Presidente"),
        COUNCILOR to Rule(5, 1, "Vereador"),
        MAYOR to Rule(2, 2, "Prefeito"),
    )

    /** Offices that can have a second round (CF arts. 28, 29 II and 77). */
    private val runoffOffices = setOf(PRESIDENT, GOVERNOR, MAYOR)

    fun isVotable(code: Int): Boolean = code in rules

    fun hasRunoff(code: Int): Boolean = code in runoffOffices

    fun digitCount(code: Int): Int? = rules[code]?.digits

    fun urnaOrder(code: Int): Int? = rules[code]?.urnaOrder

    /** Official TSE office name, used when the TSE list is unavailable. */
    fun canonicalName(code: Int): String? = rules[code]?.canonicalName

    /**
     * Votes for [code] in [round] of a [year] election. The Senate renews one third and two thirds
     * of its seats alternately (CF art. 46, § 2º): two seats are open when `year % 8 == 2`
     * (2018, 2026, 2034...). TSE open data (`consulta_vagas_2026`) shows 2 seats in all 27 UFs in 2026.
     */
    fun maxPicks(code: Int, year: Int, round: Round): Int = when {
        !isVotable(code) -> 0
        round == Round.SECOND -> if (hasRunoff(code)) 1 else 0
        code == SENATOR && Math.floorMod(year, 8) == 2 -> 2
        else -> 1
    }

    /** Builds the [Office] for [code], or null when [code] is not voted on in [round]. */
    fun office(
        code: Int,
        name: String,
        ueCode: String,
        year: Int,
        round: Round,
        candidateCount: Int? = null,
    ): Office? {
        val rule = rules[code] ?: return null
        val picks = maxPicks(code, year, round)
        if (picks == 0) return null
        return Office(code, name, rule.digits, rule.urnaOrder, picks, ueCode, candidateCount)
    }

    /**
     * Office codes defined by law for a voter in [uf] (Lei 9.504/97 art. 59, § 3º), in urna order.
     * Used to render the ballot when the TSE office list cannot be loaded.
     */
    fun defaultCodes(scope: ElectionScope, uf: String): List<Int> = when (scope) {
        ElectionScope.GENERAL -> when (uf) {
            ElectoralUnit.ABROAD_CODE -> listOf(PRESIDENT)
            "DF" -> listOf(FEDERAL_DEPUTY, DISTRICT_DEPUTY, SENATOR, GOVERNOR, PRESIDENT)
            else -> listOf(FEDERAL_DEPUTY, STATE_DEPUTY, SENATOR, GOVERNOR, PRESIDENT)
        }
        ElectionScope.MUNICIPAL ->
            if (uf == "DF" || uf == ElectoralUnit.ABROAD_CODE) emptyList() else listOf(COUNCILOR, MAYOR)
    }

    /** UE under which [code] is listed: "BR" for President, the UF otherwise (general elections). */
    fun ueCodeFor(code: Int, location: VoterLocation, scope: ElectionScope): String? = when (scope) {
        ElectionScope.GENERAL -> if (code == PRESIDENT) ElectoralUnit.BRAZIL_CODE else location.uf
        ElectionScope.MUNICIPAL -> location.municipality?.code
    }
}
