package com.veronezzi.colaeleitoral.data.repository

import com.veronezzi.colaeleitoral.core.network.TseJson
import com.veronezzi.colaeleitoral.data.mapper.toElectionOrNull
import com.veronezzi.colaeleitoral.data.remote.dto.EleicaoListSerializer
import com.veronezzi.colaeleitoral.domain.model.Election
import java.time.Instant
import java.time.LocalDate

/**
 * Bootstrap snapshot of the TSE election list, used only when the cache is empty and
 * `eleicao/ordinarias` cannot be read (the API blocked on a fresh install). Without it the
 * open-data fallback would have no election to serve. Values are a verbatim excerpt of the real
 * response captured at [CAPTURED_AT] (test fixture `tse/eleicoes-ordinarias.json`); the rows are
 * stored with that time as `fetchedAt`, so the UI shows them as old data and the next refresh
 * replaces them. The snapshot expires [VALID_DAYS_AFTER_LAST_ROUND] days after the last round
 * of its newest election, so it never stands in for elections it does not know.
 */
object ElectionSeed {
    val CAPTURED_AT: Instant = Instant.parse("2026-09-18T17:56:00Z")
    private const val VALID_DAYS_AFTER_LAST_ROUND = 30L

    private const val SNAPSHOT = """[
{"id":20322002026,"ano":2026,"nomeEleicao":"Eleição Geral Federal 2026","tipoEleicao":"O","tipoAbrangencia":"F","dataEleicao":"2026-10-04"},
{"id":2045202024,"ano":2024,"nomeEleicao":"Eleições Municipais 2024","tipoEleicao":"O","tipoAbrangencia":"M","dataEleicao":"2024-10-06"},
{"id":2040602022,"ano":2022,"nomeEleicao":"Eleição Geral Federal 2022","tipoEleicao":"O","tipoAbrangencia":"F","dataEleicao":"2022-10-02"},
{"id":2030402020,"ano":2020,"nomeEleicao":"Eleições Municipais 2020","tipoEleicao":"O","tipoAbrangencia":"M","dataEleicao":"2020-11-15"},
{"id":2032002020,"ano":2020,"nomeEleicao":"Eleições Municipais 2020 - AP","tipoEleicao":"O","tipoAbrangencia":"M","dataEleicao":"2020-11-14"},
{"id":2022802018,"ano":2018,"nomeEleicao":"Eleição Geral Federal 2018","tipoEleicao":"O","tipoAbrangencia":"F","dataEleicao":"2018-10-07"},
{"id":2,"ano":2016,"nomeEleicao":"Eleições Municipais 2016","tipoEleicao":"O","tipoAbrangencia":"M","dataEleicao":"2016-10-02"},
{"id":680,"ano":2014,"nomeEleicao":"Eleições Gerais 2014","tipoEleicao":"O","tipoAbrangencia":"F","dataEleicao":"2014-10-05"},
{"id":1699,"ano":2012,"nomeEleicao":"Eleição Municipal 2012","tipoEleicao":"O","tipoAbrangencia":"M","dataEleicao":"2012-10-07"},
{"id":14417,"ano":2010,"nomeEleicao":"Eleições 2010","tipoEleicao":"O","tipoAbrangencia":"F","dataEleicao":null},
{"id":14422,"ano":2008,"nomeEleicao":"Eleições 2008","tipoEleicao":"O","tipoAbrangencia":"M","dataEleicao":"2008-10-05"},
{"id":14423,"ano":2006,"nomeEleicao":"Eleições 2006","tipoEleicao":"O","tipoAbrangencia":"F","dataEleicao":null},
{"id":14431,"ano":2004,"nomeEleicao":"Eleições 2004","tipoEleicao":"O","tipoAbrangencia":"M","dataEleicao":null}
]"""

    val elections: List<Election> by lazy {
        TseJson.decodeFromString(EleicaoListSerializer, SNAPSHOT).mapNotNull { it.toElectionOrNull() }
    }

    /** The snapshot, or an empty list once it is too old to describe the current elections. */
    fun electionsFor(today: LocalDate): List<Election> {
        val lastRound = elections.mapNotNull { it.secondRoundDate ?: it.date }.maxOrNull() ?: return emptyList()
        return if (today.isAfter(lastRound.plusDays(VALID_DAYS_AFTER_LAST_ROUND))) emptyList() else elections
    }
}
