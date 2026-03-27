package com.haidianfirstteam.nostalgiaai.ui.rss

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.haidianfirstteam.nostalgiaai.net.HttpClients
import java.util.concurrent.Executors

/**
 * Lightweight image loader (OkHttp + LruCache).
 * Designed for API19 compatibility without adding large dependencies.
 */
object ImageLoader {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(3)

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(calcCacheSize()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun loadInto(urlRaw: String?, imageView: ImageView) {
        val url = urlRaw?.trim().orEmpty()
        if (url.isBlank()) {
            imageView.setImageDrawable(null)
            return
        }
        val key = url
        imageView.tag = key
        val cached = cache.get(key)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        executor.execute {
            val bmp = fetchBitmapBestEffort(url)
            if (bmp != null) {
                cache.put(key, bmp)
            }
            mainHandler.post {
                if (imageView.tag == key) {
                    imageView.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun fetchBitmapBestEffort(urlRaw: String): Bitmap? {
        val urls = ArrayList<String>(2)
        urls.add(urlRaw)
        // Best-effort upgrade for mixed-content images.
        if (urlRaw.startsWith("http://")) {
            urls.add("https://" + urlRaw.removePrefix("http://"))
        }
        val client = HttpClients.music()

        for (u in urls.distinct()) {
            try {
                val req = okhttp3.Request.Builder().url(u).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val bytes = resp.body()?.bytes() ?: return@use
                    val opts = BitmapFactory.Options()
                    opts.inPreferredConfig = Bitmap.Config.RGB_565
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            } catch (_: Throwable) {
                // try next
            }
        }
        return null
    }

    private fun calcCacheSize(): Int {
        // Use ~8MB in KB (LruCache size in KB)
        return 8 * 1024
    }
}
