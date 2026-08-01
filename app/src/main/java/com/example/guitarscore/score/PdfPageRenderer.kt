package com.example.guitarscore.score

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * PDF 한 부를 열어 두고 페이지를 비트맵으로 렌더한다.
 *
 * PdfRenderer 는 한 번에 한 페이지만 열 수 있으므로 모든 접근을 [mutex] 로 직렬화한다.
 * 비트맵은 화면에 붙어 있는 동안 recycle 하면 크래시가 나므로 캐시에서 밀려나도 recycle 하지 않고
 * GC 에 맡긴다. 대신 캐시를 작게 유지해 메모리를 억제한다.
 */
class PdfPageRenderer(private val context: Context, private val uri: Uri) : AutoCloseable {
    private val mutex = Mutex()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val cache = linkedMapOf<Int, Bitmap>()
    @Volatile private var closed = false

    suspend fun open(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "renderer is closed" }
            if (renderer == null) {
                val opened = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("PDF 파일을 열 수 없습니다.")
                descriptor = opened
                renderer = PdfRenderer(opened)
            }
            requireNotNull(renderer).pageCount
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "renderer is closed" }
            cache.remove(pageIndex)?.let { cached ->
                cache[pageIndex] = cached // 접근한 항목을 뒤로 보내 LRU 로 동작시킨다.
                return@withLock cached
            }
            val pdfRenderer = requireNotNull(renderer) { "open() 을 먼저 호출해야 합니다." }
            pdfRenderer.openPage(pageIndex).use { page ->
                val width = targetWidth.coerceIn(320, MAX_RENDER_WIDTH)
                val height = (page.height * (width.toFloat() / page.width)).roundToInt().coerceAtLeast(320)
                // PdfRenderer 는 ARGB_8888 로만 그릴 수 있다. 다른 설정을 주면 렌더가 통째로 실패한다.
                // 메모리는 캐시 크기와 렌더 폭으로 잡는다.
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache[pageIndex] = bitmap
                while (cache.size > MAX_CACHED_PAGES) {
                    cache.remove(cache.keys.first())
                }
                bitmap
            }
        }
    }

    /** 창 밖으로 벗어난 페이지를 캐시에서 버린다. 스크롤 모드의 메모리를 일정하게 유지하는 용도. */
    suspend fun trimTo(keep: Set<Int>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                cache.keys.toList().filterNot { it in keep }.forEach { cache.remove(it) }
            }
        }
    }

    override fun close() {
        // 화면에 아직 붙어 있을 수 있으므로 비트맵은 recycle 하지 않는다.
        closed = true
        cache.clear()
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }

    private companion object {
        const val MAX_CACHED_PAGES = 4
        const val MAX_RENDER_WIDTH = 1_600
    }
}

/**
 * 썸네일은 화면에 오래 남아 있고 악보 수만큼 쌓이므로, 렌더가 끝난 뒤 RGB_565 로 복사해 절반만 들고 있는다.
 * (렌더 자체는 ARGB_8888 로만 할 수 있다.)
 */
suspend fun renderPdfThumbnail(context: Context, uri: Uri, targetWidth: Int = 360): Bitmap {
    val renderer = PdfPageRenderer(context.applicationContext, uri)
    return try {
        renderer.open()
        val rendered = renderer.renderPage(0, targetWidth)
        rendered.copy(Bitmap.Config.RGB_565, false) ?: rendered
    } finally {
        renderer.close()
    }
}
