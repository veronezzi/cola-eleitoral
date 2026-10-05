package com.veronezzi.colaeleitoral.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The [ViewModelStore] of the content shown for one key, kept in the store of the screen that
 * hosts it (for example the candidate open in the detail pane of the list). Changing the key
 * clears the previous content's ViewModels at once; the host being cleared clears the rest. It
 * lives in a ViewModel, so the content keeps its ViewModels across rotation.
 */
class KeyedViewModelStores : ViewModel() {
    private var key: Any? = null
    private var store: ViewModelStore? = null

    /** The store for [key]; the store of any other key is cleared first. */
    fun storeFor(key: Any): ViewModelStore {
        store?.takeIf { this.key == key }?.let { return it }
        store?.clear()
        return ViewModelStore().also {
            this.key = key
            store = it
        }
    }

    override fun onCleared() {
        store?.clear()
        store = null
        key = null
    }
}

/**
 * A [ViewModelStoreOwner] for the content of [key] (see [KeyedViewModelStores]). It reuses the
 * host's default factory and creation extras, so `hiltViewModel()` works inside it.
 */
@Composable
fun rememberKeyedViewModelStoreOwner(key: Any): ViewModelStoreOwner {
    val host = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner for the keyed content" }
    val stores: KeyedViewModelStores = viewModel(viewModelStoreOwner = host, key = KEYED_STORES)
    return remember(host, key) { KeyedOwner(stores.storeFor(key), host as? HasDefaultViewModelProviderFactory) }
}

private class KeyedOwner(
    override val viewModelStore: ViewModelStore,
    private val factoryHost: HasDefaultViewModelProviderFactory?,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = factoryHost?.defaultViewModelProviderFactory ?: ViewModelProvider.NewInstanceFactory()

    override val defaultViewModelCreationExtras: CreationExtras
        get() = factoryHost?.defaultViewModelCreationExtras ?: CreationExtras.Empty
}

private const val KEYED_STORES = "keyed-viewmodel-stores"
