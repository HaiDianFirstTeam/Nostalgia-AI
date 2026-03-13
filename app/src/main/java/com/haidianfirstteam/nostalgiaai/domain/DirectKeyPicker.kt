package com.haidianfirstteam.nostalgiaai.domain

import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.ApiKeyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random

/**
 * 直连模型：自动随机选择该 Provider 下启用的 key。
 * 失败时重试 1 次，再换下一个。
 */
class DirectKeyPicker(private val db: AppDatabase) {

    private val rnd = Random()

    suspend fun pickKeys(providerId: Long): List<ApiKeyEntity> = withContext(Dispatchers.IO) {
        val keys = db.apiKeys().listAll().filter { it.providerId == providerId && it.enabled }
        if (keys.isEmpty()) return@withContext emptyList()
        val list = keys.toMutableList()
        list.shuffle(rnd)
        list
    }
}
