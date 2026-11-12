package com.bori.keyboard.gif

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Downloads GIF previews (kept in memory only, never on disk) and the GIF to send (to the
 * cache folder Bori's FileProvider shares).
 */
class MediaLoader(private val res: Resources, private val cacheDir: File) {

    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val bytes = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    /** Animated where Android can (9+), else still. [done] runs on the main thread. */
    fun preview(url: String, done: (Drawable?) -> Unit) {
        io.execute {
            val d = runCatching {
                val b = bytes.get(url) ?: download(url, MAX_PREVIEW).also { bytes.put(url, it) }
                decode(b)
            }.getOrNull()
            main.post { done(d) }
        }
    }

    /** Saves the GIF to send into cache/gifs/ and gives the file (null on failure). */
    fun fetchToSend(item: GifItem, done: (File?) -> Unit) {
        io.execute {
            val f = runCatching {
                val dir = File(cacheDir, "gifs").apply { mkdirs() }
                // Keep the folder small: only the last few sent GIFs.
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach { it.delete() }
                val safe = item.slug.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifEmpty { "gif" }
                val out = File(dir, "$safe.gif")
                if (!out.exists() || out.length() == 0L) out.writeBytes(download(item.send, MAX_SEND))
                out.setLastModified(System.currentTimeMillis())
                out
            }.getOrNull()
            main.post { done(f) }
        }
    }

    private fun decode(b: ByteArray): Drawable? =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(b)))
        } else {
            BitmapFactory.decodeByteArray(b, 0, b.size)?.let { BitmapDrawable(res, it) }
        }

    private fun download(url: String, max: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > max) error("too big")
                }
            }
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val MAX_PREVIEW = 2 * 1024 * 1024
        private const val MAX_SEND = 10 * 1024 * 1024
    }
}
