package com.haidianfirstteam.nostalgiaai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.data.entities.ApiKeyEntity
import com.haidianfirstteam.nostalgiaai.data.entities.ModelEntity
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProviderDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NostalgiaApp).db
    private var providerId: Long = -1L

    private val _title = MutableLiveData<String>()
    val title: LiveData<String> = _title

    private val _items = MutableLiveData<List<TwoLineItem>>(emptyList())
    val items: LiveData<List<TwoLineItem>> = _items

    private val _uiSections = MutableLiveData<List<ProviderDetailRow>>(emptyList())
    val uiSections: LiveData<List<ProviderDetailRow>> = _uiSections

    fun load(providerId: Long) {
        this.providerId = providerId
        viewModelScope.launch {
            refresh()
        }
    }

    fun addApiKey(nickname: String, apiKey: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.apiKeys().insert(ApiKeyEntity(providerId = providerId, nickname = nickname.trim(), apiKey = apiKey.trim()))
            }
            refresh()
        }
    }

    fun addModel(modelName: String, nickname: String, multimodal: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.models().insert(ModelEntity(providerId = providerId, modelName = modelName.trim(), nickname = nickname.trim(), multimodal = multimodal))
            }
            refresh()
        }
    }

    fun editItem(item: TwoLineItem) {
        // Editing is initiated from Activity where we have a UI Context.
        // This ViewModel is intentionally UI-agnostic; call the explicit methods below.
    }

    fun editApiKey(keyId: Long, nickname: String, apiKey: String) {
        val nick = nickname.trim()
        val k = apiKey.trim()
        if (nick.isEmpty() || k.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = db.apiKeys().getById(keyId) ?: return@withContext
                db.apiKeys().update(existing.copy(nickname = nick, apiKey = k))
            }
            refresh()
        }
    }

    fun editModel(modelId: Long, modelName: String, nickname: String, multimodal: Boolean) {
        val mn = modelName.trim()
        val nick = nickname.trim()
        if (mn.isEmpty() || nick.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = db.models().getById(modelId) ?: return@withContext
                db.models().update(existing.copy(modelName = mn, nickname = nick, multimodal = multimodal))
            }
            refresh()
        }
    }

    fun deleteItem(item: TwoLineItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (item.subtitle.startsWith("KEY:")) {
                    db.apiKeys().deleteById(item.id)
                } else if (item.subtitle.startsWith("MODEL:")) {
                    db.models().deleteById(item.id)
                }
            }
            refresh()
        }
    }

    fun toggleEnabled(item: TwoLineItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (item.subtitle.startsWith("KEY:")) {
                    val k = db.apiKeys().getById(item.id) ?: return@withContext
                    db.apiKeys().update(k.copy(enabled = !k.enabled))
                } else if (item.subtitle.startsWith("MODEL:")) {
                    val m = db.models().getById(item.id) ?: return@withContext
                    // No enabled flag on model currently; toggle multimodal doesn't make sense.
                    // We treat this as no-op for models.
                }
            }
            refresh()
        }
    }

    private suspend fun refresh() {
        val provider = withContext(Dispatchers.IO) { db.providers().getById(providerId) }
        _title.value = provider?.name ?: "Provider"

        // List using blocking queries.
        val keyList = withContext(Dispatchers.IO) { db.apiKeys().listAll().filter { it.providerId == providerId } }
        val modelList = withContext(Dispatchers.IO) { db.models().listAll().filter { it.providerId == providerId } }

        val ui = ArrayList<TwoLineItem>()
        keyList.forEach {
            val status = if (it.enabled) "启用" else "禁用"
            ui.add(TwoLineItem(it.id, "Key: ${it.nickname}（$status）", "KEY: ${maskKey(it.apiKey)}"))
        }
        modelList.forEach {
            val suffix = if (it.multimodal) "多模态" else "纯文本"
            ui.add(TwoLineItem(it.id, "模型: ${it.nickname}（$suffix）", "MODEL: ${it.modelName}  多模态=${it.multimodal}"))
        }
        _items.value = ui

        // New: sectioned rows for UI
        val rows = ArrayList<ProviderDetailRow>()
        rows.add(ProviderDetailRow.Header("API Key 设置"))
        keyList.forEach {
            val status = if (it.enabled) "启用" else "禁用"
            rows.add(
                ProviderDetailRow.Item(
                    item = TwoLineItem(it.id, "Key: ${it.nickname}（$status）", "KEY: ${maskKey(it.apiKey)}")
                )
            )
        }
        rows.add(ProviderDetailRow.Header("模型设置"))
        modelList.forEach {
            val suffix = if (it.multimodal) "多模态" else "纯文本"
            rows.add(
                ProviderDetailRow.Item(
                    item = TwoLineItem(it.id, "模型: ${it.nickname}（$suffix）", "MODEL: ${it.modelName}  多模态=${it.multimodal}")
                )
            )
        }
        _uiSections.value = rows
    }

    suspend fun getApiKeyById(keyId: Long): ApiKeyEntity? = withContext(Dispatchers.IO) {
        db.apiKeys().getById(keyId)
    }

    suspend fun getModelById(modelId: Long): ModelEntity? = withContext(Dispatchers.IO) {
        db.models().getById(modelId)
    }

    private fun maskKey(k: String): String {
        val t = k.trim()
        if (t.length <= 8) return "****"
        return t.take(3) + "****" + t.takeLast(3)
    }
}

sealed class ProviderDetailRow {
    data class Header(val title: String) : ProviderDetailRow()
    data class Item(val item: TwoLineItem) : ProviderDetailRow()
}
