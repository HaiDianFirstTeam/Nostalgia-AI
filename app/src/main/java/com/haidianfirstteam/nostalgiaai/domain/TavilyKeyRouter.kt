package com.haidianfirstteam.nostalgiaai.domain

import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.TavilyKeyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TavilyKeyRouter(private val db: AppDatabase) {

    /**
     * Round-robin across enabled keys.
     * Index stored in app_settings: tavily_rr_index
     */
    suspend fun pickOne(): TavilyKeyEntity? = withContext(Dispatchers.IO) {
        val keys = db.tavilyKeys().listAll().filter { it.enabled }.sortedBy { it.id }
        if (keys.isEmpty()) return@withContext null

        val idx = db.appSettings().get("tavily_rr_index")?.value?.toIntOrNull() ?: 0
        val picked = keys[idx % keys.size]
        db.appSettings().put(com.haidianfirstteam.nostalgiaai.data.entities.AppSettingEntity("tavily_rr_index", ((idx + 1) % keys.size).toString()))
        picked
    }
}
