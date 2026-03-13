package com.haidianfirstteam.nostalgiaai.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.haidianfirstteam.nostalgiaai.util.FileUtil
import java.io.ByteArrayOutputStream

object AttachmentProcessor {

    data class PreparedAttachment(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,

        val kind: Kind,
        val error: String? = null,
        val extractedText: String? = null,
        val imageBase64: String? = null,
        val imageDataUrl: String? = null,

        val audioBase64: String? = null,
        val audioFormat: String? = null,

        val videoPosterDataUrl: String? = null
    )

    enum class Kind {
        TEXT, IMAGE, AUDIO, VIDEO, OTHER
    }

    fun prepare(context: Context, file: FileUtil.PickedFile): PreparedAttachment {
        val mime = file.mimeType
        return when {
            mime.startsWith("image/") -> prepareImage(context, file)
            mime.startsWith("audio/") -> prepareAudio(context, file)
            mime.startsWith("video/") -> prepareVideoPoster(context, file)
            mime == "application/pdf" || mime.startsWith("text/") || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                val r = TextExtractor.extract(context, file.uri, mime)
                if (r.ok) {
                    PreparedAttachment(file.uri, file.displayName, mime, file.sizeBytes, Kind.TEXT, extractedText = r.text)
                } else {
                    PreparedAttachment(file.uri, file.displayName, mime, file.sizeBytes, Kind.OTHER, error = r.error)
                }
            }
            else -> PreparedAttachment(file.uri, file.displayName, mime, file.sizeBytes, Kind.OTHER, error = "不支持的文件类型：$mime")
        }
    }

    private fun prepareImage(context: Context, file: FileUtil.PickedFile): PreparedAttachment {
        // Decode with inSampleSize to cap memory
        val cr = context.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(file.uri).use { ins ->
            if (ins != null) BitmapFactory.decodeStream(ins, null, opts)
        }
        val (w, h) = opts.outWidth to opts.outHeight
        val maxSide = 1024
        var sample = 1
        var ww = w
        var hh = h
        while (ww > maxSide || hh > maxSide) {
            sample *= 2
            ww /= 2
            hh /= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp: Bitmap = cr.openInputStream(file.uri).use { ins ->
            BitmapFactory.decodeStream(ins, null, decodeOpts)
        } ?: return PreparedAttachment(file.uri, file.displayName, file.mimeType, file.sizeBytes, Kind.OTHER)

        val baos = ByteArrayOutputStream()
        val compressFormat = if (file.mimeType.equals("image/png", ignoreCase = true)) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        bmp.compress(compressFormat, 85, baos)
        val bytes = baos.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUrl = "data:${file.mimeType};base64,$b64"
        return PreparedAttachment(file.uri, file.displayName, file.mimeType, file.sizeBytes, Kind.IMAGE, imageBase64 = b64, imageDataUrl = dataUrl)
    }

    private fun prepareAudio(context: Context, file: FileUtil.PickedFile): PreparedAttachment {
        // Best-effort: read small audio as base64 for OpenAI input_audio.
        val maxBytes = 2_000_000 // ~2MB raw
        val bytes = BinaryUtil.readAllBytesWithLimit(context, file.uri, maxBytes) ?: return PreparedAttachment(
            file.uri, file.displayName, file.mimeType, file.sizeBytes, Kind.AUDIO,
            error = "音频文件过大（> ${maxBytes} bytes）或无法读取，已降级为仅提示文件名"
        )
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val format = mimeToAudioFormat(file.mimeType)
        return PreparedAttachment(
            file.uri,
            file.displayName,
            file.mimeType,
            file.sizeBytes,
            Kind.AUDIO,
            error = if (format == null) "音频格式无法识别，可能不被模型支持" else null,
            audioBase64 = b64,
            audioFormat = format
        )
    }

    private fun mimeToAudioFormat(mime: String): String? {
        val m = mime.lowercase()
        return when {
            m.contains("wav") -> "wav"
            m.contains("mpeg") || m.contains("mp3") -> "mp3"
            m.contains("m4a") || m.contains("mp4") -> "m4a"
            m.contains("ogg") -> "ogg"
            else -> null
        }
    }

    private fun prepareVideoPoster(context: Context, file: FileUtil.PickedFile): PreparedAttachment {
        // Extract a poster frame and send as image_url data URL.
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, file.uri)
            val bmp = retriever.frameAtTime
            retriever.release()
            if (bmp == null) {
                PreparedAttachment(file.uri, file.displayName, file.mimeType, file.sizeBytes, Kind.VIDEO)
            } else {
                val scaled = scaleDown(bmp, 768)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val bytes = baos.toByteArray()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$b64"
                PreparedAttachment(
                    file.uri, file.displayName, file.mimeType, file.sizeBytes, Kind.VIDEO,
                    videoPosterDataUrl = dataUrl
                )
            }
        } catch (e: Exception) {
            PreparedAttachment(file.uri, file.displayName, file.mimeType, file.sizeBytes, Kind.VIDEO, error = e.message)
        }
    }

    private fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return bmp
        val max = maxOf(w, h)
        if (max <= maxSide) return bmp
        val scale = maxSide.toFloat() / max.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, nw, nh, true)
    }
}
