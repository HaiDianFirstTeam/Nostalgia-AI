package com.haidianfirstteam.nostalgiaai.ui.music.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.collection.LruCache
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.haidianfirstteam.nostalgiaai.net.HttpClients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

object CoverLoader {
    // ~8MB cache
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun loadInto(
        scope: CoroutineScope,
        url: String?,
        imageView: ImageView,
        placeholderRes: Int? = null,
        circular: Boolean = true
    ) {
        if (url.isNullOrBlank()) {
            if (placeholderRes != null) imageView.setImageResource(placeholderRes) else imageView.setImageDrawable(null)
            return
        }
        val key = url
        val cached = cache.get(key)
        if (cached != null) {
            setBitmap(imageView, cached, circular)
            return
        }
        if (placeholderRes != null) imageView.setImageResource(placeholderRes)
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(url).get().build()
                    HttpClients.music().newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        val stream = resp.body()?.byteStream() ?: return@withContext null
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            if (bmp != null) {
                cache.put(key, bmp)
                try {
                    // Avoid setting into recycled view.
                    setBitmap(imageView, bmp, circular)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun setBitmap(imageView: ImageView, bmp: Bitmap, circular: Boolean) {
        if (!circular) {
            imageView.setImageBitmap(bmp)
            return
        }
        try {
            val d = RoundedBitmapDrawableFactory.create(imageView.resources, bmp)
            d.isCircular = true
            imageView.setImageDrawable(d)
        } catch (_: Throwable) {
            imageView.setImageBitmap(bmp)
        }
    }
}
