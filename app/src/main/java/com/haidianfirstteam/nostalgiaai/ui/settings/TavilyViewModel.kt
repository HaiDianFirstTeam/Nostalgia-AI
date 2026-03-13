package com.haidianfirstteam.nostalgiaai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.data.entities.AppSettingEntity
import com.haidianfirstteam.nostalgiaai.data.entities.TavilyKeyEntity
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TavilyState(
    val baseUrl: String,
    val keys: List<TwoLineItem>
)

class TavilyViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NostalgiaApp).db

    private val _state = MutableLiveData<TavilyState>()
    val state: LiveData<TavilyState> = _state

    fun refresh() {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) {
                val baseUrl = db.appSettings().get("tavily_base_url")?.value ?: "https://api.tavily.com"
                val keys = db.tavilyKeys().listAll().map {
                    TwoLineItem(it.id, it.nickname, mask(it.apiKey))
                }
                TavilyState(baseUrl, keys)
            }
            _state.value = s
        }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.appSettings().put(AppSettingEntity("tavily_base_url", url.trim()))
            }
            refresh()
        }
    }

    fun addKey(nickname: String, apiKey: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.tavilyKeys().insert(TavilyKeyEntity(nickname = nickname.trim(), apiKey = apiKey.trim(), enabled = true))
            }
            refresh()
        }
    }

    fun deleteKey(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.tavilyKeys().deleteById(id) }
            refresh()
        }
    }

    private fun mask(k: String): String {
        val t = k.trim()
        if (t.length <= 8) return "****"
        return t.take(3) + "****" + t.takeLast(3)
    }
}
