package com.veronezzi.meusantinho.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.veronezzi.meusantinho.domain.model.ElectoralUnit
import java.net.URI

/**
 * Official TSE addresses. Links open in the browser (no WebView, ARCHITECTURE.md 5.4) and only
 * `https` URLs on `tse.jus.br` are accepted from data, so manipulated data cannot send the user
 * elsewhere.
 */
object TseLinks {
    /** DivulgaCandContas home: the data source and the last-resort link (E7, rule 3). */
    const val DIVULGA_HOME = "https://divulgacandcontas.tse.jus.br/divulga/"

    /** TSE open-data portal, the fallback source of candidate lists (decision Q1). */
    const val OPEN_DATA_HOME = "https://dadosabertos.tse.jus.br/"

    fun isTseUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            (host == "tse.jus.br" || host.endsWith(".tse.jus.br"))
    }

    /** [url] when it is an official TSE address, else the DivulgaCandContas home. */
    fun officialOrHome(url: String?): String = url?.takeIf { isTseUrl(it) } ?: DIVULGA_HOME

    /**
     * Candidate page when the detail (and its `txLink`) is not available (E7, rule 2). The format
     * is known for general elections, where [ueCode] is a UF or "BR"; municipal elections get the
     * home page.
     */
    fun candidatePage(year: Int, electionId: Long, ueCode: String, candidateId: Long): String {
        val isGeneralUnit = ueCode == ElectoralUnit.BRAZIL_CODE ||
            (ueCode.length == 2 && ueCode.all { it.isLetter() })
        if (!isGeneralUnit) return DIVULGA_HOME
        return "${DIVULGA_HOME}#/candidato/$ueCode/$ueCode/$electionId/$candidateId/$year/$ueCode"
    }
}

/** Opens [url] in the browser. Returns false when no app can open it. */
fun Context.openInBrowser(url: String): Boolean = try {
    startActivity(
        Intent(Intent.ACTION_VIEW, url.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Opens the e-mail app addressed to [address]. Returns false when there is none. */
fun Context.writeEmail(address: String, subject: String): Boolean = try {
    startActivity(
        Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
