package com.veronezzi.colaeleitoral.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/** A10: the detail pane keeps one ViewModel, released as soon as another candidate is opened. */
class KeyedViewModelStoresTest {
    private class Probe : ViewModel() {
        var cleared = false

        init {
            addCloseable { cleared = true }
        }
    }

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = Probe() as T
    }

    private fun probeIn(store: ViewModelStore): Probe = ViewModelProvider.create(store, factory)[Probe::class]

    @Test
    fun `the same key keeps its ViewModel, another key clears it`() {
        val stores = KeyedViewModelStores()
        val first = probeIn(stores.storeFor(1L))
        assertSame(first, probeIn(stores.storeFor(1L)))
        assertFalse(first.cleared)

        val second = probeIn(stores.storeFor(2L))
        assertTrue(first.cleared)
        assertFalse(second.cleared)
    }

    @Test
    fun `clearing the host clears the current content`() {
        val host = ViewModelStore()
        val stores = ViewModelProvider.create(
            host,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = KeyedViewModelStores() as T
            },
        )[KeyedViewModelStores::class]
        val probe = probeIn(stores.storeFor("a"))
        host.clear()
        assertTrue(probe.cleared)
    }
}
