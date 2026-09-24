package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfRendererEngine(
    private val context: Context,
    val uri: Uri,
    private val fallbackTitle: String? = null
) {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val memoryCache: LruCache<String, Bitmap>

    var pageCount: Int = 0
        private set

    init {
        // Cache approximately 30MB of bitmaps
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    @Synchronized
    fun open(): Boolean {
        return try {
            fileDescriptor = PdfFileHelper.openParcelFileDescriptor(context, uri, fallbackTitle)
            if (fileDescriptor != null) {
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer?.pageCount ?: 0
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private val pageRatioCache = mutableMapOf<Int, Float>()

    @Synchronized
    fun getPageAspectRatio(pageIndex: Int): Float {
        if (pageIndex < 0 || pageIndex >= pageCount) return 0.707f
        pageRatioCache[pageIndex]?.let { return it }
        val renderer = pdfRenderer ?: return 0.707f
        return try {
            val page = renderer.openPage(pageIndex)
            val w = page.width.toFloat()
            val h = page.height.toFloat()
            page.close()
            val ratio = if (h > 0f) (w / h).coerceIn(0.2f, 3.0f) else 0.707f
            pageRatioCache[pageIndex] = ratio
            ratio
        } catch (e: Exception) {
            0.707f
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidth: Int = 1200,
        targetHeight: Int = 1800,
        cropMargins: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null
        val cacheKey = "$pageIndex-$targetWidth-$targetHeight-$cropMargins"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        synchronized(this@PdfRendererEngine) {
            val renderer = pdfRenderer ?: return@withContext null
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)
                val pageWidth = page.width
                val pageHeight = page.height

                // Calculate scaling maintaining aspect ratio
                val scale = minOf(
                    targetWidth.toFloat() / pageWidth.toFloat(),
                    targetHeight.toFloat() / pageHeight.toFloat()
                ).coerceIn(1.0f, 3.5f)

                val scaledWidth = (pageWidth * scale).toInt().coerceAtLeast(100)
                val scaledHeight = (pageHeight * scale).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                // Fill pure white background
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val finalBitmap = if (cropMargins) {
                    cropWhiteMargins(bitmap)
                } else {
                    bitmap
                }

                memoryCache.put(cacheKey, finalBitmap)
                return@withContext finalBitmap
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            } finally {
                try {
                    page?.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun cropWhiteMargins(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        // Remove approximately 6% horizontal and 4% vertical outer padding
        val marginX = (width * 0.06f).toInt()
        val marginY = (height * 0.04f).toInt()
        val newWidth = width - (marginX * 2)
        val newHeight = height - (marginY * 2)
        if (newWidth <= 50 || newHeight <= 50) return source
        return try {
            Bitmap.createBitmap(source, marginX, marginY, newWidth, newHeight)
        } catch (e: Exception) {
            source
        }
    }

    suspend fun generateCoverThumbnail(): String? = withContext(Dispatchers.IO) {
        val coverBitmap = renderPage(0, targetWidth = 400, targetHeight = 600, cropMargins = false)
            ?: return@withContext null
        return@withContext try {
            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val fileName = "cover_${Math.abs(uri.toString().hashCode())}.png"
            val file = File(coversDir, fileName)
            FileOutputStream(file).use { out ->
                coverBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Synchronized
    fun close() {
        try {
            pdfRenderer?.close()
        } catch (ignored: Exception) {}
        try {
            fileDescriptor?.close()
        } catch (ignored: Exception) {}
        pdfRenderer = null
        fileDescriptor = null
    }
}
