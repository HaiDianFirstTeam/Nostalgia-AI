package com.haidianfirstteam.nostalgiaai.domain

import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.GroupProviderEntity
import com.haidianfirstteam.nostalgiaai.data.entities.GroupProviderStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider 调用失败时，下调其优先级：penalty++，effectiveOrder = orderIndex + penalty
 */
class ProviderPriorityManager(private val db: AppDatabase) {

    suspend fun orderedProvidersForGroup(groupId: Long): List<GroupProviderEntity> = withContext(Dispatchers.IO) {
        val providers = db.groupProviders().listByGroup(groupId).filter { it.enabled }
        if (providers.isEmpty()) return@withContext emptyList()

        val states = db.groupProviderStates().listByGroup(groupId)
        val penaltyByProvider = states.associateBy({ it.providerId }, { it.penalty })

        providers.sortedWith(compareBy<GroupProviderEntity> {
            (it.orderIndex + (penaltyByProvider[it.providerId] ?: 0))
        }.thenBy { it.orderIndex })
    }

    suspend fun reportFailure(groupId: Long, providerId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = db.groupProviderStates().get(groupId, providerId)
        val next = if (existing == null) {
            GroupProviderStateEntity(groupId, providerId, penalty = 1, lastFailedAt = now, failStreak = 1)
        } else {
            existing.copy(
                penalty = existing.penalty + 1,
                lastFailedAt = now,
                failStreak = existing.failStreak + 1
            )
        }
        db.groupProviderStates().upsert(next)
    }

    suspend fun reportSuccess(groupId: Long, providerId: Long) = withContext(Dispatchers.IO) {
        // Strategy C: success resets provider priority immediately.
        val existing = db.groupProviderStates().get(groupId, providerId)
        val next = if (existing == null) {
            GroupProviderStateEntity(
                groupId = groupId,
                providerId = providerId,
                penalty = 0,
                lastFailedAt = 0,
                failStreak = 0
            )
        } else {
            existing.copy(
                penalty = 0,
                failStreak = 0,
                lastFailedAt = 0
            )
        }
        db.groupProviderStates().upsert(next)
    }
}
