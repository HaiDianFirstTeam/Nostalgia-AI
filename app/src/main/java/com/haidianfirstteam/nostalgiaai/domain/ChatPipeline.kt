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

/**
 * 负责把：历史消息 + 联网搜索 + 附件（多模态/解析文本） -> OpenAI 兼容请求
 * 并根据 target（组/直连）完成 key/provider 的选择与失败降级。
 */
class ChatPipeline(
    private val context: Context,
    private val db: AppDatabase
) {

    data class Output(
        val text: String,
        val routedProviderId: Long? = null,
        val routedApiKeyId: Long? = null,
        val routedModelId: Long? = null
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
            val convId = userMessage.conversationId
            val history = db.messages().listByConversation(convId)

            val attachments = attachmentStore.loadForMessage(userMessage.id)

            val webEnabled = userMessage.webSearchEnabled
            val webCount = userMessage.webSearchCount
            val webContext = if (webEnabled) {
                fetchWebContext(userMessage.content, webCount)
            } else null

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
        } catch (e: Exception) {
            Result.failure(e)
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
        userAttachments: List<AttachmentEntity>,
        isMultimodalModel: Boolean
    ): Result<Output> {
        return try {
            val client = OpenAiClient(baseUrl, apiKey)

            val messages = ArrayList<OpenAiMessage>()
            if (!webContext.isNullOrBlank()) {
                messages.add(OpenAiMessage("system", "联网搜索结果：\n${webContext}"))
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
                        routedModelId = modelId
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

    private fun fetchWebContext(query: String, maxResults: Int): String? {
        return try {
            val baseUrl = db.appSettings().get("tavily_base_url")?.value ?: "https://api.tavily.com"
            val key = tavilyKeyRouter.pickOne() ?: return null
            val client = TavilyClient(baseUrl, key.apiKey)
            val resp = client.search(query, maxResults)
            if (resp.error != null) return null
            resp.results.joinToString("\n") { r ->
                "- ${r.title ?: ""} ${r.url ?: ""}\n  ${r.content ?: ""}".trim()
            }
        } catch (e: Exception) {
            null
        }
    }
}
