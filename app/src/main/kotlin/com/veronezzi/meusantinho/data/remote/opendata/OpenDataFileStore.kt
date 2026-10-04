package com.veronezzi.meusantinho.data.remote.opendata

import com.veronezzi.meusantinho.core.network.TseBlockedException
import com.veronezzi.meusantinho.core.network.TseHttpStatusException
import com.veronezzi.meusantinho.core.network.TseUnusableResponseException
import com.veronezzi.meusantinho.core.network.await
import com.veronezzi.meusantinho.core.network.parseRetryAfterSeconds
import com.veronezzi.meusantinho.data.remote.TseCallExecutor
import com.veronezzi.meusantinho.domain.model.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Local copies of TSE open-data ZIPs in [directory] (inside `cacheDir`: public data the system
 * may purge). A copy is revalidated with `If-None-Match` at most every [revalidateAfter]; the
 * TSE answers 304 while the file is unchanged, so a refresh usually costs a few hundred bytes.
 */
class OpenDataFileStore(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val directory: File,
    private val executor: TseCallExecutor,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxBytes: Long = MAX_FILE_BYTES,
    private val revalidateAfter: Duration = REVALIDATE_AFTER,
) {
    private val mutex = Mutex()
    private val validatedAt = HashMap<String, Instant>()

    /** The local copy of [path] (relative to the open-data base URL), downloaded when it changed. */
    suspend fun fetch(path: String): AppResult<File> = mutex.withLock {
        val file = fileFor(path)
        val lastCheck = validatedAt[path]
        if (lastCheck != null && Duration.between(lastCheck, clock.instant()) < revalidateAfter &&
            withContext(ioDispatcher) { file.isFile }
        ) {
            return@withLock AppResult.Success(file)
        }
        val result = executor.execute { download(path, file) }
        if (result is AppResult.Success) validatedAt[path] = clock.instant()
        result
    }

    /** Drops the local copy of [path] (for example after it turned out to be corrupt). */
    suspend fun invalidate(path: String) {
        mutex.withLock {
            validatedAt.remove(path)
            withContext(ioDispatcher) {
                fileFor(path).delete()
                etagFileFor(fileFor(path)).delete()
            }
        }
    }

    /** Deletes every local copy ("Limpar dados baixados"). */
    suspend fun clear() {
        mutex.withLock {
            validatedAt.clear()
            withContext(ioDispatcher) { directory.listFiles()?.forEach { it.delete() } }
        }
    }

    private suspend fun download(path: String, file: File): File {
        val etagFile = etagFileFor(file)
        val etag = withContext(ioDispatcher) {
            if (file.isFile && etagFile.isFile) etagFile.readText().trim().takeIf { it.isNotEmpty() } else null
        }
        val url = baseUrl.resolve(path) ?: throw TseUnusableResponseException("Invalid open-data path")
        val request = Request.Builder()
            .url(url)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        client.newCall(request).await().use { response ->
            when {
                response.code == HTTP_NOT_MODIFIED && etag != null -> return file
                response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY_REQUESTS ->
                    throw TseBlockedException(
                        response.code,
                        parseRetryAfterSeconds(response.header("Retry-After"), clock.instant()),
                    )
                !response.isSuccessful -> throw TseHttpStatusException(response.code)
                response.body.contentType()?.subtype?.contains("html", ignoreCase = true) == true ->
                    throw TseBlockedException(httpCode = null)
            }
            withContext(ioDispatcher) { save(response, file, etagFile) }
        }
        return file
    }

    private fun save(response: Response, file: File, etagFile: File) {
        val body = response.body
        if (body.contentLength() > maxBytes) throw TseUnusableResponseException("Open-data file too large")
        directory.mkdirs()
        val partial = File(directory, file.name + PARTIAL_SUFFIX)
        try {
            partial.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > maxBytes) throw TseUnusableResponseException("Open-data file too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            etagFile.delete()
            if (!partial.renameTo(file)) throw IOException("Could not store the open-data file")
            response.header("ETag")?.let { etagFile.writeText(it) }
        } finally {
            partial.delete()
        }
    }

    private fun fileFor(path: String) = File(directory, path.substringAfterLast('/'))

    private fun etagFileFor(file: File) = File(directory, file.name + ETAG_SUFFIX)

    companion object {
        /** General elections are ~3 MB; the 64 MB municipal file is out of v1. */
        const val MAX_FILE_BYTES = 32L * 1024 * 1024
        val REVALIDATE_AFTER: Duration = Duration.ofMinutes(5)
        private const val HTTP_NOT_MODIFIED = 304
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val PARTIAL_SUFFIX = ".part"
        private const val ETAG_SUFFIX = ".etag"
    }
}
