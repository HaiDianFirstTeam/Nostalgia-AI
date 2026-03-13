package com.haidianfirstteam.nostalgiaai.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.MessageEntity
import com.haidianfirstteam.nostalgiaai.domain.AttachmentStore
import com.haidianfirstteam.nostalgiaai.domain.ChatPipeline
import com.haidianfirstteam.nostalgiaai.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RequestState(
    val inFlight: Boolean,
    val statusText: String = ""
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db: AppDatabase = (app as NostalgiaApp).db
    private val pipeline = ChatPipeline(app.applicationContext, db)
    private val attachmentStore = AttachmentStore(app.applicationContext, db)

    private val _conversationId = MutableLiveData<Long>()
    val conversationId: LiveData<Long> = _conversationId

    private val _messages = MutableLiveData<List<MessageUi>>(emptyList())
    val messages: LiveData<List<MessageUi>> = _messages

    private val _requestState = MutableLiveData<RequestState>(RequestState(false, ""))
    val requestState: LiveData<RequestState> = _requestState

    fun loadConversation(conversationId: Long) {
        _conversationId.value = conversationId
        // Observe via Room LiveData would be better; keep simple for now.
        viewModelScope.launch {
            refreshMessages(conversationId)
        }
    }

    fun sendUserMessage(
        text: String,
        targetType: String? = null,
        targetGroupId: Long? = null,
        targetProviderId: Long? = null,
        targetModelId: Long? = null,
        webSearchEnabled: Boolean = false,
        webSearchCount: Int = 5,
        pickedAttachments: List<FileUtil.PickedFile> = emptyList()
    ) {
        val convId = _conversationId.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // prevent spamming multiple requests
        if (_requestState.value?.inFlight == true) return

        viewModelScope.launch {
            _requestState.value = RequestState(true, getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.status_requesting))
            val now = System.currentTimeMillis()
            var userMsgId: Long = -1
            withContext(Dispatchers.IO) {
                userMsgId = db.messages().insert(
                    MessageEntity(
                        conversationId = convId,
                        role = "user",
                        content = trimmed,
                        createdAt = now
                        ,targetType = targetType
                        ,targetGroupId = targetGroupId
                        ,targetProviderId = targetProviderId
                        ,targetModelId = targetModelId
                        ,webSearchEnabled = webSearchEnabled
                        ,webSearchCount = webSearchCount
                    )
                )
                db.conversations().touch(convId, now)
            }
            // Save attachments
            withContext(Dispatchers.IO) {
                attachmentStore.saveForMessage(userMsgId, pickedAttachments)
            }
            refreshMessages(convId)

            // Real pipeline call (non-stream)
            viewModelScope.launch {
                val userMsg = withContext(Dispatchers.IO) { db.messages().getById(userMsgId) }
                if (userMsg != null) {
                    _requestState.value = RequestState(true, getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.status_calling_model))
                    val out = try {
                        val result = pipeline.runOnce(userMsg)
                        result.getOrElse { e ->
                            com.haidianfirstteam.nostalgiaai.domain.ChatPipeline.Output(
                                text = "请求失败：${e.message ?: "未知错误"}",
                                routedProviderId = null,
                                routedApiKeyId = null,
                                routedModelId = null
                            )
                        }
                    } catch (e: Exception) {
                        com.haidianfirstteam.nostalgiaai.domain.ChatPipeline.Output(
                            text = "请求失败：${e.message ?: "未知错误"}",
                            routedProviderId = null,
                            routedApiKeyId = null,
                            routedModelId = null
                        )
                    }
                    val replyText = out.text
                    withContext(Dispatchers.IO) {
                        db.messages().insert(
                            MessageEntity(
                                conversationId = convId,
                                role = "assistant",
                                content = replyText,
                                createdAt = System.currentTimeMillis(),
                                targetType = userMsg.targetType,
                                targetGroupId = userMsg.targetGroupId,
                                targetProviderId = userMsg.targetProviderId,
                                targetModelId = userMsg.targetModelId,
                                routedProviderId = out.routedProviderId,
                                routedApiKeyId = out.routedApiKeyId,
                                routedModelId = out.routedModelId,
                                webSearchEnabled = userMsg.webSearchEnabled,
                                webSearchCount = userMsg.webSearchCount
                            )
                        )
                        db.conversations().touch(convId, System.currentTimeMillis())
                    }
                    refreshMessages(convId)
                }
                _requestState.postValue(RequestState(false, ""))
            }
        }
    }

    // Placeholder for real pipeline:
    // - If pure text model: append extractedText from attachments into prompt
    // - If multimodal: send image/audio/video via standard channel (content parts)

    fun editMessage(messageId: Long, newContent: String) {
        val convId = _conversationId.value ?: return
        val trimmed = newContent.trim()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val msg = db.messages().getById(messageId) ?: return@withContext
                db.messages().update(msg.copy(content = trimmed))
            }
            refreshMessages(convId)
        }
    }

    /**
     * "编辑并重发"：把该用户消息改成新内容，然后截断该消息之后的所有消息，再重新生成（后续接真实请求）。
     */
    fun editAndResendUser(messageId: Long, newContent: String) {
        val convId = _conversationId.value ?: return
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return
        regenerateFromEdit(messageId, trimmed)
    }

    /**
     * "重试"：对某条消息对应的请求重新发起。这里先做最小实现：
     * - 如果是assistant消息：删除该assistant消息及其后续（通常没有后续），然后重新生成
     * - 如果是user消息：重新生成其后的assistant
     */
    fun retryFromMessage(messageId: Long) {
        regenerateFromRetry(messageId)
    }

    private fun regenerateFromEdit(messageId: Long, newContent: String) {
        val convId = _conversationId.value ?: return
        if (_requestState.value?.inFlight == true) return

        viewModelScope.launch {
            _requestState.value = RequestState(true, getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.status_calling_model))
            val userMsg = withContext(Dispatchers.IO) { db.messages().getById(messageId) }
            if (userMsg == null || userMsg.role != "user") {
                _requestState.postValue(RequestState(false, ""))
                return@launch
            }

            withContext(Dispatchers.IO) {
                db.messages().update(userMsg.copy(content = newContent))
                // Delete all messages after this user message
                db.messages().deleteAfter(convId, userMsg.createdAt)
            }
            refreshMessages(convId)
            runPipelineAndInsertAssistant(convId, messageId)
            _requestState.postValue(RequestState(false, ""))
        }
    }

    private fun regenerateFromRetry(messageId: Long) {
        val convId = _conversationId.value ?: return
        if (_requestState.value?.inFlight == true) return

        viewModelScope.launch {
            _requestState.value = RequestState(true, getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.status_calling_model))
            val msg = withContext(Dispatchers.IO) { db.messages().getById(messageId) }
            if (msg == null) {
                _requestState.postValue(RequestState(false, ""))
                return@launch
            }

            val userMsgIdToRun: Long? = withContext(Dispatchers.IO) {
                when (msg.role) {
                    "assistant" -> {
                        // delete assistant and anything after it
                        db.messages().deleteFromTime(convId, msg.createdAt)
                        // find last user before this assistant
                        val upTo = db.messages().listUpToTime(convId, msg.createdAt - 1)
                        upTo.lastOrNull { it.role == "user" }?.id
                    }
                    "user" -> {
                        // delete anything after this user message
                        db.messages().deleteAfter(convId, msg.createdAt)
                        msg.id
                    }
                    else -> null
                }
            }

            refreshMessages(convId)
            if (userMsgIdToRun != null) {
                runPipelineAndInsertAssistant(convId, userMsgIdToRun)
            }
            _requestState.postValue(RequestState(false, ""))
        }
    }

    private suspend fun runPipelineAndInsertAssistant(conversationId: Long, userMessageId: Long) {
        val userMsg = withContext(Dispatchers.IO) { db.messages().getById(userMessageId) } ?: return
        val out = try {
            val result = pipeline.runOnce(userMsg)
            result.getOrElse { e ->
                com.haidianfirstteam.nostalgiaai.domain.ChatPipeline.Output(
                    text = "请求失败：${e.message ?: getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.err_unknown)}",
                    routedProviderId = null,
                    routedApiKeyId = null,
                    routedModelId = null
                )
            }
        } catch (e: Exception) {
            com.haidianfirstteam.nostalgiaai.domain.ChatPipeline.Output(
                text = "请求失败：${e.message ?: getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.err_unknown)}",
                routedProviderId = null,
                routedApiKeyId = null,
                routedModelId = null
            )
        }

        withContext(Dispatchers.IO) {
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = out.text,
                    createdAt = System.currentTimeMillis(),
                    targetType = userMsg.targetType,
                    targetGroupId = userMsg.targetGroupId,
                    targetProviderId = userMsg.targetProviderId,
                    targetModelId = userMsg.targetModelId,
                    routedProviderId = out.routedProviderId,
                    routedApiKeyId = out.routedApiKeyId,
                    routedModelId = out.routedModelId,
                    webSearchEnabled = userMsg.webSearchEnabled,
                    webSearchCount = userMsg.webSearchCount
                )
            )
            db.conversations().touch(conversationId, System.currentTimeMillis())
        }
        refreshMessages(conversationId)
    }

    private suspend fun refreshMessages(conversationId: Long) {
        val list = withContext(Dispatchers.IO) {
            db.messages().listByConversation(conversationId)
        }
        _messages.value = list.map { it.toUi() }
    }

    private fun MessageEntity.toUi(): MessageUi {
        return MessageUi(
            id = id,
            role = role,
            content = content,
            createdAt = createdAt
        )
    }
}
