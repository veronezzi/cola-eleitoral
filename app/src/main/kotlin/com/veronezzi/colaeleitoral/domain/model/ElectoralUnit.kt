package com.veronezzi.colaeleitoral.domain.model

/**
 * TSE "unidade eleitoral" (UE): "BR" for President, a UF code ("SP", "DF") for the other offices
 * of a general election, or the 5-digit TSE municipality code of a municipal election. Codes are
 * text: municipality codes have leading zeros ("01120").
 */
data class ElectoralUnit(
    val code: String,
    val name: String,
    val uf: String,
    val isMunicipality: Boolean,
) {
    companion object {
        const val BRAZIL_CODE = "BR"

        /** Voters registered abroad (TSE UF "ZZ") vote only for President (Código Eleitoral, art. 225). */
        const val ABROAD_CODE = "ZZ"

        val BRAZIL = ElectoralUnit(BRAZIL_CODE, "Brasil", BRAZIL_CODE, isMunicipality = false)
    }
}

/** The 27 federative units (proper nouns, used as data). */
object FederativeUnits {
    val all: List<ElectoralUnit> = listOf(
        "AC" to "Acre", "AL" to "Alagoas", "AM" to "Amazonas", "AP" to "Amapá",
        "BA" to "Bahia", "CE" to "Ceará", "DF" to "Distrito Federal", "ES" to "Espírito Santo",
        "GO" to "Goiás", "MA" to "Maranhão", "MG" to "Minas Gerais", "MS" to "Mato Grosso do Sul",
        "MT" to "Mato Grosso", "PA" to "Pará", "PB" to "Paraíba", "PE" to "Pernambuco",
        "PI" to "Piauí", "PR" to "Paraná", "RJ" to "Rio de Janeiro", "RN" to "Rio Grande do Norte",
        "RO" to "Rondônia", "RR" to "Roraima", "RS" to "Rio Grande do Sul", "SC" to "Santa Catarina",
        "SE" to "Sergipe", "SP" to "São Paulo", "TO" to "Tocantins",
    ).map { (code, name) -> ElectoralUnit(code, name, code, isMunicipality = false) }

    fun byCode(code: String): ElectoralUnit? = all.firstOrNull { it.code == code }
}

/**
 * Where the user votes. [uf] is a federative unit code or [ElectoralUnit.ABROAD_CODE];
 * [municipality] is only needed for municipal elections.
 */
data class VoterLocation(
    val uf: String,
    val municipality: ElectoralUnit? = null,
) {
    val isAbroad: Boolean get() = uf == ElectoralUnit.ABROAD_CODE

    /** UE codes whose offices make up this voter's ballot. Empty when the voter has no vote. */
    fun ballotUnitCodes(scope: ElectionScope): List<String> = when (scope) {
        ElectionScope.GENERAL ->
            if (isAbroad) listOf(ElectoralUnit.BRAZIL_CODE) else listOf(ElectoralUnit.BRAZIL_CODE, uf)
        ElectionScope.MUNICIPAL ->
            if (isAbroad || uf == "DF") emptyList() else listOfNotNull(municipality?.code)
    }
}
