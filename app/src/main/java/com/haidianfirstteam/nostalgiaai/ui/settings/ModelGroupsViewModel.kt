package com.haidianfirstteam.nostalgiaai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.data.entities.ModelGroupEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelGroupsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NostalgiaApp).db
    val groups: LiveData<List<ModelGroupEntity>> = db.modelGroups().observeAll()

    fun addGroup(name: String) {
        val t = name.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.modelGroups().insert(ModelGroupEntity(name = t))
            }
        }
    }

    fun rename(id: Long, name: String) {
        val t = name.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val g = db.modelGroups().getById(id) ?: return@withContext
                db.modelGroups().update(g.copy(name = t))
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.modelGroups().deleteById(id)
            }
        }
    }
}
