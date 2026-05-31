package de.openbahn.navigator.ui.tickets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun renderPdfPages(context: Context, uriString: String): List<ImageBitmap> =
    withContext(Dispatchers.IO) {
        val file = resolveTicketFile(context, uriString) ?: return@withContext emptyList()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = 2
                        val width = page.width * scale
                        val height = page.height * scale
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap.asImageBitmap()
                    }
                }
            }
        }
    }

internal fun resolveTicketFile(context: Context, uriString: String): File? {
    return runCatching {
        when {
            uriString.startsWith("file:") -> File(Uri.parse(uriString).path ?: return null)
            uriString.startsWith("/") -> File(uriString)
            else -> {
                val uri = Uri.parse(uriString)
                when (uri.scheme) {
                    "file" -> File(uri.path ?: return null)
                    "content" -> {
                        val name = uri.lastPathSegment ?: return null
                        File(context.getDir("tickets", Context.MODE_PRIVATE), name)
                            .takeIf { it.exists() }
                    }
                    else -> null
                }
            }
        }
    }.getOrNull()?.takeIf { it.exists() && it.canRead() }
}
