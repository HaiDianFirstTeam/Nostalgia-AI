package com.haidianfirstteam.nostalgiaai.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

object FileUtil {
    data class PickedFile(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    fun getPickedFile(context: Context, uri: Uri): PickedFile {
        val cr: ContentResolver = context.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"

        var name: String? = null
        var size: Long = -1
        var cursor: Cursor? = null
        try {
            cursor = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) name = cursor.getString(nameIdx)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        } finally {
            cursor?.close()
        }
        if (name == null) {
            name = DocumentFile.fromSingleUri(context, uri)?.name ?: "file"
        }
        if (size < 0) {
            size = try {
                cr.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
            } catch (e: Exception) {
                -1
            }
        }
        return PickedFile(uri, name ?: "file", mime, size)
    }
}
