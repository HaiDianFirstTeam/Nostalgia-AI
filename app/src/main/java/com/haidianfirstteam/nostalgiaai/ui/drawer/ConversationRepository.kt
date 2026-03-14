package com.haidianfirstteam.nostalgiaai.ui.drawer

import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationRepository(private val db: AppDatabase) {
    fun observeAll() = db.conversations().observeAll()

    suspend fun createConversation(title: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.conversations().insert(
            ConversationEntity(
                title = title,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun createConversationWithTimeTitle(): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val title = fmt.format(Date(now))
        db.conversations().insert(
            ConversationEntity(
                title = title,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteIfEmpty(conversationId: Long) = withContext(Dispatchers.IO) {
        val msgs = db.messages().listByConversation(conversationId)
        if (msgs.isEmpty()) {
            db.conversations().deleteById(conversationId)
        }
    }

    suspend fun renameConversation(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val existing = db.conversations().getById(id) ?: return@withContext
        db.conversations().update(existing.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        db.conversations().deleteById(id)
    }
}
