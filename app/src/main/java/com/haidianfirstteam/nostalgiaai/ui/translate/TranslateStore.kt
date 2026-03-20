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

class TranslateStore(
    private val db: AppDatabase,
    private val gson: Gson = Gson(),
) {
    private companion object {
        private const val KEY_SETTINGS = "translate_settings"
        private const val KEY_HISTORY = "translate_history"
        private const val MAX_HISTORY = 200
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
}
