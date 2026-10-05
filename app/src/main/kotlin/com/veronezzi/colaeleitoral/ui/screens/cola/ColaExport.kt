package com.veronezzi.colaeleitoral.ui.screens.cola

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Exports of the cola. PDF goes through the Android print framework (the system dialog offers
 * "Salvar como PDF"); the image is a PNG in `cacheDir/shared/`, the only directory the
 * non-exported FileProvider exposes, granted read-only to the app the user shares with. The
 * file is deleted on the next app start ([clearSharedFiles]).
 */
object ColaExport {
    private const val SHARED_DIR = "shared"

    fun print(context: Context, content: ColaContent, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        printManager.print(jobName, ColaPrintAdapter(context, content, jobName), attributes)
    }

    /** Writes the PNG (call off the main thread) and returns the file to share. */
    fun writeImage(context: Context, content: ColaContent): File {
        val directory = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val file = File(directory, "minha-cola-${System.currentTimeMillis()}.png")
        val bitmap = ColaRenderer().renderBitmap(content)
        try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    /** Opens the share sheet for [file]. Returns false when no app can receive it. */
    fun share(context: Context, file: File, chooserTitle: String): Boolean {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: IllegalArgumentException) {
            // Outside the FileProvider paths (should never happen): nothing is shared.
            return false
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return try {
            context.startActivity(
                Intent.createChooser(send, chooserTitle)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Deletes the images left by previous shares. */
    fun clearSharedFiles(context: Context) {
        File(context.cacheDir, SHARED_DIR).listFiles()?.forEach { it.delete() }
    }
}

/** One A4 page, drawn by [ColaRenderer] inside the printable area. */
private class ColaPrintAdapter(
    private val context: Context,
    private val content: ColaContent,
    private val documentName: String,
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        val info = PrintDocumentInfo.Builder("$documentName.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val printAttributes = attributes ?: run {
            callback.onWriteFailed(null)
            return
        }
        val document = PrintedPdfDocument(context, printAttributes)
        try {
            val page = document.startPage(0)
            val area = page.info.contentRect
            page.canvas.save()
            page.canvas.translate(area.left.toFloat(), area.top.toFloat())
            ColaRenderer().draw(page.canvas, content, area.width().toFloat(), area.height().toFloat())
            page.canvas.restore()
            document.finishPage(page)
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }
            FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        } finally {
            document.close()
        }
    }
}
