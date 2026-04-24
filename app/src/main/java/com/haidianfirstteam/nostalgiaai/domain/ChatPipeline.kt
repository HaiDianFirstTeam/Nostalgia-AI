package com.haidianfirstteam.nostalgiaai.domain

import android.content.Context
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.AttachmentEntity
import com.haidianfirstteam.nostalgiaai.data.entities.MessageEntity
import com.haidianfirstteam.nostalgiaai.util.FileUtil
import com.haidianfirstteam.nostalgiaai.net.OpenAiChatRequest
import com.haidianfirstteam.nostalgiaai.net.OpenAiClient
import com.haidianfirstteam.nostalgiaai.net.OpenAiMessage
import com.haidianfirstteam.nostalgiaai.net.TavilyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import android.util.Log

/**
 * 负责把：历史消息 + 联网搜索 + 附件（多模态/解析文本） -> OpenAI 兼容请求
 * 并根据 target（组/直连）完成 key/provider 的选择与失败降级。
 */
class ChatPipeline(
    private val context: Context,
    private val db: AppDatabase
) {

    private companion object {
        // Web search context is included in prompt. Keep it small for low-memory devices.
        private const val WEB_CONTEXT_MAX_CHARS = 8_000
        private const val WEB_RESULT_TITLE_MAX_CHARS = 160
        private const val WEB_RESULT_URL_MAX_CHARS = 400
        private const val WEB_RESULT_CONTENT_MAX_CHARS = 600

        // Keep Markdown/LaTeX rendering stable across devices (especially API19).
        // Intentionally short to reduce token usage.
        private val RENDERING_OUTPUT_SPEC: String = """
            [最高优先级] 输出格式规范（必须严格遵守，否则数学公式将渲染失败）：

            规则1（最重要）：所有 $...$ 包裹的数学公式必须独占一行！$ 左右不得有（除换行外的）任何字符。

            ❌ 禁止（AI 容易犯的错误——公式与文字混在同一行会导致渲染乱码）：
              "已知${'$'}x=1${'$'}是对的"          ← ${'$'}x=1${'$'} 和文字混在同一行
              "公式是：${'$'}x=1${'$'}然后"       ← 同上
              "计算 ${'$'}a+b${'$'} 的结果"       ← 同上
              "当 ${'$'}x>0${'$'} 时成立"        ← 同上

            ✅ 正确写法（所有 $...$ 独占一行）：
              已知
              ${'$'}x=1${'$'}
              是对的
              公式是：
              ${'$'}x=1${'$'}
              然后

            提示：即使你觉得放在同一行更"简洁"，也请务必分行。渲染引擎对行内 $...$ 的支持不稳定，容易导致整条公式变成乱码。

            规则2：方程组/分段函数必须用块公式 $$...$$ 并完整闭合。

            规则3：禁止使用全角符号（如 ＄｛｝），只能用半角 $ { }。

            规则4：Mermaid 必须用 fenced code block：```mermaid ... ```。
        """.trimIndent()
    }

    data class Output(
        val text: String,
        val routedProviderId: Long? = null,
        val routedApiKeyId: Long? = null,
        val routedModelId: Long? = null,
        val webLinks: List<WebLink> = emptyList()
    )

    enum class StreamMode {
        OFF, ON, COMPAT
    }

    data class WebLink(
        val title: String,
        val url: String
    )

    private val directKeyPicker = DirectKeyPicker(db)
    private val providerPriorityManager = ProviderPriorityManager(db)
    private val tavilyKeyRouter = TavilyKeyRouter(db)
    private val attachmentStore = AttachmentStore(context, db)

    /**
     * v1: 暂不做 streaming。
     */
    suspend fun runOnce(userMessage: MessageEntity): Result<Output> = withContext(Dispatchers.IO) {
        try {
            val history = loadBranchHistory(userMessage)

            val attachments = attachmentStore.loadForMessage(userMessage.id)

            val webEnabled = userMessage.webSearchEnabled
            val webCount = userMessage.webSearchCount
            val web = if (webEnabled) {
                fetchWeb(userMessage.content, webCount)
            } else null
            val webContext = web?.context

            when (userMessage.targetType) {
                "direct" -> {
                    val providerId = userMessage.targetProviderId ?: return@withContext Result.failure(IllegalStateException("missing providerId"))
                    val modelId = userMessage.targetModelId ?: return@withContext Result.failure(IllegalStateException("missing modelId"))
                    val provider = db.providers().getById(providerId) ?: return@withContext Result.failure(IllegalStateException("provider not found"))
                    val model = db.models().getById(modelId) ?: return@withContext Result.failure(IllegalStateException("model not found"))

                    val keys = directKeyPicker.pickKeys(providerId)
                    if (keys.isEmpty()) return@withContext Result.failure(IllegalStateException("no enabled api keys"))

                    // Try each key: 1 retry on same key, then next
                    for (k in keys) {
                        val r = callOpenAi(
                            providerId = providerId,
                            apiKeyId = k.id,
                            modelId = modelId,
                            baseUrl = provider.baseUrl,
                            apiKey = k.apiKey,
                            modelName = model.modelName,
                            history = history,
                            webContext = webContext,
                            webLinks = web?.links.orEmpty(),
                            userAttachments = attachments,
                            isMultimodalModel = model.multimodal
                        )
                        if (r.isSuccess) return@withContext r
                        // retry once
                        val r2 = callOpenAi(
                            providerId = providerId,
                            apiKeyId = k.id,
                            modelId = modelId,
                            baseUrl = provider.baseUrl,
                            apiKey = k.apiKey,
                            modelName = model.modelName,
                            history = history,
                            webContext = webContext,
                            webLinks = web?.links.orEmpty(),
                            userAttachments = attachments,
                            isMultimodalModel = model.multimodal
                        )
                        if (r2.isSuccess) return@withContext r2
                    }
                    return@withContext Result.failure(IllegalStateException("all keys failed"))
                }

                "group" -> {
                    val groupId = userMessage.targetGroupId ?: return@withContext Result.failure(IllegalStateException("missing groupId"))
                    val ordered = providerPriorityManager.orderedProvidersForGroup(groupId)
                    if (ordered.isEmpty()) {
                        return@withContext Result.failure(IllegalStateException("group has no providers configured"))
                    }

                    for (gp in ordered) {
                        val provider = db.providers().getById(gp.providerId) ?: continue
                        val model = db.models().getById(gp.modelId) ?: continue
                        val keys = directKeyPicker.pickKeys(gp.providerId)
                        if (keys.isEmpty()) {
                            providerPriorityManager.reportFailure(groupId, gp.providerId)
                            continue
                        }

                        // Pick a random key (first in shuffled list)
                        val key = keys[0]
                        val r = callOpenAi(
                            providerId = gp.providerId,
                            apiKeyId = key.id,
                            modelId = gp.modelId,
                            baseUrl = provider.baseUrl,
                            apiKey = key.apiKey,
                            modelName = model.modelName,
                            history = history,
                            webContext = webContext,
                            webLinks = web?.links.orEmpty(),
                            userAttachments = attachments,
                            isMultimodalModel = model.multimodal
                        )
                        if (r.isSuccess) {
                            providerPriorityManager.reportSuccess(groupId, gp.providerId)
                            return@withContext r
                        }
                        // Downrank immediately on first failure; success later will reset penalty to 0.
                        providerPriorityManager.reportFailure(groupId, gp.providerId)
                        // one retry on same provider (can use another key)
                        val key2 = keys.getOrNull(1) ?: key
                        val r2 = callOpenAi(
                            providerId = gp.providerId,
                            apiKeyId = key2.id,
                            modelId = gp.modelId,
                            baseUrl = provider.baseUrl,
                            apiKey = key2.apiKey,
                            modelName = model.modelName,
                            history = history,
                            webContext = webContext,
                            webLinks = web?.links.orEmpty(),
                            userAttachments = attachments,
                            isMultimodalModel = model.multimodal
                        )
                        if (r2.isSuccess) {
                            providerPriorityManager.reportSuccess(groupId, gp.providerId)
                            return@withContext r2
                        }
                        // then move to next provider
                    }
                    return@withContext Result.failure(IllegalStateException("all providers failed"))
                }

                else -> {
                    return@withContext Result.failure(IllegalStateException("targetType not set"))
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun loadBranchHistory(leaf: MessageEntity): List<MessageEntity> {
        // IMPORTANT: Branching chat uses parentId pointers. We must build history from
        // the active branch path, not the entire conversation, otherwise context will mix.
        val out = ArrayList<MessageEntity>()
        var cur: MessageEntity? = leaf
        var guard = 0
        while (cur != null && guard++ < 5000) {
            out.add(cur)
            val pid = cur.parentId ?: break
            cur = db.messages().getById(pid)
        }
        out.reverse()
        return out
    }

    data class StreamHandle(
        val call: Call,
        val routedProviderId: Long? = null,
        val routedApiKeyId: Long? = null,
        val routedModelId: Long? = null
    )

    /**
     * Streaming run: best-effort.
     * Caller receives partial text via callbacks.
     */
    suspend fun runStream(
        userMessage: MessageEntity,
        mode: StreamMode,
        compatIntervalMs: Long,
        onCallReady: (Call) -> Unit,
        onStart: (Output) -> Unit,
        onDeltaThinking: (String) -> Unit,
        onDeltaText: (String) -> Unit,
        onDone: (Output) -> Unit,
        onError: (String) -> Unit
    ): StreamHandle = withContext(Dispatchers.IO) {

        // In OFF mode, caller should use runOnce().
        // Keep runStream() for ON/COMPAT.
        val intervalMs = if (mode == StreamMode.COMPAT) compatIntervalMs.coerceAtLeast(50L) else 0L

        var lastEmit = 0L
        val buffer = StringBuilder()
        fun emitBuffered(force: Boolean = false) {
            if (buffer.isEmpty()) return
            if (intervalMs <= 0) {
                onDeltaText(buffer.toString())
                buffer.setLength(0)
                return
            }
            val now = System.currentTimeMillis()
            if (force || now - lastEmit >= intervalMs) {
                onDeltaText(buffer.toString())
                buffer.setLength(0)
                lastEmit = now
            }
        }

        val history = loadBranchHistory(userMessage)
        val attachments = attachmentStore.loadForMessage(userMessage.id)

        val webEnabled = userMessage.webSearchEnabled
        val webCount = userMessage.webSearchCount
        val web = if (webEnabled) fetchWeb(userMessage.content, webCount) else null
        val webContext = web?.context
        val webLinks = web?.links.orEmpty()

        fun buildMessages(isMultimodalModel: Boolean): List<OpenAiMessage> {
            val messages = ArrayList<OpenAiMessage>()

            // Always inject rendering spec first.
            messages.add(OpenAiMessage("system", RENDERING_OUTPUT_SPEC))

            if (!webContext.isNullOrBlank()) {
                // Encourage grounded answers when web search is enabled.
                messages.add(
                    OpenAiMessage(
                        "system",
                        "你必须基于下面的联网搜索结果来回答。如果搜索结果里没有相关信息，请明确说明无法从搜索结果中得到答案，不要编造。"
                    )
                )
                // Avoid duplicating large strings via interpolation.
                messages.add(OpenAiMessage("system", "联网搜索结果："))
                messages.add(OpenAiMessage("system", webContext))
            }
            val currentUserId = history.lastOrNull { it.role == "user" }?.id
            history.forEach { msg ->
                if (msg.id == currentUserId && attachments.isNotEmpty()) {
                    if (isMultimodalModel) {
                        val parts = buildMultimodalParts(msg.content, attachments)
                        messages.add(OpenAiMessage("user", parts))
                    } else {
                        val text = buildTextWithExtractedDocs(msg.content, attachments)
                        messages.add(OpenAiMessage("user", text))
                    }
                } else {
                    messages.add(OpenAiMessage(msg.role, msg.content))
                }
            }
            return messages
        }

        // Resolve routing (same as runOnce but returning first workable provider/key)
        when (userMessage.targetType) {
            "direct" -> {
                val providerId = userMessage.targetProviderId ?: run {
                    onError("missing providerId")
                    throw IllegalStateException("missing providerId")
                }
                val modelId = userMessage.targetModelId ?: run {
                    onError("missing modelId")
                    throw IllegalStateException("missing modelId")
                }
                val provider = db.providers().getById(providerId) ?: run {
                    onError("provider not found")
                    throw IllegalStateException("provider not found")
                }
                val model = db.models().getById(modelId) ?: run {
                    onError("model not found")
                    throw IllegalStateException("model not found")
                }
                val keys = directKeyPicker.pickKeys(providerId)
                if (keys.isEmpty()) {
                    onError("no enabled api keys")
                    throw IllegalStateException("no enabled api keys")
                }

                val out0 = Output(text = "", routedProviderId = providerId, routedApiKeyId = keys[0].id, routedModelId = modelId, webLinks = webLinks)
                onStart(out0)

                val client = OpenAiClient(provider.baseUrl, keys[0].apiKey)
                val req0 = OpenAiChatRequest(
                    model = model.modelName,
                    messages = buildMessages(model.multimodal),
                    temperature = 0.7,
                    stream = true
                )
                val call = client.createChatCompletionsStreamCall(req0)
                onCallReady(call)
                try {
                    client.executeChatCompletionsStream(call) { chunk ->
                        when {
                            chunk.done -> {
                                emitBuffered(force = true)
                                onDone(out0)
                            }
                            chunk.error != null -> {
                                emitBuffered(force = true)
                                onError(chunk.error)
                            }
                            chunk.deltaThinking != null -> {
                                onDeltaThinking(chunk.deltaThinking)
                            }
                            chunk.deltaText != null -> {
                                if (intervalMs > 0) {
                                    buffer.append(chunk.deltaText)
                                    emitBuffered(force = false)
                                } else {
                                    onDeltaText(chunk.deltaText)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    emitBuffered(force = true)
                    onError(e.message ?: "stream error")
                }
                return@withContext StreamHandle(call, providerId, keys[0].id, modelId)
            }

            "group" -> {
                val groupId = userMessage.targetGroupId ?: run {
                    onError("missing groupId")
                    throw IllegalStateException("missing groupId")
                }
                val ordered = providerPriorityManager.orderedProvidersForGroup(groupId)
                if (ordered.isEmpty()) {
                    onError("group has no providers configured")
                    throw IllegalStateException("group has no providers configured")
                }
                // pick first provider with keys
                for (gp in ordered) {
                    val provider = db.providers().getById(gp.providerId) ?: continue
                    val model = db.models().getById(gp.modelId) ?: continue
                    val keys = directKeyPicker.pickKeys(gp.providerId)
                    if (keys.isEmpty()) continue

                    val out0 = Output(text = "", routedProviderId = gp.providerId, routedApiKeyId = keys[0].id, routedModelId = gp.modelId, webLinks = webLinks)
                    onStart(out0)
                    val client = OpenAiClient(provider.baseUrl, keys[0].apiKey)
                    val req0 = OpenAiChatRequest(
                        model = model.modelName,
                        messages = buildMessages(model.multimodal),
                        temperature = 0.7,
                        stream = true
                    )
                    val call = client.createChatCompletionsStreamCall(req0)
                    onCallReady(call)
                    try {
                        client.executeChatCompletionsStream(call) { chunk ->
                            when {
                                chunk.done -> {
                                    emitBuffered(force = true)
                                    onDone(out0)
                                }
                                chunk.error != null -> {
                                    emitBuffered(force = true)
                                    onError(chunk.error)
                                }
                                chunk.deltaThinking != null -> {
                                    onDeltaThinking(chunk.deltaThinking)
                                }
                                chunk.deltaText != null -> {
                                    if (intervalMs > 0) {
                                        buffer.append(chunk.deltaText)
                                        emitBuffered(force = false)
                                    } else {
                                        onDeltaText(chunk.deltaText)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        emitBuffered(force = true)
                        onError(e.message ?: "stream error")
                    }
                    return@withContext StreamHandle(call, gp.providerId, keys[0].id, gp.modelId)
                }

                onError("all providers failed")
                throw IllegalStateException("all providers failed")
            }

            else -> {
                onError("targetType not set")
                throw IllegalStateException("targetType not set")
            }
        }
    }

    private fun callOpenAi(
        providerId: Long,
        apiKeyId: Long,
        modelId: Long,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        history: List<MessageEntity>,
        webContext: String?,
        webLinks: List<WebLink>,
        userAttachments: List<AttachmentEntity>,
        isMultimodalModel: Boolean
    ): Result<Output> {
        return try {
            val client = OpenAiClient(baseUrl, apiKey)

            val messages = ArrayList<OpenAiMessage>()
            // Always inject rendering spec first.
            messages.add(OpenAiMessage("system", RENDERING_OUTPUT_SPEC))
            if (!webContext.isNullOrBlank()) {
                messages.add(
                    OpenAiMessage(
                        "system",
                        "你必须基于下面的联网搜索结果来回答。如果搜索结果里没有相关信息，请明确说明无法从搜索结果中得到答案，不要编造。"
                    )
                )
                messages.add(OpenAiMessage("system", "联网搜索结果："))
                messages.add(OpenAiMessage("system", webContext))
            }

            // Build conversation messages.
            // For the current userMessage (last turn), enrich its content with attachments.
            val currentUserId = history.lastOrNull { it.role == "user" }?.id
            history.forEach { msg ->
                if (msg.id == currentUserId && userAttachments.isNotEmpty()) {
                    if (isMultimodalModel) {
                        val parts = buildMultimodalParts(msg.content, userAttachments)
                        messages.add(OpenAiMessage("user", parts))
                    } else {
                        val text = buildTextWithExtractedDocs(msg.content, userAttachments)
                        messages.add(OpenAiMessage("user", text))
                    }
                } else {
                    messages.add(OpenAiMessage(msg.role, msg.content))
                }
            }
            val req = OpenAiChatRequest(
                model = modelName,
                messages = messages,
                temperature = 0.7,
                stream = false
            )
            val resp = client.chatCompletions(req)
            val err = resp.error?.message
            if (err != null) {
                Result.failure(IllegalStateException(prettyOpenAiError(err)))
            } else {
                val text = resp.choices.firstOrNull()?.message?.content
                Result.success(
                    Output(
                        text = text?.toString() ?: "",
                        routedProviderId = providerId,
                        routedApiKeyId = apiKeyId,
                        routedModelId = modelId,
                        webLinks = webLinks
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun prettyOpenAiError(raw: String): String {
        // Keep it short but useful for users
        val t = raw.trim()
        if (t.length <= 2000) return t
        return t.substring(0, 2000) + "\n..."
    }

    private fun buildTextWithExtractedDocs(userText: String, atts: List<AttachmentEntity>): String {
        val sb = StringBuilder()
        sb.append(userText)
        val extracted = atts.mapNotNull { att ->
            val uri = attachmentStore.parseUri(att.uri)
            val r = TextExtractor.extract(context, uri, att.mimeType)
            if (r.ok && r.text.isNotBlank()) {
                "\n\n---\n文件: ${att.displayName}\n类型: ${att.mimeType}\n内容:\n${r.text.take(120000)}"
            } else null
        }
        if (extracted.isNotEmpty()) {
            sb.append("\n\n[附件解析文本]")
            extracted.forEach { sb.append(it) }
        }
        return sb.toString()
    }

    private fun buildMultimodalParts(userText: String, atts: List<AttachmentEntity>): List<Map<String, Any>> {
        val parts = ArrayList<Map<String, Any>>()
        parts.add(MultimodalParts.text(userText))
        atts.forEach { att ->
            val uri = attachmentStore.parseUri(att.uri)
            val picked = FileUtil.PickedFile(uri, att.displayName, att.mimeType, att.sizeBytes)
            val prepared = AttachmentProcessor.prepare(context, picked)
            when {
                att.mimeType.startsWith("image/") && !prepared.imageDataUrl.isNullOrBlank() -> {
                    parts.add(MultimodalParts.imageDataUrl(prepared.imageDataUrl))
                }
                att.mimeType.startsWith("audio/") && !prepared.audioBase64.isNullOrBlank() -> {
                    parts.add(MultimodalParts.inputAudio(prepared.audioBase64, prepared.audioFormat))
                }
                att.mimeType.startsWith("video/") && !prepared.videoPosterDataUrl.isNullOrBlank() -> {
                    parts.add(MultimodalParts.text("视频附件: ${att.displayName}（已提取封面帧）"))
                    parts.add(MultimodalParts.imageDataUrl(prepared.videoPosterDataUrl))
                }
                else -> {
                    if (!prepared.extractedText.isNullOrBlank()) {
                        parts.add(MultimodalParts.text("\n---\n文件: ${att.displayName}\n内容:\n${prepared.extractedText.take(80000)}"))
                    } else {
                        parts.add(MultimodalParts.text("附件: ${att.displayName} ${att.mimeType}"))
                    }
                }
            }
        }
        return parts
    }

    private data class WebFetch(
        val context: String,
        val links: List<WebLink>
    )

    private suspend fun fetchWeb(query: String, maxResults: Int): WebFetch? {
        return try {
            val baseUrl = db.appSettings().get("tavily_base_url")?.value ?: "https://api.tavily.com"
            val key = tavilyKeyRouter.pickOne() ?: return null
            val client = TavilyClient(baseUrl, key.apiKey)
            val resp = client.search(query, maxResults)
            if (resp.error != null) return null

            val links = ArrayList<WebLink>(resp.results.size)
            val sb = StringBuilder()

            fun takeSafe(s: String?, max: Int): String {
                val t = (s ?: "").trim()
                if (t.length <= max) return t
                return t.substring(0, max) + "…"
            }

            for (r in resp.results) {
                val url = takeSafe(r.url, WEB_RESULT_URL_MAX_CHARS)
                if (url.isBlank()) continue
                val title = takeSafe(r.title ?: url, WEB_RESULT_TITLE_MAX_CHARS)
                links.add(WebLink(title = title.ifBlank { url }, url = url))

                if (sb.length < WEB_CONTEXT_MAX_CHARS) {
                    val content = takeSafe(r.content, WEB_RESULT_CONTENT_MAX_CHARS)
                    val block = buildString {
                        append("- ")
                        append(title)
                        append(" ")
                        append(url)
                        if (content.isNotBlank()) {
                            append("\n  ")
                            append(content)
                        }
                    }
                    if (sb.isNotEmpty()) sb.append("\n")
                    if (sb.length + block.length > WEB_CONTEXT_MAX_CHARS) {
                        val remain = (WEB_CONTEXT_MAX_CHARS - sb.length).coerceAtLeast(0)
                        sb.append(block.take(remain))
                        sb.append("\n…(已截断)")
                    } else {
                        sb.append(block)
                    }
                }

                if (sb.length >= WEB_CONTEXT_MAX_CHARS) {
                    // Still collect links, but stop growing context.
                    continue
                }
            }

            WebFetch(context = sb.toString(), links = links)
        } catch (t: Throwable) {
            try {
                Log.w("NostalgiaAI", "fetchWeb failed (enabled) maxResults=$maxResults", t)
            } catch (_: Throwable) {
                // ignore
            }
            null
        }
    }
}
