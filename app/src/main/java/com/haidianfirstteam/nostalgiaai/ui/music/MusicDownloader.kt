package com.haidianfirstteam.nostalgiaai.ui.music

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack

object MusicDownloader {

    data class Enqueued(
        val downloadId: Long,
        val fileName: String,
    )

    fun enqueue(context: Context, track: MusicTrack, url: String): Enqueued {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val title = sanitize(track.name)
        val artist = sanitize(track.artists.joinToString("-"))
        val fileName = ("${title}-${artist}").take(60).ifBlank { "music" } + ".mp3"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.name)
            .setDescription(artist)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)

        // Android 4.4 can throw SecurityException if WRITE_EXTERNAL_STORAGE is not granted.
        // Prefer public Downloads when permitted; otherwise fall back to app-scoped external dir.
        val canWritePublic = try {
            context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        if (canWritePublic) {
            try {
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } catch (_: Throwable) {
                // fallback below
            }
        }
        if (!canWritePublic) {
            try {
                req.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            } catch (_: Throwable) {
                // ignore
            }
        }
        val id = dm.enqueue(req)
        return Enqueued(downloadId = id, fileName = fileName)
    }

    data class Snapshot(
        val downloadId: Long,
        val status: Int,
        val reason: Int,
        val bytesSoFar: Long,
        val totalBytes: Long,
        val localUri: String?,
    )

    fun query(context: Context, ids: List<Long>): Map<Long, Snapshot> {
        if (ids.isEmpty()) return emptyMap()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(*ids.toLongArray())
        val out = HashMap<Long, Snapshot>()
        val c = dm.query(q)
        try {
            val colId = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val colStatus = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val colReason = c.getColumnIndex(DownloadManager.COLUMN_REASON)
            val colSoFar = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val colTotal = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val colLocal = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            while (c.moveToNext()) {
                val id = if (colId >= 0) c.getLong(colId) else -1L
                if (id <= 0) continue
                val status = if (colStatus >= 0) c.getInt(colStatus) else 0
                val reason = if (colReason >= 0) c.getInt(colReason) else 0
                val soFar = if (colSoFar >= 0) c.getLong(colSoFar) else 0L
                val total = if (colTotal >= 0) c.getLong(colTotal) else -1L
                val localUri = if (colLocal >= 0) c.getString(colLocal) else null
                out[id] = Snapshot(id, status, reason, soFar, total, localUri)
            }
        } finally {
            try { c.close() } catch (_: Throwable) {}
        }
        return out
    }

    fun remove(context: Context, ids: LongArray): Int {
        if (ids.isEmpty()) return 0
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return try {
            dm.remove(*ids)
        } catch (_: Throwable) {
            0
        }
    }

    private fun sanitize(s: String): String {
        return s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
