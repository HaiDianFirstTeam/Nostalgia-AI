package com.haidianfirstteam.nostalgiaai.domain

import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.ApiKeyEntity
import com.haidianfirstteam.nostalgiaai.data.entities.GroupRouteEntity
import com.haidianfirstteam.nostalgiaai.data.entities.ModelEntity
import com.haidianfirstteam.nostalgiaai.data.entities.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random

data class RoutedModel(
    val provider: ProviderEntity,
    val apiKey: ApiKeyEntity,
    val model: ModelEntity
)

/**
 * 根据组内路由条目随机选择一个 provider+apikey+model.
 * 失败时：同一个条目重试1次，然后换下一个条目。
 */
class ModelGroupRouter(private val db: AppDatabase) {

    private val rnd = Random()

    suspend fun pick(groupId: Long): List<RoutedModel> = withContext(Dispatchers.IO) {
        val routes: List<GroupRouteEntity> = db.groupRoutes().listByGroup(groupId)
            .filter { it.enabled }
        if (routes.isEmpty()) return@withContext emptyList()

        // Random shuffle order
        val shuffled = routes.toMutableList()
        shuffled.shuffle(rnd)

        val result = ArrayList<RoutedModel>(shuffled.size)
        for (r in shuffled) {
            val provider = db.providers().getById(r.providerId) ?: continue
            val key = db.apiKeys().getById(r.apiKeyId) ?: continue
            val model = db.models().getById(r.modelId) ?: continue
            if (!key.enabled) continue
            result.add(RoutedModel(provider, key, model))
        }
        result
    }
}
