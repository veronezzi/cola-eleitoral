package com.veronezzi.colaeleitoral.di

import com.veronezzi.colaeleitoral.core.common.ApplicationScope
import com.veronezzi.colaeleitoral.core.common.DefaultDispatcher
import com.veronezzi.colaeleitoral.core.common.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

/**
 * Dispatchers, the application scope and the clock of the data layer. The UI has its own
 * constructor-injected `AppClock` and `UiDispatchers` (no module), so nothing here clashes.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    /** UTC system clock; every election rule converts to Brasília explicitly. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
