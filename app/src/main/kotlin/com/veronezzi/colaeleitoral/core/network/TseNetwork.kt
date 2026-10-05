package com.veronezzi.colaeleitoral.core.network

import kotlinx.serialization.json.Json

/**
 * Parser for TSE responses (ARCHITECTURE.md 2.3). The API sends ~85 keys per candidacy and
 * changes without notice: unknown keys are ignored, a null in a field with a default becomes the
 * default, and numbers and strings are accepted either way.
 */
val TseJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

/** TSE hosts and URLs. Nothing else is reachable ([HostAllowlistInterceptor]). */
object TseEndpoints {
    const val API_HOST = "divulgacandcontas.tse.jus.br"
    const val OPEN_DATA_HOST = "cdn.tse.jus.br"
    const val API_BASE_URL = "https://divulgacandcontas.tse.jus.br/divulga/rest/v1/"
    const val OPEN_DATA_BASE_URL = "https://cdn.tse.jus.br/estatistica/sead/odsele/"
    const val PHOTO_BASE_URL = "https://divulgacandcontas.tse.jus.br/divulga/rest/arquivo/img/"
    const val PORTAL_URL = "https://divulgacandcontas.tse.jus.br/divulga/"

    val ALLOWED_HOSTS: Set<String> = setOf(API_HOST, OPEN_DATA_HOST)

    /**
     * Honest User-Agent (ARCHITECTURE.md 2.7): the app's name and version, the Android release and
     * where to read about the app, never a browser's.
     * Example: `ColaEleitoral/1.0.0 (Android 14; +https://veronezzi.github.io/cola-eleitoral-privacidade/)`.
     */
    fun userAgent(versionName: String, androidRelease: String, privacyPolicyUrl: String): String =
        "ColaEleitoral/${token(versionName)} (Android ${token(androidRelease)}; +${privacyPolicyUrl.filter(::isUrlChar)})"

    /** Header values must be printable ASCII; a custom ROM may report anything as its release. */
    private fun token(text: String): String = text.filter { it.isLetterOrDigit() && it.code < ASCII_LIMIT || it in "._-" }
        .ifEmpty { "unknown" }

    private fun isUrlChar(c: Char): Boolean = c.code in ASCII_PRINTABLE && c !in " ()<>\";\\"

    private const val ASCII_LIMIT = 128
    private val ASCII_PRINTABLE = 0x21..0x7E
}
