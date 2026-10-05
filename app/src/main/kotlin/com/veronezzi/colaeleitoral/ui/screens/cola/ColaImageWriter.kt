package com.veronezzi.colaeleitoral.ui.screens.cola

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Writes the PNG of the cola to share ([ColaExport.writeImage]) on the I/O dispatcher. Built from
 * a function so [ColaExportViewModel] is tested without Android; production uses the injected
 * constructor (no Hilt module needed, like [com.veronezzi.colaeleitoral.ui.common.AppClock]).
 */
class ColaImageWriter(private val writer: suspend (ColaContent) -> File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ content ->
        withContext(Dispatchers.IO) { ColaExport.writeImage(context, content) }
    })

    /** Throws when the image can't be written (disk full, I/O error). */
    suspend fun write(content: ColaContent): File = writer(content)
}
