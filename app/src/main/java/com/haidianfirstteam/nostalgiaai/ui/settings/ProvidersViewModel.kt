package com.haidianfirstteam.nostalgiaai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.data.entities.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProvidersViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NostalgiaApp).db
    val providers: LiveData<List<ProviderEntity>> = db.providers().observeAll()

    fun addProvider(name: String, baseUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.providers().insert(ProviderEntity(name = name.trim(), baseUrl = baseUrl.trim()))
            }
        }
    }

    fun rename(id: Long, newName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val p = db.providers().getById(id) ?: return@withContext
                db.providers().update(p.copy(name = newName.trim()))
            }
        }
    }

    fun updateBaseUrl(id: Long, baseUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val p = db.providers().getById(id) ?: return@withContext
                db.providers().update(p.copy(baseUrl = baseUrl.trim()))
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.providers().deleteById(id)
            }
        }
    }
}
