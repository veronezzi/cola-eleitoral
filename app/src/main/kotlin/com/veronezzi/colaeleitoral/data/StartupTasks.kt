package com.veronezzi.colaeleitoral.data

import android.content.Context
import com.veronezzi.colaeleitoral.core.common.ApplicationScope
import com.veronezzi.colaeleitoral.core.network.PhotoImageLoader
import com.veronezzi.colaeleitoral.data.remote.NetworkMonitor
import com.veronezzi.colaeleitoral.data.remote.opendata.OpenDataFileStore
import com.veronezzi.colaeleitoral.data.repository.CacheRetention
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Work of the data layer at every app start (from `ColaEleitoralApplication.onCreate`): watch
 * network changes, delete what older versions kept on disk (raw open-data ZIPs, S5; photos in
 * Coil's disk cache, S2) and apply the 60-day retention (S7). The file work runs in the
 * background on the application scope; whatever fails there is retried on the next start.
 */
@Singleton
class StartupTasks @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val retention: CacheRetention,
    private val openDataFiles: OpenDataFileStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    /** [alsoInBackground] runs with the file work (the UI's own cleanup of shared cola images). */
    fun run(alsoInBackground: () -> Unit = {}): Job {
        networkMonitor.start()
        return scope.launch {
            bestEffort { alsoInBackground() }
            bestEffort { PhotoImageLoader.deleteLegacyDiskCache(context) }
            bestEffort { openDataFiles.deleteLegacyFiles() }
            bestEffort { retention.cleanUp() }
        }
    }

    /** Maintenance must never crash the app at start; whatever fails is retried next time. */
    private inline fun bestEffort(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to report: no logs in release, and the user can't act on it.
        }
    }
}
