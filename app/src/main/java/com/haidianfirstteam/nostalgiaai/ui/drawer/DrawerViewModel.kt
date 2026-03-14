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
            // Name new conversation by current time to avoid duplicates like "新对话".
            val id = repo.createConversationWithTimeTitle()
            _openConversationId.value = id
        }
    }

    fun deleteIfEmpty(conversationId: Long) {
        viewModelScope.launch {
            repo.deleteIfEmpty(conversationId)
        }
    }

    fun openConversation(id: Long) {
        if (_openConversationId.value != id) {
            _openConversationId.value = id
        }
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
