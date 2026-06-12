package com.example.guitarscore.score

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class PdfPageRenderer(private val context: Context, private val uri: Uri) : AutoCloseable {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val cache = linkedMapOf<Int, Bitmap>()

    suspend fun open(): Int = withContext(Dispatchers.IO) {
        if (renderer == null) {
            descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            renderer = PdfRenderer(requireNotNull(descriptor))
        }
        requireNotNull(renderer).pageCount
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        cache[pageIndex]?.let { return@withContext it }
        val pdfRenderer = requireNotNull(renderer)
        pdfRenderer.openPage(pageIndex).use { page ->
            val scale = targetWidth.toFloat() / page.width.toFloat()
            val width = targetWidth.coerceAtLeast(320)
            val height = (page.height * scale).roundToInt().coerceAtLeast(320)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache[pageIndex] = bitmap
            while (cache.size > 48) {
                val firstKey = cache.keys.first()
                cache.remove(firstKey)?.recycle()
            }
            bitmap
        }
    }

    override fun close() {
        cache.values.forEach { it.recycle() }
        cache.clear()
        renderer?.close()
        descriptor?.close()
        renderer = null
        descriptor = null
    }
}
