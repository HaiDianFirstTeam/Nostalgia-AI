package com.haidianfirstteam.nostalgiaai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.data.entities.GroupProviderEntity
import com.haidianfirstteam.nostalgiaai.data.entities.ModelEntity
import com.haidianfirstteam.nostalgiaai.data.entities.ProviderEntity
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelGroupDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NostalgiaApp).db
    private var groupId: Long = -1

    private val _title = MutableLiveData<String>()
    val title: LiveData<String> = _title

    private val _items = MutableLiveData<List<TwoLineItem>>(emptyList())
    val items: LiveData<List<TwoLineItem>> = _items

    private var providersCache: List<ProviderEntity> = emptyList()
    private var modelsCache: List<ModelEntity> = emptyList()

    fun load(groupId: Long) {
        this.groupId = groupId
        viewModelScope.launch { refresh() }
    }

    fun providerOptions(): List<ProviderEntity> = providersCache

    fun modelOptionsForProvider(providerId: Long): List<ModelEntity> {
        return modelsCache.filter { it.providerId == providerId }
    }

    fun addProvider(providerId: Long, modelId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val current = db.groupProviders().listByGroup(groupId)
                val nextIndex = (current.maxOfOrNull { it.orderIndex } ?: -1) + 1
                db.groupProviders().insert(
                    GroupProviderEntity(
                        groupId = groupId,
                        providerId = providerId,
                        modelId = modelId,
                        orderIndex = nextIndex,
                        enabled = true
                    )
                )
            }
            refresh()
        }
    }

    fun moveUp(entryId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val list = db.groupProviders().listByGroup(groupId).toMutableList()
                val idx = list.indexOfFirst { it.id == entryId }
                if (idx <= 0) return@withContext
                val a = list[idx - 1]
                val b = list[idx]
                db.groupProviders().update(a.copy(orderIndex = b.orderIndex))
                db.groupProviders().update(b.copy(orderIndex = a.orderIndex))
            }
            refresh()
        }
    }

    fun moveDown(entryId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val list = db.groupProviders().listByGroup(groupId).toMutableList()
                val idx = list.indexOfFirst { it.id == entryId }
                if (idx < 0 || idx >= list.size - 1) return@withContext
                val a = list[idx]
                val b = list[idx + 1]
                db.groupProviders().update(a.copy(orderIndex = b.orderIndex))
                db.groupProviders().update(b.copy(orderIndex = a.orderIndex))
            }
            refresh()
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.groupProviders().deleteById(entryId) }
            refresh()
        }
    }

    fun resetPriorityState() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.groupProviderStates().deleteByGroup(groupId) }
            refresh()
        }
    }

    private suspend fun refresh() {
        val group = withContext(Dispatchers.IO) { db.modelGroups().getById(groupId) }
        _title.value = group?.name ?: "组"

        providersCache = withContext(Dispatchers.IO) { db.providers().listAll() }
        modelsCache = withContext(Dispatchers.IO) { db.models().listAll() }

        val providerById = providersCache.associateBy { it.id }
        val modelById = modelsCache.associateBy { it.id }

        val entries = withContext(Dispatchers.IO) { db.groupProviders().listByGroup(groupId) }
        val states = withContext(Dispatchers.IO) { db.groupProviderStates().listByGroup(groupId) }
        val penaltyByProvider = states.associateBy({ it.providerId }, { it.penalty })
        val ui = entries.sortedBy { it.orderIndex }.map { e ->
            val p = providerById[e.providerId]
            val m = modelById[e.modelId]
            val penalty = penaltyByProvider[e.providerId] ?: 0
            val title = "${e.orderIndex}. ${p?.name ?: "Provider"}"
            val subtitle = "model=${m?.nickname ?: "?"} (${m?.modelName ?: ""})" +
                (if (m?.multimodal == true) " 多模态" else "") +
                "  penalty=$penalty"
            TwoLineItem(e.id, title, subtitle)
        }
        _items.value = ui
    }
}
