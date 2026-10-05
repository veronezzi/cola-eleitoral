package com.veronezzi.colaeleitoral

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.veronezzi.colaeleitoral.core.network.PhotoImageLoader
import com.veronezzi.colaeleitoral.data.StartupTasks
import com.veronezzi.colaeleitoral.ui.screens.cola.ColaExport
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Application entry point. Owns the Hilt component and provides:
 * - WorkManager's configuration, so workers get their dependencies through [HiltWorkerFactory]
 *   (the default initializer is removed in the manifest; WorkManager starts on demand);
 * - Coil's image loader, on the app's own OkHttp client with a memory cache only
 *   ([PhotoImageLoader]); the client is lazy so the first frame does not wait for it;
 * - the startup maintenance ([StartupTasks]), plus deleting the cola images shared in a previous
 *   session (ARCHITECTURE.md 4.7), on every process start and not only when the activity is new.
 */
@HiltAndroidApp
class ColaEleitoralApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: dagger.Lazy<OkHttpClient>

    @Inject
    lateinit var startupTasks: StartupTasks

    override fun onCreate() {
        super.onCreate()
        startupTasks.run(alsoInBackground = { ColaExport.clearSharedFiles(this) })
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        PhotoImageLoader.create(context) { okHttpClient.get() }
}
