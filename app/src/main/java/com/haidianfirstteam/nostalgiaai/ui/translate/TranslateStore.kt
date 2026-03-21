package com.haidianfirstteam.nostalgiaai.ui.translate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.AppSettingEntity

enum class TranslateMode {
    WORD,
    SENTENCE,
    ARTICLE,
}

data class TranslateSettings(
    val langA: String = "auto",
    val langB: String = "Chinese",
    val mode: TranslateMode = TranslateMode.SENTENCE,
    val memoryEnabled: Boolean = false,
    val identity: String = "",

    // Model route selection for translator (reuses app's provider/group/model config).
    // routeType: "auto" | "group" | "direct"
    val routeType: String = "auto",
    val routeGroupId: Long? = null,
    val routeProviderId: Long? = null,
    val routeModelId: Long? = null,
)

data class TranslateHistoryItem(
    val id: Long,
    val createdAt: Long,
    val input: String,
    val output: String,
    val langA: String,
    val langB: String,
    val mode: TranslateMode,
)

data class TranslateConversation(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val turns: List<TranslateHistoryItem>,
)

class TranslateStore(
    private val db: AppDatabase,
    private val gson: Gson = Gson(),
) {
    private companion object {
        private const val KEY_SETTINGS = "translate_settings"
        private const val KEY_HISTORY = "translate_history"
        private const val KEY_CONVERSATIONS = "translate_conversations_v1"
        private const val KEY_ACTIVE_CONVERSATION_ID = "translate_active_conversation_id"
        private const val MAX_HISTORY = 200
        private const val MAX_CONVERSATIONS = 30
        private const val MAX_TURNS_PER_CONV = 60
    }

    suspend fun getSettings(): TranslateSettings {
        val raw = db.appSettings().get(KEY_SETTINGS)?.value
        if (raw.isNullOrBlank()) return TranslateSettings()
        return try {
            gson.fromJson(raw, TranslateSettings::class.java) ?: TranslateSettings()
        } catch (_: Throwable) {
            TranslateSettings()
        }
    }

    suspend fun setSettings(s: TranslateSettings) {
        db.appSettings().put(AppSettingEntity(KEY_SETTINGS, gson.toJson(s)))
    }

    suspend fun listHistory(): List<TranslateHistoryItem> {
        val raw = db.appSettings().get(KEY_HISTORY)?.value ?: return emptyList()
        return try {
            val t = object : TypeToken<List<TranslateHistoryItem>>() {}.type
            val list: List<TranslateHistoryItem> = gson.fromJson(raw, t) ?: emptyList()
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun addHistory(item: TranslateHistoryItem) {
        val old = listHistory().toMutableList()
        old.removeAll { it.id == item.id }
        old.add(0, item)
        val next = old.take(MAX_HISTORY)
        db.appSettings().put(AppSettingEntity(KEY_HISTORY, gson.toJson(next)))
    }

    suspend fun deleteHistory(id: Long) {
        val next = listHistory().filterNot { it.id == id }
        db.appSettings().put(AppSettingEntity(KEY_HISTORY, gson.toJson(next)))
    }

    suspend fun clearHistory() {
        db.appSettings().put(AppSettingEntity(KEY_HISTORY, gson.toJson(emptyList<TranslateHistoryItem>())))
    }

    suspend fun getActiveConversationId(): Long? {
        val raw = db.appSettings().get(KEY_ACTIVE_CONVERSATION_ID)?.value?.trim()
        return raw?.toLongOrNull()
    }

    suspend fun setActiveConversationId(id: Long?) {
        db.appSettings().put(AppSettingEntity(KEY_ACTIVE_CONVERSATION_ID, id?.toString().orEmpty()))
    }

    suspend fun listConversations(): List<TranslateConversation> {
        val raw = db.appSettings().get(KEY_CONVERSATIONS)?.value ?: return emptyList()
        return try {
            val t = object : TypeToken<List<TranslateConversation>>() {}.type
            val list: List<TranslateConversation> = gson.fromJson(raw, t) ?: emptyList()
            list.sortedByDescending { it.updatedAt }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun putConversations(list: List<TranslateConversation>) {
        db.appSettings().put(AppSettingEntity(KEY_CONVERSATIONS, gson.toJson(list)))
    }

    suspend fun ensureActiveConversationId(): Long {
        val cur = getActiveConversationId()
        if (cur != null) {
            val exists = listConversations().any { it.id == cur }
            if (exists) return cur
        }
        return newConversation()
    }

    suspend fun newConversation(): Long {
        val now = System.currentTimeMillis()
        val id = now
        val conv = TranslateConversation(id = id, createdAt = now, updatedAt = now, turns = emptyList())
        val old = listConversations().toMutableList()
        old.removeAll { it.id == id }
        old.add(0, conv)
        val next = old.sortedByDescending { it.updatedAt }.take(MAX_CONVERSATIONS)
        putConversations(next)
        setActiveConversationId(id)
        return id
    }

    suspend fun getConversation(id: Long): TranslateConversation? {
        return listConversations().firstOrNull { it.id == id }
    }

    suspend fun getActiveConversation(): TranslateConversation? {
        val id = getActiveConversationId() ?: return null
        return getConversation(id)
    }

    suspend fun listActiveTurns(): List<TranslateHistoryItem> {
        val id = ensureActiveConversationId()
        return getConversation(id)?.turns ?: emptyList()
    }

    suspend fun appendTurnToActiveConversation(item: TranslateHistoryItem) {
        val id = ensureActiveConversationId()
        appendTurnToConversation(id, item)
    }

    suspend fun appendTurnToConversation(conversationId: Long, item: TranslateHistoryItem) {
        val now = System.currentTimeMillis()
        val all = listConversations().toMutableList()
        val idx = all.indexOfFirst { it.id == conversationId }
        val existing = if (idx >= 0) all[idx] else TranslateConversation(conversationId, now, now, emptyList())
        val turns = existing.turns.toMutableList()
        turns.add(item)
        val nextTurns = turns.takeLast(MAX_TURNS_PER_CONV)
        val updated = existing.copy(updatedAt = now, turns = nextTurns)
        if (idx >= 0) all[idx] = updated else all.add(0, updated)
        val next = all.sortedByDescending { it.updatedAt }.take(MAX_CONVERSATIONS)
        putConversations(next)
        setActiveConversationId(conversationId)
    }

    suspend fun deleteConversation(conversationId: Long) {
        val next = listConversations().filterNot { it.id == conversationId }
        putConversations(next)
        if (getActiveConversationId() == conversationId) {
            setActiveConversationId(next.firstOrNull()?.id)
        }
    }

    suspend fun clearConversations() {
        putConversations(emptyList())
        setActiveConversationId(null)
    }
}
