package com.veronezzi.colaeleitoral.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.veronezzi.colaeleitoral.data.repository.CachePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the default network (Wi-Fi, mobile data, VPN on or off). When it changes, a refusal
 * from the TSE says nothing about the new network, which is exactly what the "Tente outra rede"
 * message asks for: the source selector forgets its pause and every key that failed may be
 * downloaded again at once. Registering needs ACCESS_NETWORK_STATE (a normal permission).
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val policy: CachePolicy,
    private val selector: CandidateSourceSelector,
) {
    private val started = AtomicBoolean(false)

    /** Starts watching (once per process; called from the Application). */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            started.set(false)
            return
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (e: RuntimeException) {
            // SecurityException on a few Android 11 builds, TooManyRequestsException: no harm,
            // the limits then simply expire on their own.
            started.set(false)
        }
    }

    /** The default network is another one now. */
    fun onNetworkChanged() {
        selector.reset()
        policy.forgiveFailures()
    }

    internal val callback = object : ConnectivityManager.NetworkCallback() {
        // Callbacks arrive one at a time on the ConnectivityThread.
        private var current: Network? = null

        override fun onAvailable(network: Network) {
            if (network != current) {
                current = network
                onNetworkChanged()
            }
        }

        override fun onLost(network: Network) {
            if (network == current) current = null
        }
    }
}
