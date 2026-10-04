package com.veronezzi.colaeleitoral.ui.common

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The app is pt-BR only: dates use the Brazilian format whatever the device language. */
private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)
private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm", PT_BR)
private val DAY_MONTH_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM, HH:mm", PT_BR)

fun LocalDate.formatDate(): String = format(DATE)

/** TSE timestamps ("Atualizado pelo TSE em ...") are already in Brasília time. */
fun LocalDateTime.formatDateTime(): String = format(DATE_TIME)

/** Download time in the device's time zone ("Dados do TSE de 04/10, 14:32"). */
fun Instant.formatDayMonthTime(zone: ZoneId = ZoneId.systemDefault()): String =
    atZone(zone).format(DAY_MONTH_TIME)

/** Coarse age of a download, for "atualizado há X". */
sealed interface RelativeAge {
    data object JustNow : RelativeAge

    data class Minutes(val count: Int) : RelativeAge

    data class Hours(val count: Int) : RelativeAge

    data class Days(val count: Int) : RelativeAge
}

fun relativeAge(from: Instant, now: Instant): RelativeAge {
    val elapsed = Duration.between(from, now).let { if (it.isNegative) Duration.ZERO else it }
    return when {
        elapsed.toMinutes() < 1 -> RelativeAge.JustNow
        elapsed.toHours() < 1 -> RelativeAge.Minutes(elapsed.toMinutes().toInt())
        elapsed.toDays() < 1 -> RelativeAge.Hours(elapsed.toHours().toInt())
        else -> RelativeAge.Days(elapsed.toDays().toInt())
    }
}

/** A candidate number as typed on the urna: exactly [digitCount] digits when known. */
fun candidateNumberText(number: Int, digitCount: Int?): String {
    val text = number.toString()
    return if (digitCount != null && text.length < digitCount) text.padStart(digitCount, '0') else text
}

/**
 * Digits separated by spaces, so TalkBack announces a number digit by digit ("1 2 3", not
 * "cento e vinte e três"), which is how it is typed on the urna.
 */
fun spokenDigits(number: String): String = number.filter { it.isDigit() }.toCharArray().joinToString(" ")

/** Up to two initials of a ballot name, for the neutral avatar ("Maria da Silva" -> "MS"). */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("""\s+""")).filter { word -> word.firstOrNull()?.isLetterOrDigit() == true }
    val letters = when {
        words.isEmpty() -> ""
        words.size == 1 -> words.first().take(1)
        else -> words.first().take(1) + words.last().take(1)
    }
    return letters.uppercase(PT_BR)
}
