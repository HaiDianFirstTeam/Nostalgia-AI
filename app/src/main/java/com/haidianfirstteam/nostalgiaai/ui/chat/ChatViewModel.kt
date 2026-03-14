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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.Call

data class RequestState(
    val inFlight: Boolean,
    val statusText: String = ""
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db: AppDatabase = (app as NostalgiaApp).db
    private val pipeline = ChatPipeline(app.applicationContext, db)
    private val attachmentStore = AttachmentStore(app.applicationContext, db)
    private val settingsRepo = (app as NostalgiaApp).settingsRepository

    private val _conversationId = MutableLiveData<Long>()
    val conversationId: LiveData<Long> = _conversationId

    private val _messages = MutableLiveData<List<MessageUi>>(emptyList())
    val messages: LiveData<List<MessageUi>> = _messages

    private val _requestState = MutableLiveData<RequestState>(RequestState(false, ""))
    val requestState: LiveData<RequestState> = _requestState

    private var runningJob: Job? = null
    private var runningCall: Call? = null
    private var runningAssistantMessageId: Long? = null

    // Throttle UI/DB updates during streaming to reduce jitter.
    private var lastStreamUpdateAt: Long = 0L
    private val streamUpdateMinIntervalMs: Long = 80L

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
        if (trimmed.isEmpty() && pickedAttachments.isEmpty()) return

        // prevent spamming multiple requests
        if (_requestState.value?.inFlight == true) return

        viewModelScope.launch {
            try {
                _requestState.value = RequestState(true, getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.status_requesting))
            val now = System.currentTimeMillis()
            var userMsgId: Long = -1
            withContext(Dispatchers.IO) {
                // Ensure conversation still exists (it may have been cleaned up unexpectedly)
                val conv = db.conversations().getById(convId)
                if (conv == null) throw IllegalStateException("conversation missing")
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

            // Real pipeline call (stream/compat/off). Keep a reference so UI can cancel.
            val job = viewModelScope.launch {
                try {
                    val userMsg = withContext(Dispatchers.IO) { db.messages().getById(userMsgId) }
                    if (userMsg != null) {
                        _requestState.value = RequestState(true, getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.status_calling_model))

                        val (streamMode, compatMs) = withContext(Dispatchers.IO) {
                            val mode = settingsRepo.getStreamModeBlocking()
                            val ms = settingsRepo.getCompatStreamIntervalMsBlocking()
                            Pair(mode, ms)
                        }

                        if (streamMode == "off") {
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

                            if (!currentCoroutineContext().isActive) return@launch

                            val replyText = out.text
                            val encoded = if (out.webLinks.isNotEmpty()) {
                                WebLinksCodec.encode(out.webLinks.map { WebLinkUi(it.title, it.url) }, replyText)
                            } else replyText
                            withContext(Dispatchers.IO) {
                                db.messages().insert(
                                    MessageEntity(
                                        conversationId = convId,
                                        role = "assistant",
                                        content = encoded,
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
                        } else {
                            var assistantId: Long = -1
                            withContext(Dispatchers.IO) {
                                assistantId = db.messages().insert(
                                    MessageEntity(
                                        conversationId = convId,
                                        role = "assistant",
                                        content = "",
                                        createdAt = System.currentTimeMillis(),
                                        targetType = userMsg.targetType,
                                        targetGroupId = userMsg.targetGroupId,
                                        targetProviderId = userMsg.targetProviderId,
                                        targetModelId = userMsg.targetModelId,
                                        webSearchEnabled = userMsg.webSearchEnabled,
                                        webSearchCount = userMsg.webSearchCount
                                    )
                                )
                                db.conversations().touch(convId, System.currentTimeMillis())
                            }
                            runningAssistantMessageId = assistantId
                            refreshMessages(convId)

                            val sb = StringBuilder()
                            var routedProviderId: Long? = null
                            var routedApiKeyId: Long? = null
                            var routedModelId: Long? = null
                            var webLinks: List<com.haidianfirstteam.nostalgiaai.domain.ChatPipeline.WebLink> = emptyList()

                            val mode = if (streamMode == "compat") ChatPipeline.StreamMode.COMPAT else ChatPipeline.StreamMode.ON
                            try {
                                pipeline.runStream(
                                    userMessage = userMsg,
                                    mode = mode,
                                    compatIntervalMs = compatMs,
                                    onCallReady = { call ->
                                        runningCall = call
                                    },
                                    onStart = { out0 ->
                                        routedProviderId = out0.routedProviderId
                                        routedApiKeyId = out0.routedApiKeyId
                                        routedModelId = out0.routedModelId
                                        webLinks = out0.webLinks
                                    },
                                onDeltaText = onDeltaText@{ delta ->
                                    sb.append(delta)
                                    val textNow = sb.toString()
                                    val encodedNow = if (webLinks.isNotEmpty()) {
                                        WebLinksCodec.encode(webLinks.map { WebLinkUi(it.title, it.url) }, textNow)
                                    } else textNow
                                    // Update DB on IO; refresh UI on main
                                    val now = System.currentTimeMillis()
                                    if (now - lastStreamUpdateAt < streamUpdateMinIntervalMs) return@onDeltaText
                                    lastStreamUpdateAt = now

                                    viewModelScope.launch(Dispatchers.IO) {
                                        val msg = db.messages().getById(assistantId) ?: return@launch
                                        db.messages().update(msg.copy(content = encodedNow))
                                        withContext(Dispatchers.Main) {
                                            refreshMessages(convId)
                                        }
                                    }
                                },
                                    onDone = { _ ->
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val msg = db.messages().getById(assistantId) ?: return@launch
                                        db.messages().update(
                                            msg.copy(
                                                routedProviderId = routedProviderId,
                                                routedApiKeyId = routedApiKeyId,
                                                routedModelId = routedModelId
                                            )
                                        )
                                        db.conversations().touch(convId, System.currentTimeMillis())
                                        withContext(Dispatchers.Main) {
                                            refreshMessages(convId)
                                        }
                                    }
                                },
                                onError = { err ->
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val msg = db.messages().getById(assistantId) ?: return@launch
                                        if (msg.content.isBlank()) {
                                            db.messages().update(msg.copy(content = "请求失败：${err}"))
                                        }
                                        withContext(Dispatchers.Main) {
                                            refreshMessages(convId)
                                        }
                                    }
                                }
                            )
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                // Never crash the app on stream setup failure.
                                withContext(Dispatchers.IO) {
                                    val msg = db.messages().getById(assistantId) ?: return@withContext
                                    if (msg.content.isBlank()) {
                                        val err = if (t is OutOfMemoryError) "内存不足（已自动降级/截断搜索结果）" else (t.message ?: "未知错误")
                                        db.messages().update(msg.copy(content = "请求失败：${err}"))
                                    }
                                }
                                refreshMessages(convId)
                            }
                        }
                    }
                } finally {
                    runningJob = null
                    runningCall = null
                    runningAssistantMessageId = null
                    _requestState.postValue(RequestState(false, ""))
                }
            }
            runningJob = job
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                // Never crash UI on send.
                _requestState.postValue(RequestState(false, ""))
                // Best-effort: show error as a toast via application context.
                val msg = if (t is OutOfMemoryError) "内存不足" else (t.message ?: "未知错误")
                try {
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(getApplication(), "发送失败：${msg}")
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    fun cancelRunningRequest() {
        try {
            runningCall?.cancel()
        } catch (_: Throwable) {
            // ignore
        }

        // Mark interrupted under truncated message
        val convId = _conversationId.value
        val assistantId = runningAssistantMessageId
        if (convId != null && assistantId != null) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    val msg = db.messages().getById(assistantId) ?: return@withContext
                    val decoded = WebLinksCodec.decode(msg.content)
                    val suffix = "\n\n（已中断）"
                    val next = if (decoded.content.endsWith(suffix)) decoded.content else (decoded.content + suffix)
                    val encoded = if (decoded.links.isNotEmpty()) WebLinksCodec.encode(decoded.links, next) else next
                    db.messages().update(msg.copy(content = encoded))
                }
                refreshMessages(convId)
            }
        }

        runningJob?.cancel(CancellationException("user canceled"))
        runningJob = null
        runningCall = null
        runningAssistantMessageId = null
        _requestState.postValue(RequestState(false, ""))
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

        val replyEncoded = if (out.webLinks.isNotEmpty()) {
            WebLinksCodec.encode(
                out.webLinks.map { WebLinkUi(it.title, it.url) },
                out.text
            )
        } else out.text

        withContext(Dispatchers.IO) {
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = replyEncoded,
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
        // If assistant message contains embedded web links header, parse it.
        // Format (inserted by us):
        // [WEB_LINKS]
        // title|url
        // ...
        // [/WEB_LINKS]
        // <actual content>
        val parsed = WebLinksCodec.decode(content)
        return MessageUi(
            id = id,
            role = role,
            content = parsed.content,
            createdAt = createdAt,
            webLinks = parsed.links
        )
    }
}

private object WebLinksCodec {
    private const val START = "[WEB_LINKS]"
    private const val END = "[/WEB_LINKS]"

    data class Decoded(
        val content: String,
        val links: List<WebLinkUi>
    )

    fun encode(links: List<WebLinkUi>, content: String): String {
        if (links.isEmpty()) return content
        val sb = StringBuilder()
        sb.append(START).append('\n')
        links.forEach { l ->
            sb.append(l.title.replace("\n", " ").trim())
            sb.append('|')
            sb.append(l.url.replace("\n", " ").trim())
            sb.append('\n')
        }
        sb.append(END).append('\n')
        sb.append(content)
        return sb.toString()
    }

    fun decode(raw: String): Decoded {
        val t = raw
        val startIdx = t.indexOf(START)
        if (startIdx != 0) {
            return Decoded(content = raw, links = emptyList())
        }
        val endIdx = t.indexOf(END)
        if (endIdx < 0) {
            return Decoded(content = raw, links = emptyList())
        }
        val linksBlock = t.substring(START.length, endIdx)
        val lines = linksBlock.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val links = lines.mapNotNull { line ->
            val sep = line.indexOf('|')
            if (sep <= 0) return@mapNotNull null
            val title = line.substring(0, sep).trim()
            val url = line.substring(sep + 1).trim()
            if (url.isBlank()) return@mapNotNull null
            WebLinkUi(title = if (title.isBlank()) url else title, url = url)
        }
        val after = t.substring(endIdx + END.length)
        val content = after.trimStart('\n', '\r', ' ')
        return Decoded(content = content, links = links)
    }
}
