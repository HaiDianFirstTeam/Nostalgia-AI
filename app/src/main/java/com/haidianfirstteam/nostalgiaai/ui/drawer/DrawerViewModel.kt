package com.haidianfirstteam.nostalgiaai.ui.drawer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.data.entities.ConversationEntity
import kotlinx.coroutines.launch

class DrawerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConversationRepository((app as NostalgiaApp).db)
    val conversations: LiveData<List<ConversationEntity>> = repo.observeAll()

    private val _openConversationId = MutableLiveData<Long>()
    val openConversationId: LiveData<Long> = _openConversationId

    fun newConversation() {
        viewModelScope.launch {
            val id = repo.createConversation("新对话")
            _openConversationId.value = id
        }
    }

    fun openConversation(id: Long) {
        _openConversationId.value = id
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch {
            repo.renameConversation(id, title)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repo.deleteConversation(id)
        }
    }
}
