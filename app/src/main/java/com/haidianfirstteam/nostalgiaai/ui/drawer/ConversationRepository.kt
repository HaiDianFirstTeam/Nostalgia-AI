package com.haidianfirstteam.nostalgiaai.ui.drawer

import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun renameConversation(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val existing = db.conversations().getById(id) ?: return@withContext
        db.conversations().update(existing.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        db.conversations().deleteById(id)
    }
}
