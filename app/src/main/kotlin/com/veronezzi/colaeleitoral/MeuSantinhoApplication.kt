package com.veronezzi.colaeleitoral

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Owns the Hilt component and provides WorkManager's configuration so
 * workers get their dependencies through [HiltWorkerFactory]. WorkManager's default initializer is
 * removed in the manifest, so WorkManager initializes on demand with this configuration.
 */
@HiltAndroidApp
class ColaEleitoralApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
