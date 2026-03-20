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
import java.io.PrintWriter
import java.io.StringWriter

data class RequestState(
    val inFlight: Boolean,
    val statusText: String = ""
)

data class BranchNavState(
    val enabled: Boolean,
    val index: Int,
    val total: Int
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

    private val _branchNav = MutableLiveData<BranchNavState>(BranchNavState(false, 1, 1))
    val branchNav: LiveData<BranchNavState> = _branchNav

    private val _requestState = MutableLiveData<RequestState>(RequestState(false, ""))
    val requestState: LiveData<RequestState> = _requestState

    private val _errorDialog = MutableLiveData<String?>(null)
    val errorDialog: LiveData<String?> = _errorDialog

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
                val parentId = conv.activeLeafMessageId
                val shouldUpdateTitle = parentId == null
                userMsgId = db.messages().insert(
                    MessageEntity(
                        conversationId = convId,
                        role = "user",
                        content = trimmed,
                        createdAt = now
                        ,parentId = parentId
                        ,targetType = targetType
                        ,targetGroupId = targetGroupId
                        ,targetProviderId = targetProviderId
                        ,targetModelId = targetModelId
                        ,webSearchEnabled = webSearchEnabled
                        ,webSearchCount = webSearchCount
                    )
                )
                db.conversations().touch(convId, now)
                db.conversations().setActiveLeaf(convId, userMsgId)

                // Set conversation title to first user prompt snippet.
                if (shouldUpdateTitle) {
                    val existing = db.conversations().getById(convId) ?: return@withContext
                    db.conversations().update(
                        existing.copy(
                            title = makeConversationTitleFromFirstPrompt(trimmed),
                            updatedAt = now
                        )
                    )
                }
            }
            // Save attachments
            withContext(Dispatchers.IO) {
                attachmentStore.saveForMessage(userMsgId, pickedAttachments)
            }
            refreshMessages(convId)

            startAssistantRequest(convId, userMsgId)
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                postErrorDialog("发送失败", t)
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

    private fun startAssistantRequest(conversationId: Long, userMsgId: Long) {
        // Real pipeline call (stream/compat/off). Keep a reference so UI can cancel.
        val job = viewModelScope.launch {
            try {
                val userMsg = withContext(Dispatchers.IO) { db.messages().getById(userMsgId) }
                if (userMsg != null) {
                    _requestState.value = RequestState(true, getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.status_calling_model))
                    runAssistantForUserMessage(conversationId, userMsg)
                }
            } finally {
                runningJob = null
                runningCall = null
                runningAssistantMessageId = null
                _requestState.postValue(RequestState(false, ""))
            }
        }
        runningJob = job
    }

    private suspend fun runAssistantForUserMessage(conversationId: Long, userMsg: MessageEntity) {
        val (streamMode, compatMs) = withContext(Dispatchers.IO) {
            val mode = settingsRepo.getStreamModeBlocking()
            val ms = settingsRepo.getCompatStreamIntervalMsBlocking()
            Pair(mode, ms)
        }

        if (streamMode == "off") {
            val out = try {
                val result = pipeline.runOnce(userMsg)
                result.getOrElse { e ->
                    postErrorDialog("请求失败（非流式）", e)
                    com.haidianfirstteam.nostalgiaai.domain.ChatPipeline.Output(
                        text = "请求失败：${e.message ?: "未知错误"}",
                        routedProviderId = null,
                        routedApiKeyId = null,
                        routedModelId = null
                    )
                }
            } catch (e: Exception) {
                postErrorDialog("请求失败（非流式）", e)
                com.haidianfirstteam.nostalgiaai.domain.ChatPipeline.Output(
                    text = "请求失败：${e.message ?: "未知错误"}",
                    routedProviderId = null,
                    routedApiKeyId = null,
                    routedModelId = null
                )
            }

            if (!currentCoroutineContext().isActive) return

            val replyText = out.text
            val encoded = if (out.webLinks.isNotEmpty()) {
                WebLinksCodec.encode(out.webLinks.map { WebLinkUi(it.title, it.url) }, replyText)
            } else replyText

            withContext(Dispatchers.IO) {
                db.messages().insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "assistant",
                        content = encoded,
                        createdAt = System.currentTimeMillis(),
                        parentId = userMsg.id,
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
                ).also { newAssistantId ->
                    db.conversations().setActiveLeaf(conversationId, newAssistantId)
                }
                db.conversations().touch(conversationId, System.currentTimeMillis())
            }
            refreshMessages(conversationId)
            return
        }

        // streaming / compat
        var assistantId: Long = -1
        withContext(Dispatchers.IO) {
            assistantId = db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "",
                    createdAt = System.currentTimeMillis(),
                    parentId = userMsg.id,
                    targetType = userMsg.targetType,
                    targetGroupId = userMsg.targetGroupId,
                    targetProviderId = userMsg.targetProviderId,
                    targetModelId = userMsg.targetModelId,
                    webSearchEnabled = userMsg.webSearchEnabled,
                    webSearchCount = userMsg.webSearchCount
                )
            )
            db.conversations().setActiveLeaf(conversationId, assistantId)
            db.conversations().touch(conversationId, System.currentTimeMillis())
        }
        runningAssistantMessageId = assistantId
        refreshMessages(conversationId)

        val sb = StringBuilder()
        var routedProviderId: Long? = null
        var routedApiKeyId: Long? = null
        var routedModelId: Long? = null
        var webLinksUi: List<WebLinkUi> = emptyList()

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
                    webLinksUi = out0.webLinks.map { WebLinkUi(it.title, it.url) }
                    updateAssistantWebLinksPreview(assistantId, webLinksUi)
                },
                onDeltaText = onDeltaText@{ delta ->
                    sb.append(delta)
                    val textNow = sb.toString()
                    val now = System.currentTimeMillis()
                    if (now - lastStreamUpdateAt < streamUpdateMinIntervalMs) return@onDeltaText
                    lastStreamUpdateAt = now
                    updateAssistantPreview(assistantId, textNow)
                },
                onDone = { _ ->
                    viewModelScope.launch(Dispatchers.IO) {
                        val msg = db.messages().getById(assistantId) ?: return@launch
                        val finalText = sb.toString()
                        val encoded = if (webLinksUi.isNotEmpty()) WebLinksCodec.encode(webLinksUi, finalText) else finalText
                        db.messages().update(
                            msg.copy(
                                content = encoded,
                                routedProviderId = routedProviderId,
                                routedApiKeyId = routedApiKeyId,
                                routedModelId = routedModelId
                            )
                        )
                        db.conversations().touch(conversationId, System.currentTimeMillis())
                        withContext(Dispatchers.Main) {
                            refreshMessages(conversationId)
                        }
                    }
                },
                onError = { err ->
                    _errorDialog.postValue("请求失败（流式）\n\n$err")
                    viewModelScope.launch(Dispatchers.IO) {
                        val msg = db.messages().getById(assistantId) ?: return@launch
                        if (msg.content.isBlank()) {
                            db.messages().update(msg.copy(content = "请求失败：${err}"))
                        }
                        withContext(Dispatchers.Main) {
                            refreshMessages(conversationId)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            postErrorDialog("请求失败（流式启动）", t)
            withContext(Dispatchers.IO) {
                val msg = db.messages().getById(assistantId) ?: return@withContext
                if (msg.content.isBlank()) {
                    val err = if (t is OutOfMemoryError) "内存不足（已自动降级/截断搜索结果）" else (t.message ?: "未知错误")
                    db.messages().update(msg.copy(content = "请求失败：${err}"))
                }
            }
            refreshMessages(conversationId)
        }
    }

    fun clearErrorDialog() {
        _errorDialog.value = null
    }

    private fun updateAssistantPreview(assistantId: Long, textNow: String) {
        val current = _messages.value ?: return
        val idx = current.indexOfFirst { it.id == assistantId }
        if (idx < 0) return
        val old = current[idx]
        val next = old.copy(content = textNow)
        val list = current.toMutableList()
        list[idx] = next
        _messages.postValue(list)
    }

    private fun updateAssistantWebLinksPreview(assistantId: Long, links: List<WebLinkUi>) {
        if (links.isEmpty()) return
        val current = _messages.value ?: return
        val idx = current.indexOfFirst { it.id == assistantId }
        if (idx < 0) return
        val old = current[idx]
        if (old.webLinks.isNotEmpty()) return
        val next = old.copy(webLinks = links)
        val list = current.toMutableList()
        list[idx] = next
        _messages.postValue(list)
    }

    private fun postErrorDialog(prefix: String, t: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println(prefix)
            pw.println()
            pw.println(t::class.java.name + ": " + (t.message ?: ""))
            pw.println()
            t.printStackTrace(pw)
            pw.flush()
            _errorDialog.postValue(sw.toString())
        } catch (_: Throwable) {
            _errorDialog.postValue(prefix + ": " + (t.message ?: "未知错误"))
        }
    }

    private fun makeConversationTitleFromFirstPrompt(prompt: String): String {
        val t = prompt.trim().replace("\n", " ").replace("\r", " ")
        if (t.isBlank()) return getApplication<Application>().getString(com.haidianfirstteam.nostalgiaai.R.string.nav_new_chat)
        val scale = try {
            settingsRepo.getFontScaleBlocking()
        } catch (_: Throwable) {
            1.0f
        }
        val maxChars = (10f / scale).toInt().coerceIn(4, 10)
        val head = t.take(maxChars)
        return if (t.length > maxChars) head + "..." else head
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

    fun switchBranch(delta: Int) {
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) {
                val conv = db.conversations().getById(convId) ?: return@withContext false
                val leafId = conv.activeLeafMessageId ?: return@withContext false
                val nav = computeBranchNavState(convId, leafId)
                if (!nav.enabled || nav.total <= 1) return@withContext false

                val pivot = navPivot(convId, leafId) ?: return@withContext false
                val (siblings, currentIdx) = pivot
                val nextIdxRaw = currentIdx + if (delta >= 0) 1 else -1
                val nextIdx = ((nextIdxRaw % siblings.size) + siblings.size) % siblings.size
                val startId = siblings[nextIdx].id
                val newLeaf = latestLeafFrom(convId, startId)
                db.conversations().setActiveLeaf(convId, newLeaf)
                true
            }
            if (changed) {
                refreshMessages(convId)
            }
        }
    }

    fun deleteMessagePair(messageId: Long) {
        val convId = _conversationId.value ?: return
        // If deleting the currently streaming assistant, cancel first.
        if (runningAssistantMessageId == messageId) {
            cancelRunningRequest()
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Delete within the current active branch path.
                val conv = db.conversations().getById(convId) ?: return@withContext
                val leafId = conv.activeLeafMessageId
                val path = if (leafId == null) {
                    db.messages().listByConversation(convId)
                } else {
                    val out = ArrayList<MessageEntity>()
                    var cur: Long? = leafId
                    var guard = 0
                    while (cur != null && guard++ < 5000) {
                        val m = db.messages().getById(cur) ?: break
                        if (m.conversationId != convId) break
                        out.add(m)
                        cur = m.parentId
                    }
                    out.reverse(); out
                }

                val idx = path.indexOfFirst { it.id == messageId }
                if (idx < 0) return@withContext
                val msg = path[idx]

                var userId: Long? = null
                var assistantId: Long? = null
                var newLeaf: Long? = null

                if (msg.role == "user") {
                    userId = msg.id
                    assistantId = path.getOrNull(idx + 1)?.takeIf { it.role == "assistant" }?.id
                    newLeaf = msg.parentId
                } else if (msg.role == "assistant") {
                    assistantId = msg.id
                    userId = path.getOrNull(idx - 1)?.takeIf { it.role == "user" }?.id
                    newLeaf = (userId?.let { db.messages().getById(it)?.parentId })
                }

                userId?.let { db.messages().deleteById(it) }
                assistantId?.let { db.messages().deleteById(it) }
                db.conversations().setActiveLeaf(convId, newLeaf)
                db.conversations().touch(convId, System.currentTimeMillis())
            }
            refreshMessages(convId)
        }
    }

    private suspend fun latestLeafFrom(conversationId: Long, startId: Long): Long {
        var curId = startId
        var guard = 0
        while (guard++ < 5000) {
            val children = db.messages().listChildren(conversationId, curId)
            if (children.isEmpty()) return curId
            curId = children.last().id
        }
        return curId
    }

    // Returns siblings list and current index for the nearest pivot.
    private suspend fun navPivot(conversationId: Long, leafId: Long): Pair<List<MessageEntity>, Int>? {
        // 1) Walk up from leaf and find first node that has siblings under the same parent.
        var curId: Long? = leafId
        var guard = 0
        while (curId != null && guard++ < 5000) {
            val cur = db.messages().getById(curId) ?: return null
            val parentId = cur.parentId
            if (parentId != null) {
                val siblings = db.messages().listChildren(conversationId, parentId)
                if (siblings.size > 1) {
                    val idx = siblings.indexOfFirst { it.id == cur.id }
                    if (idx >= 0) return Pair(siblings, idx)
                }
            } else {
                // root: check multiple roots
                val roots = db.messages().listRoots(conversationId)
                if (roots.size > 1) {
                    val idx = roots.indexOfFirst { it.id == cur.id }
                    if (idx >= 0) return Pair(roots, idx)
                }
            }
            curId = cur.parentId
        }
        return null
    }

    private suspend fun computeBranchNavState(conversationId: Long, leafId: Long): BranchNavState {
        val pivot = navPivot(conversationId, leafId) ?: return BranchNavState(false, 1, 1)
        val total = pivot.first.size
        return if (total <= 1) BranchNavState(false, 1, 1) else BranchNavState(true, pivot.second + 1, total)
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

            // Create a new user message variant (branch) instead of overwriting/deleting.
            val newUserId = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val id = db.messages().insert(
                    userMsg.copy(
                        id = 0,
                        content = newContent,
                        createdAt = now
                        // keep parentId to branch from the same point
                    )
                )
                db.conversations().setActiveLeaf(convId, id)
                db.conversations().touch(convId, now)
                id
            }
            refreshMessages(convId)
            startAssistantRequest(convId, newUserId)
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
                    "assistant" -> msg.parentId
                    "user" -> msg.id
                    else -> null
                }
            }

            refreshMessages(convId)
            if (userMsgIdToRun != null) {
                // Retrying creates a new assistant child under the same user message.
                startAssistantRequest(convId, userMsgIdToRun)
            } else {
                _requestState.postValue(RequestState(false, ""))
            }
        }
    }

    private suspend fun refreshMessages(conversationId: Long) {
        val list = withContext(Dispatchers.IO) {
            val conv = db.conversations().getById(conversationId)
            val leafId = conv?.activeLeafMessageId
            if (leafId == null) {
                db.messages().listByConversation(conversationId)
            } else {
                val out = ArrayList<MessageEntity>()
                var cur: Long? = leafId
                var guard = 0
                while (cur != null && guard++ < 5000) {
                    val m = db.messages().getById(cur) ?: break
                    if (m.conversationId != conversationId) break
                    out.add(m)
                    cur = m.parentId
                }
                out.reverse()
                out
            }
        }
        _messages.value = list.map { it.toUi() }

        // Update branch navigation state.
        viewModelScope.launch(Dispatchers.IO) {
            val conv = db.conversations().getById(conversationId) ?: return@launch
            val leafId = conv.activeLeafMessageId ?: run {
                _branchNav.postValue(BranchNavState(false, 1, 1))
                return@launch
            }
            val st = computeBranchNavState(conversationId, leafId)
            _branchNav.postValue(st)
        }
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
