package com.veronezzi.colaeleitoral.ui.common

import com.veronezzi.colaeleitoral.domain.model.ElectionCalendar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Time source of the UI layer. Production uses the system clock; tests pass a fixed [Clock].
 * Constructor-injected, so it needs no Hilt module (and cannot clash with the data layer's).
 */
class AppClock(private val clock: Clock) {
    @Inject
    constructor() : this(Clock.systemUTC())

    fun now(): Instant = clock.instant()

    /** Today in Brasília: every election date and the polling hours are in Brasília time. */
    fun todayInBrasilia(): LocalDate = LocalDate.now(clock.withZone(ElectionCalendar.BRASILIA))
}

/** Dispatchers used by ViewModels for CPU work (filtering up to ~1.500 candidates). */
class UiDispatchers(val default: CoroutineDispatcher) {
    @Inject
    constructor() : this(Dispatchers.Default)
}
