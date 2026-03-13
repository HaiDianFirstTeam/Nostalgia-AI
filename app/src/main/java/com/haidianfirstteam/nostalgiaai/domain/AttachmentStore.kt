package com.haidianfirstteam.nostalgiaai.domain

import android.content.Context
import android.net.Uri
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.AttachmentEntity
import com.haidianfirstteam.nostalgiaai.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttachmentStore(
    private val context: Context,
    private val db: AppDatabase
) {
    suspend fun saveForMessage(messageId: Long, picked: List<FileUtil.PickedFile>) = withContext(Dispatchers.IO) {
        if (picked.isEmpty()) return@withContext
        val entities = picked.map {
            AttachmentEntity(
                messageId = messageId,
                uri = it.uri.toString(),
                displayName = it.displayName,
                mimeType = it.mimeType,
                sizeBytes = it.sizeBytes
            )
        }
        db.attachments().insertAll(entities)
    }

    suspend fun loadForMessage(messageId: Long): List<AttachmentEntity> = withContext(Dispatchers.IO) {
        db.attachments().listForMessage(messageId)
    }

    fun parseUri(s: String): Uri = Uri.parse(s)
}
