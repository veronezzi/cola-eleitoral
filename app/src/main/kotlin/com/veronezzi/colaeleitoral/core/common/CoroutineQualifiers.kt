package com.veronezzi.colaeleitoral.core.common

import javax.inject.Qualifier

/** Dispatcher for blocking I/O: files, ZIP and CSV parsing. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher for CPU work: mapping and filtering lists of up to ~1.500 candidates. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Process-wide scope for work that outlives screens (the DataStore instances). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
