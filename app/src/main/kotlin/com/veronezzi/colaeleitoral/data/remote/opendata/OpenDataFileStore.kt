package com.veronezzi.colaeleitoral.data.remote.opendata

import com.veronezzi.colaeleitoral.core.network.TseBlockedException
import com.veronezzi.colaeleitoral.core.network.TseHttpStatusException
import com.veronezzi.colaeleitoral.core.network.TseUnusableResponseException
import com.veronezzi.colaeleitoral.core.network.await
import com.veronezzi.colaeleitoral.core.network.parseRetryAfterSeconds
import com.veronezzi.colaeleitoral.data.local.LocalStorageException
import com.veronezzi.colaeleitoral.data.local.storageResult
import com.veronezzi.colaeleitoral.data.remote.TseCallExecutor
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Writes the PII-free part of one ZIP entry to `output` while the ZIP streams in. */
fun interface OpenDataReducer {
    fun reduce(entry: InputStream, output: OutputStream)
}

/** The derived entries of one open-data ZIP, as of one download. */
class OpenDataSet internal constructor(
    /** Changes whenever the derived files change (a new download). */
    val version: String,
    private val directory: File,
) {
    /** The derived copy of [entryName] (case-insensitive), or null when the ZIP had no such entry. Blocking. */
    fun open(entryName: String): InputStream? {
        val file = File(directory, derivedName(entryName))
        return if (file.isFile) GZIPInputStream(file.inputStream().buffered()) else null
    }

    internal companion object {
        fun derivedName(entryName: String) = entryName.substringAfterLast('/').lowercase() + ".gz"
    }
}

/**
 * Derived copies of TSE open-data ZIPs in [directory] (inside `cacheDir`: public data the system
 * may purge). The ZIP itself never touches the disk (S5): it is read as a stream from the
 * response body, and each entry selected by `keep` goes through a reducer that writes only the
 * columns the app shows (gzipped). The CPF, título, e-mail and birth date of the candidates in the
 * original CSVs are never stored. Per ZIP, the folder holds the derived entries and a manifest
 * with the ETag; nothing else.
 *
 * A copy is revalidated with `If-None-Match` at most every [revalidateAfter]; the TSE answers 304
 * while the file is unchanged. Downloads never hold the lock [clear] needs: [clear] cancels them
 * (their callers get a failure), deletes everything and returns at once (A14).
 */
