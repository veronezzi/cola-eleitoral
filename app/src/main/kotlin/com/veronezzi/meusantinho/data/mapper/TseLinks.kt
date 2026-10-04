package com.veronezzi.meusantinho.data.mapper

import com.veronezzi.meusantinho.core.network.TseEndpoints

/** URLs the app builds for the TSE (ARCHITECTURE.md 2.2, E6 and E7). Only TSE domains. */
object TseLinks {
    private val UF = Regex("^[A-Z]{2}$")

    /** E6: candidate photo. */
    fun photoUrl(electionId: Long, candidateId: Long, ueCode: String): String =
        "${TseEndpoints.PHOTO_BASE_URL}$electionId/$candidateId/$ueCode"

    /**
     * E7: the candidate's official page. Prefers the `txLink` the TSE generated for this candidacy
     * ([links] are `eleicoesAnteriores` id/link pairs), then the documented page format, then the
     * portal home.
     */
    fun officialPage(
        year: Int,
        electionId: Long,
        candidateId: Long,
        ueCode: String,
        uf: String?,
        links: List<Pair<String, String?>> = emptyList(),
    ): String {
        val own = links.firstOrNull { (id, _) -> id == candidateId.toString() }?.second?.trim()
        if (own != null && isTseUrl(own)) return own
        val region = uf?.trim()?.uppercase()?.takeIf { UF.matches(it) } ?: return TseEndpoints.PORTAL_URL
        return "${TseEndpoints.PORTAL_URL}#/candidato/$region/$region/$electionId/$candidateId/$year/$ueCode"
    }

    /** True for HTTPS URLs on DivulgaCandContas: anything else from the API is ignored. */
    fun isTseUrl(url: String): Boolean = url.startsWith("https://${TseEndpoints.API_HOST}/")
}
