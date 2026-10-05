package com.veronezzi.colaeleitoral.core.network

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import okhttp3.Call
import java.io.File

/**
 * Coil's image loader for candidate photos (ARCHITECTURE.md 2.9, S2, S4). Photos go through the
 * app's own OkHttp ([callFactory]: app User-Agent, TSE host allowlist on every redirect hop, at
 * most 4 connections per host, no cookies) and are kept only in a memory cache: there is no disk
 * cache, so the photo of a candidate whose detail was opened never stays on the device. The
 * ServiceLoader is off, so Coil cannot pick up its own default `OkHttpClient` instead.
 */
object PhotoImageLoader {
    /** Share of the app's memory for decoded photos. */
    private const val MEMORY_CACHE_PERCENT = 0.15

    /** Coil's default disk cache folder, which builds before this change filled with photos. */
    private const val LEGACY_DISK_CACHE_DIR = "image_cache"

    fun create(context: Context, callFactory: () -> Call.Factory): ImageLoader = ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = callFactory)) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
        .diskCache { null }
        .build()

    /** Deletes the photos an older version cached on disk. Blocking. */
    fun deleteLegacyDiskCache(context: Context) {
        File(context.cacheDir, LEGACY_DISK_CACHE_DIR).deleteRecursively()
    }
}
