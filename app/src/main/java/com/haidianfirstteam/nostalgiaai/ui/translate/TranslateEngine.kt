package com.haidianfirstteam.nostalgiaai.ui.translate

import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.net.OpenAiChatRequest
import com.haidianfirstteam.nostalgiaai.net.OpenAiClient
import com.haidianfirstteam.nostalgiaai.net.OpenAiMessage

/**
 * Stateless translation request engine.
 * Picks a workable provider/key/model from existing configured data.
 */
class TranslateEngine(private val db: AppDatabase) {

    data class Route(
        val baseUrl: String,
        val apiKey: String,
        val modelName: String,
    )

    suspend fun translate(settings: TranslateSettings, text: String, history: List<TranslateHistoryItem>): String {
        val route = pickRoute(settings)
        val sys = buildSystemPrompt(settings)
        val messages = ArrayList<OpenAiMessage>()
        messages.add(OpenAiMessage("system", sys))

        // Memory enabled: include a small sliding window of previous translations.
        if (settings.memoryEnabled) {
            val ctx = history.take(6).reversed()
            for (h in ctx) {
                messages.add(OpenAiMessage("user", h.input))
                messages.add(OpenAiMessage("assistant", h.output))
            }
        }

        messages.add(OpenAiMessage("user", text))

        val req = OpenAiChatRequest(
            model = route.modelName,
            messages = messages,
            stream = false
        )
        val client = OpenAiClient(route.baseUrl, route.apiKey)
        val resp = client.chatCompletions(req)
        return resp.firstTextString()?.trim().orEmpty()
    }

    private suspend fun pickRoute(settings: TranslateSettings): Route {
        // If user selected a specific route, honor it.
        when (settings.routeType) {
            "group" -> {
                val gid = settings.routeGroupId
                if (gid != null) {
                    val gp = db.groupProviders().listByGroup(gid).firstOrNull { it.enabled }
                    if (gp != null) {
                        val provider = db.providers().getById(gp.providerId)
                        val model = db.models().getById(gp.modelId)
                        val key = db.apiKeys().listByProvider(gp.providerId).firstOrNull { it.enabled }
                        if (provider != null && model != null && key != null) {
                            return Route(provider.baseUrl, key.apiKey, model.modelName)
                        }
                    }
                }
            }
            "direct" -> {
                val pid = settings.routeProviderId
                val mid = settings.routeModelId
                if (pid != null && mid != null) {
                    val provider = db.providers().getById(pid)
                    val model = db.models().getById(mid)
                    val key = db.apiKeys().listByProvider(pid).firstOrNull { it.enabled }
                    if (provider != null && model != null && key != null) {
                        return Route(provider.baseUrl, key.apiKey, model.modelName)
                    }
                }
            }
        }
        // Prefer group_providers first (user curated).
        val gp = db.groupProviders().listAll().firstOrNull { it.enabled }
        if (gp != null) {
            val provider = db.providers().getById(gp.providerId)
            val model = db.models().getById(gp.modelId)
            val key = db.apiKeys().listByProvider(gp.providerId).firstOrNull { it.enabled }
            if (provider != null && model != null && key != null) {
                return Route(provider.baseUrl, key.apiKey, model.modelName)
            }
        }
        // Fallback: first provider+model+enabled key.
        val provider = db.providers().listAll().firstOrNull() ?: throw IllegalStateException("暂无 Provider")
        val model = db.models().listAll().firstOrNull { it.providerId == provider.id } ?: throw IllegalStateException("暂无 Model")
        val key = db.apiKeys().listByProvider(provider.id).firstOrNull { it.enabled } ?: throw IllegalStateException("暂无可用 Key")
        return Route(provider.baseUrl, key.apiKey, model.modelName)
    }

    private fun buildSystemPrompt(s: TranslateSettings): String {
        val a = s.langA
        val b = s.langB
        val modeText = when (s.mode) {
            TranslateMode.WORD -> "单词/词组"
            TranslateMode.SENTENCE -> "句子"
            TranslateMode.ARTICLE -> "文章"
        }
        val identity = s.identity.trim()
        val who = if (identity.isBlank()) "" else "\n\n你的身份设定：${identity}"
        val from = if (a == "auto") "自动检测源语言" else "源语言：${a}"
        return (
            "你是一个严格的翻译引擎。任务：把用户输入翻译为目标语言。\n" +
                "${from}；目标语言：${b}；翻译模式：${modeText}。" +
                who +
                "\n\n输出规则（必须遵守）：\n" +
                "1) 只输出翻译结果本身，不要解释，不要加前后缀，不要加引号，不要加多余换行。\n" +
                "2) 保持原文格式：如果是单词/词组就精简；如果是句子/文章就自然流畅。\n" +
                "3) 如果无法翻译，输出空字符串。\n" +
                "4) 禁止输出任何提示语，例如：'翻译如下'、'译文：' 等。"
        )
    }
}

// Small helper to read first assistant text
private fun com.haidianfirstteam.nostalgiaai.net.OpenAiChatResponse.firstTextString(): String? {
    return try {
        val c = choices.firstOrNull()?.message?.content
        when (c) {
            is String -> c
            is Map<*, *> -> (c["text"] as? String) ?: c.values.firstOrNull { it is String } as? String
            is List<*> -> c.firstOrNull { it is String } as? String
            else -> c?.toString()
        }
    } catch (_: Throwable) {
        null
    }
}
