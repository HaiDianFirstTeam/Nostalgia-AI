package com.haidianfirstteam.nostalgiaai.domain

import android.content.Context
import android.net.Uri

object BinaryUtil {
    fun readAllBytesWithLimit(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            val buf = ByteArray(8192)
            var total = 0
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buf, 0, read)
            }
            return out.toByteArray()
        }
    }
}
