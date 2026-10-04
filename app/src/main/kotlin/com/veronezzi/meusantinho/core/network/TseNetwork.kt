package com.veronezzi.meusantinho.core.network

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

    /** Honest User-Agent: the app's own name and version, never a browser's. */
    fun userAgent(versionName: String): String = "MeuSantinho/$versionName (Android)"
}