class OpenDataFileStore(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val directory: File,
    private val executor: TseCallExecutor,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxBytes: Long = MAX_FILE_BYTES,
    private val maxEntryBytes: Long = MAX_ENTRY_BYTES,
    private val revalidateAfter: Duration = REVALIDATE_AFTER,
) {
    /** Held only for short file operations (manifest reads, installs, deletions), never across I/O on the network. */
    private val stateMutex = Mutex()
    private val downloadMutexes = ConcurrentHashMap<String, Mutex>()
    private val validatedAt = HashMap<String, Instant>()
    private val generation = AtomicLong()
    private val downloads: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    /**
     * The derived entries of the ZIP at [path] (relative to the open-data base URL), downloaded
     * again when it changed. Only entries whose file name passes [keep] are derived, by [reducer].
     */
    suspend fun fetch(path: String, keep: (String) -> Boolean, reducer: OpenDataReducer): AppResult<OpenDataSet> =
        downloadMutexes.computeIfAbsent(path) { Mutex() }.withLock {
            val datasetDir = datasetDirFor(path)
            val (manifest, lastCheck) = stateMutex.withLock {
                withContext(ioDispatcher) { readManifest(datasetDir) } to validatedAt[path]
            }
            if (manifest != null && lastCheck != null && Duration.between(lastCheck, clock.instant()) < revalidateAfter) {
                return@withLock AppResult.Success(manifest.toSet(datasetDir))
            }
            val startedAt = generation.get()
            val result = cancellableByClear { executor.execute { download(path, manifest?.etag, keep, reducer) } }
                ?: return@withLock AppResult.Failure(AppError.Unknown(null))
            when (result) {
                is AppResult.Failure -> result
                is AppResult.Success -> stateMutex.withLock { commit(path, datasetDir, manifest, result.value, startedAt) }
            }
        }

    /** Drops the derived copy of [path] (for example after it turned out to be unreadable). */
    suspend fun invalidate(path: String) {
        stateMutex.withLock {
            validatedAt.remove(path)
            withContext(ioDispatcher) { datasetDirFor(path).deleteTree() }
        }
    }

    /** Cancels the downloads in flight and deletes every derived copy ("Limpar dados baixados"). */
    suspend fun clear() {
        generation.incrementAndGet()
        downloads.forEach { it.cancel() }
        stateMutex.withLock {
            validatedAt.clear()
            withContext(ioDispatcher) { directory.deleteTree() }
        }
    }

    /**
     * Startup cleanup: deletes what older versions kept here (the raw ZIPs with personal data of
     * the candidates, their `.etag` and `.part` files) and staging folders left by a killed process.
     */
    suspend fun deleteLegacyFiles() {
        stateMutex.withLock {
            withContext(ioDispatcher) {
                val staleBefore = clock.instant().minus(STAGING_MAX_AGE).toEpochMilli()
                directory.listFiles()?.forEach { file ->
                    when {
                        file.isFile -> file.delete()
                        file.name.startsWith(STAGING_PREFIX) && file.lastModified() < staleBefore -> file.deleteTree()
                    }
                }
            }
        }
    }

    /** Retention: deletes the derived copies not validated since [cutoff]. */
    suspend fun deleteUnusedSince(cutoff: Instant) {
        stateMutex.withLock {
            val deleted = withContext(ioDispatcher) {
                directory.listFiles().orEmpty()
                    .filter { it.isDirectory && !it.name.startsWith(STAGING_PREFIX) }
                    .filter { File(it, MANIFEST).lastModified() < cutoff.toEpochMilli() }
                    .onEach { it.deleteTree() }
                    .map { it.name }
                    .toSet()
            }
            validatedAt.keys.removeAll { datasetDirFor(it).name in deleted }
        }
    }

    private suspend fun download(path: String, etag: String?, keep: (String) -> Boolean, reducer: OpenDataReducer): Download {
        val url = baseUrl.resolve(path) ?: throw TseUnusableResponseException("Invalid open-data path")
        val request = Request.Builder()
            .url(url)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        return client.newCall(request).executeCancellable { response ->
            when {
                response.code == HTTP_NOT_MODIFIED && etag != null -> return@executeCancellable Download.NotModified
                response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY_REQUESTS ->
                    throw TseBlockedException(response.code, parseRetryAfterSeconds(response.header("Retry-After"), clock.instant()))
                !response.isSuccessful -> throw TseHttpStatusException(response.code)
                response.body.contentType()?.subtype?.contains("html", ignoreCase = true) == true ->
                    throw TseBlockedException(httpCode = null)
            }
            val staging = File(directory, "$STAGING_PREFIX${System.nanoTime()}")
            try {
                derive(response.body, staging, keep, reducer)
                Download.Fresh(staging, response.header("ETag"))
            } catch (e: Throwable) {
                staging.deleteTree()
                throw e
            }
        }
    }

    /** Streams the ZIP and writes the reduced entries into [staging]. Blocking. */
    private fun derive(body: ResponseBody, staging: File, keep: (String) -> Boolean, reducer: OpenDataReducer) {
        if (body.contentLength() > maxBytes) throw TseUnusableResponseException("Open-data file too large")
        if (!staging.mkdirs()) throw LocalStorageException(IOException("Could not create the open-data folder"))
        try {
            ZipInputStream(body.byteStream().limited(maxBytes, "Open-data file too large")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || !keep(name)) continue
                    val target = try {
                        File(staging, OpenDataSet.derivedName(name)).outputStream()
                    } catch (e: IOException) {
                        throw LocalStorageException(e)
                    }
                    GZIPOutputStream(StorageOutputStream(target).buffered()).use { output ->
                        reducer.reduce(zip.limited(maxEntryBytes, "Open-data entry too large").unclosable(), output)
                    }
                }
            }
        } catch (e: ZipException) {
            throw OpenDataFormatException("Unreadable open-data ZIP")
        }
    }

    private suspend fun commit(
        path: String,
        datasetDir: File,
        previous: Manifest?,
        download: Download,
        startedAt: Long,
    ): AppResult<OpenDataSet> = withContext(ioDispatcher) {
        if (generation.get() != startedAt) {
            // Cleared while downloading: the result belongs to data the user deleted.
            if (download is Download.Fresh) download.staging.deleteTree()
            return@withContext AppResult.Failure(AppError.Unknown(null))
        }
        val installed = storageResult {
            val manifest = when (download) {
                is Download.Fresh -> install(download, datasetDir)
                Download.NotModified -> requireNotNull(previous) { "304 without a previous copy" }
            }
            File(datasetDir, MANIFEST).setLastModified(clock.instant().toEpochMilli())
            manifest
        }
        if (installed is AppResult.Success) validatedAt[path] = clock.instant()
        if (installed is AppResult.Failure && download is Download.Fresh) download.staging.deleteTree()
        when (installed) {
            is AppResult.Success -> AppResult.Success(installed.value.toSet(datasetDir))
            is AppResult.Failure -> installed
        }
    }

    private fun install(download: Download.Fresh, datasetDir: File): Manifest {
        val manifest = Manifest(etag = download.etag, savedAt = clock.instant().toEpochMilli())
        File(download.staging, MANIFEST).writeText(manifest.encode())
        val old = File(directory, datasetDir.name + OLD_SUFFIX)
        old.deleteTree()
        if (datasetDir.exists() && !datasetDir.renameTo(old)) datasetDir.deleteTree()
        if (!download.staging.renameTo(datasetDir)) throw IOException("Could not store the open-data files")
        old.deleteTree()
        return manifest
    }

    private fun readManifest(datasetDir: File): Manifest? {
        val file = File(datasetDir, MANIFEST)
        if (!file.isFile) return null
        return try {
            Manifest.decode(file.readText())
        } catch (e: IOException) {
            null
        }
    }

    /** Null when [clear] cancelled the work while the caller itself is still active. */
    private suspend fun <T> cancellableByClear(block: suspend () -> T): T? = try {
        coroutineScope {
            val job = coroutineContext.job
            downloads += job
            try {
                block()
            } finally {
                downloads -= job
            }
        }
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    }

    /**
     * Runs [block] on the response on [ioDispatcher]. Cancelling the coroutine cancels the call,
     * which also interrupts a body that is still streaming (a blocking read would ignore it).
     */
    private suspend fun <T> Call.executeCancellable(block: (Response) -> T): T = coroutineScope {
        val call = this@executeCancellable
        val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.await().use { response -> withContext(ioDispatcher) { block(response) } }
        } finally {
            canceller.cancel()
        }
    }

    private fun datasetDirFor(path: String) = File(directory, path.substringAfterLast('/').removeSuffix(".zip"))

    private sealed interface Download {
        data object NotModified : Download

        class Fresh(val staging: File, val etag: String?) : Download
    }

    private class Manifest(val etag: String?, val savedAt: Long) {
        fun encode() = "${etag.orEmpty()}\n$savedAt\n"

        fun toSet(datasetDir: File) = OpenDataSet(version = "${etag.orEmpty()}@$savedAt", directory = datasetDir)

        companion object {
            fun decode(text: String): Manifest? {
                val lines = text.lines()
                val savedAt = lines.getOrNull(1)?.trim()?.toLongOrNull() ?: return null
                return Manifest(etag = lines[0].trim().takeIf { it.isNotEmpty() }, savedAt = savedAt)
            }
        }
    }

    /** Disk errors while deriving become [LocalStorageException], so they are not taken for network ones. */
    private class StorageOutputStream(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = storage { out.write(b, off, len) }

        override fun write(b: Int) = storage { out.write(b) }

        override fun flush() = storage { out.flush() }

        override fun close() = storage { out.close() }

        private inline fun storage(block: () -> Unit) {
            try {
                block()
            } catch (e: IOException) {
                throw LocalStorageException(e)
            }
        }
    }

    companion object {
        /** General elections are ~3 MB; the 64 MB municipal file is out of v1. */
        const val MAX_FILE_BYTES = 32L * 1024 * 1024

        /** Uncompressed size limit per CSV entry (the largest, SP, has 1.4 MB). */
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
        val REVALIDATE_AFTER: Duration = Duration.ofMinutes(5)
        private val STAGING_MAX_AGE: Duration = Duration.ofHours(1)
        private const val HTTP_NOT_MODIFIED = 304
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MANIFEST = "manifest"
        private const val STAGING_PREFIX = ".staging-"
        private const val OLD_SUFFIX = ".old"

        private fun InputStream.limited(max: Long, message: String): InputStream = object : FilterInputStream(this) {
            private var total = 0L

            override fun read(): Int = super.read().also { if (it != -1) count(1) }

            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it) }

            private fun count(bytes: Int) {
                total += bytes
                if (total > max) throw TseUnusableResponseException(message)
            }
        }

        /**
         * Deletes [this] and everything below it, tolerating a concurrent deletion of the same tree
         * ([clear] while a cancelled download removes its staging folder). Unlike
         * `deleteRecursively`, a folder that vanishes midway is not an error. Blocking.
         */
        private fun File.deleteTree() {
            listFiles()?.forEach { it.deleteTree() }
            delete()
        }

        /** The reducer may close its reader without closing the whole ZIP stream. */
        private fun InputStream.unclosable(): InputStream = object : FilterInputStream(this) {
            override fun close() = Unit
        }
    }
}
