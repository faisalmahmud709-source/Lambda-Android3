package com.lambda.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Reads a routine from a PDF, JPG or PNG completely offline (Tesseract OCR, English + Bangla)
 * and returns plain text that [RoutineParser] understands.
 */
object RoutineOcr {
    private const val MAX_SIDE = 2600
    private const val MAX_PDF_PAGES = 8
    private val lock = Any()

    private fun tessDir(context: Context): File {
        val root = File(context.filesDir, "tesseract")
        val dir = File(root, "tessdata").also { it.mkdirs() }
        listOf("eng", "ben").forEach { lang ->
            val f = File(dir, "$lang.traineddata")
            val asset = "tessdata/$lang.traineddata"
            if (!f.exists() || f.length() == 0L) {
                context.assets.open(asset).use { input -> FileOutputStream(f).use { input.copyTo(it) } }
            }
        }
        return root
    }

    /** @return recognised text, or null if the file could not be read at all. */
    suspend fun read(context: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
        synchronized(lock) {
            val app = context.applicationContext
            val mime = app.contentResolver.getType(uri).orEmpty().lowercase()
            val isPdf = mime.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")
            val tess = TessBaseAPI()
            try {
                if (!tess.init(tessDir(app).absolutePath, "ben+eng")) return@synchronized null
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                val sb = StringBuilder()
                if (isPdf) {
                    readPdf(app, uri) { bmp ->
                        tess.setImage(bmp)
                        sb.appendLine(tess.getUTF8Text().orEmpty())
                    }
                } else {
                    val bmp = decodeImage(app, uri) ?: return@synchronized null
                    tess.setImage(bmp)
                    sb.appendLine(tess.getUTF8Text().orEmpty())
                    bmp.recycle()
                }
                sb.toString().trim()
            } catch (e: Throwable) {
                null
            } finally {
                runCatching { tess.recycle() }
            }
        }
    }

    private fun readPdf(context: Context, uri: Uri, onPage: (Bitmap) -> Unit) {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, MAX_PDF_PAGES)) {
                    renderer.openPage(i).use { page ->
                        val scale = MAX_SIDE.toFloat() / maxOf(page.width, page.height)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE) // PDFs render on transparent otherwise
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        onPage(bmp)
                        bmp.recycle()
                    }
                }
            }
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        // honour the camera's rotation flag
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                when (ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        val big = maxOf(bmp.width, bmp.height)
        val scale = if (big > MAX_SIDE) MAX_SIDE.toFloat() / big else 1f
        if (degrees != 0f || scale != 1f) {
            val m = Matrix().apply { postRotate(degrees); postScale(scale, scale) }
            val t = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (t !== bmp) bmp.recycle()
            bmp = t
        }
        // flatten transparency (PNG) onto white
        if (bmp.hasAlpha()) {
            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
            bmp.recycle(); bmp = flat
        }
        return bmp
    }
}
