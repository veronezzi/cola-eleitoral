package com.veronezzi.colaeleitoral.data.remote.opendata

import java.io.BufferedReader
import java.io.Reader

/**
 * Minimal reader for the TSE open-data CSVs: `;` separator, fields optionally in double quotes
 * (a doubled quote escapes one), CRLF or LF line ends.
 *
 * Only the columns the caller keeps become strings. Every other column (CPF, título de eleitor,
 * e-mail, birth date, gender, race...) is skipped character by character and never materialized.
 */
internal class SemicolonCsvReader(reader: Reader) {
    private val input: Reader = reader as? BufferedReader ?: BufferedReader(reader, BUFFER_SIZE)
    private val field = StringBuilder()
    private var pushedBack = NONE

    /** Reads the header record (all columns, trimmed; a leading BOM is skipped). Null on empty input. */
    fun readHeader(): List<String>? {
        val first = read()
        if (first != BOM) pushedBack = first
        val names = mutableListOf<String>()
        if (!readRecord({ true }) { _, value -> names += value }) return null
        return names.map { it.trim() }
    }

    /**
     * Reads one record and calls [onField] with (column index, value) for each column where
     * [keep] is true. Returns false at the end of the input.
     */
    fun readRecord(keep: (Int) -> Boolean, onField: (Int, String) -> Unit): Boolean {
        var c = read()
        if (c == EOF) return false
        var index = 0
        while (true) {
            val kept = keep(index)
            field.setLength(0)
            if (c == QUOTE) {
                c = read()
                while (c != EOF) {
                    if (c == QUOTE) {
                        c = read()
                        if (c != QUOTE) break
                    }
                    if (kept) field.append(c.toChar())
                    c = read()
                }
            }
            while (c != EOF && c != SEPARATOR && c != LF && c != CR) {
                if (kept) field.append(c.toChar())
                c = read()
            }
            if (kept) onField(index, field.toString())
            index++
            when (c) {
                SEPARATOR -> c = read()
                CR -> {
                    val next = read()
                    if (next != LF && next != EOF) pushedBack = next
                    return true
                }
                else -> return true
            }
        }
    }

    private fun read(): Int {
        val pending = pushedBack
        if (pending != NONE) {
            pushedBack = NONE
            return pending
        }
        return input.read()
    }

    private companion object {
        const val EOF = -1
        const val NONE = -2
        const val QUOTE = '"'.code
        const val SEPARATOR = ';'.code
        const val CR = '\r'.code
        const val LF = '\n'.code
        const val BUFFER_SIZE = 64 * 1024
        const val BOM = 0xFEFF
    }
}
