package com.haidianfirstteam.nostalgiaai.export

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ImportExportRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val gson: Gson = Gson()
) {
    enum class Section {
        PROVIDERS, API_KEYS, MODELS, MODEL_GROUPS, GROUP_ROUTES, TAVILY, SETTINGS, CONVERSATIONS
    }

    suspend fun export(sections: Set<Section>): ExportBundle = withContext(Dispatchers.IO) {
        val providers = if (sections.contains(Section.PROVIDERS)) db.providers().listAll().map { it.toJson() } else emptyList()
        val keys = if (sections.contains(Section.API_KEYS)) db.apiKeys().listAll().map { it.toJson() } else emptyList()
        val models = if (sections.contains(Section.MODELS)) db.models().listAll().map { it.toJson() } else emptyList()
        val groups = if (sections.contains(Section.MODEL_GROUPS)) db.modelGroups().listAll().map { it.toJson() } else emptyList()
        val routes = if (sections.contains(Section.GROUP_ROUTES)) db.groupRoutes().listAll().map { it.toJson() } else emptyList()

        val groupProviders = if (sections.contains(Section.GROUP_ROUTES)) {
            // For backward compatibility, keep it behind GROUP_ROUTES selection.
            db.groupProviders().listAll().map { it.toJson() }
        } else emptyList()

        val tavily = if (sections.contains(Section.TAVILY)) {
            TavilyJson(
                baseUrl = db.appSettings().get("tavily_base_url")?.value ?: "https://api.tavily.com",
                keys = db.tavilyKeys().listAll().map { it.toJson() }
            )
        } else null

        val settings = if (sections.contains(Section.SETTINGS)) {
            // Minimal: export known keys
            val keysToExport = listOf(
                "theme_mode",
                "tavily_base_url",
                "stream_mode",
                "stream_compat_interval_ms"
            )
            val map = LinkedHashMap<String, String>()
            for (k in keysToExport) {
                val v = db.appSettings().get(k)?.value
                if (v != null) map[k] = v
            }
            map
        } else emptyMap()

        val conversations = if (sections.contains(Section.CONVERSATIONS)) db.conversations().listAll().map { it.toJson() } else emptyList()
        val messages = if (sections.contains(Section.CONVERSATIONS)) db.messages().listAll().map { it.toJson() } else emptyList()
        val attachments = if (sections.contains(Section.CONVERSATIONS)) db.attachments().listAll().map { it.toJson() } else emptyList()

        ExportBundle(
            providers = providers,
            apiKeys = keys,
            models = models,
            modelGroups = groups,
            groupRoutes = routes,
            groupProviders = groupProviders,
            tavily = tavily,
            settings = settings,
            conversations = conversations,
            messages = messages,
            attachments = attachments
        )
    }

    suspend fun exportToJson(sections: Set<Section>): String = withContext(Dispatchers.IO) {
        val bundle = export(sections)
        gson.toJson(bundle)
    }

    suspend fun importFromJson(json: String, overwrite: Boolean) = withContext(Dispatchers.IO) {
        val bundle = gson.fromJson(json, ExportBundle::class.java)
        importBundle(bundle, overwrite)
    }

    suspend fun exportToUri(sections: Set<Section>, uri: Uri) = withContext(Dispatchers.IO) {
        val bundle = export(sections)
        val json = gson.toJson(bundle)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("无法写入文件")
    }

    suspend fun importFromUri(uri: Uri, overwrite: Boolean) = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { ins ->
            BufferedReader(InputStreamReader(ins)).readText()
        } ?: throw IllegalStateException("无法读取文件")
        val bundle = gson.fromJson(json, ExportBundle::class.java)
        importBundle(bundle, overwrite)
    }

    private fun importBundle(bundle: ExportBundle, overwrite: Boolean) {
        // Import strategy: if overwrite -> delete & insert; else upsert-like behavior.
        // v1 minimal: overwrite is not implemented.
        if (overwrite) {
            // NOTE: full wipe is heavy; v1 minimal: not implemented.
        }

        // Build ID mapping for providers/models/groups/keys
        val providerIdMap = HashMap<Long, Long>()
        val modelIdMap = HashMap<Long, Long>()
        val groupIdMap = HashMap<Long, Long>()

        // Providers (upsert by (name, baseUrl) when possible)
        val existingProviders = db.providers().listAll()
        bundle.providers.forEach { p ->
            val match = existingProviders.firstOrNull { it.name == p.name && it.baseUrl == p.baseUrl }
            val newId = match?.id ?: db.providers().insert(ProviderEntity(name = p.name, baseUrl = p.baseUrl))
            providerIdMap[p.id] = newId
        }

        // Models
        val existingModels = db.models().listAll()
        bundle.models.forEach { m ->
            val newProviderId = providerIdMap[m.providerId] ?: return@forEach
            val match = existingModels.firstOrNull { it.providerId == newProviderId && it.modelName == m.modelName }
            val newId = match?.id ?: db.models().insert(
                ModelEntity(providerId = newProviderId, modelName = m.modelName, nickname = m.nickname, multimodal = m.multimodal)
            )
            modelIdMap[m.id] = newId
        }

        // Groups
        val existingGroups = db.modelGroups().listAll()
        bundle.modelGroups.forEach { g ->
            val match = existingGroups.firstOrNull { it.name == g.name }
            val newId = match?.id ?: db.modelGroups().insert(ModelGroupEntity(name = g.name))
            groupIdMap[g.id] = newId
        }

        // Group providers (ordered)
        bundle.groupProviders.forEach { gp ->
            val newGroupId = groupIdMap[gp.groupId] ?: return@forEach
            val newProviderId = providerIdMap[gp.providerId] ?: return@forEach
            val newModelId = modelIdMap[gp.modelId] ?: return@forEach
            db.groupProviders().insert(
                GroupProviderEntity(
                    groupId = newGroupId,
                    providerId = newProviderId,
                    modelId = newModelId,
                    orderIndex = gp.orderIndex,
                    enabled = gp.enabled
                )
            )
        }

        // API Keys (provider-scoped, keep nickname+key)
        bundle.apiKeys.forEach { k ->
            val newProviderId = providerIdMap[k.providerId] ?: return@forEach
            db.apiKeys().insert(ApiKeyEntity(providerId = newProviderId, nickname = k.nickname, apiKey = k.apiKey, enabled = k.enabled))
        }
        // Tavily base url
        bundle.tavily?.let {
            db.appSettings().put(AppSettingEntity("tavily_base_url", it.baseUrl))
            it.keys.forEach { k -> db.tavilyKeys().insert(TavilyKeyEntity(nickname = k.nickname, apiKey = k.apiKey, enabled = k.enabled)) }
        }
        // Settings
        bundle.settings.forEach { (k, v) -> db.appSettings().put(AppSettingEntity(k, v)) }

        // Conversations + messages + attachments
        if (bundle.conversations.isNotEmpty()) {
            val convIdMap = HashMap<Long, Long>()
            val existingConvs = db.conversations().listAll()
            for (c in bundle.conversations) {
                val match = existingConvs.firstOrNull { it.title == c.title && it.createdAt == c.createdAt }
                val newId = match?.id ?: db.conversations().insert(
                    ConversationEntity(
                        title = c.title,
                        createdAt = c.createdAt,
                        updatedAt = c.updatedAt
                    )
                )
                convIdMap[c.id] = newId
            }

            val msgIdMap = HashMap<Long, Long>()
            val existingMsgs = db.messages().listAll()
            for (m in bundle.messages) {
                val newConvId = convIdMap[m.conversationId] ?: continue
                val match = existingMsgs.firstOrNull {
                    it.conversationId == newConvId && it.createdAt == m.createdAt && it.role == m.role && it.content == m.content
                }
                val newId = match?.id ?: db.messages().insert(
                    MessageEntity(
                        conversationId = newConvId,
                        role = m.role,
                        content = m.content,
                        createdAt = m.createdAt,
                        targetType = m.targetType,
                        targetGroupId = m.targetGroupId,
                        targetProviderId = m.targetProviderId,
                        targetModelId = m.targetModelId,
                        routedProviderId = m.routedProviderId,
                        routedApiKeyId = m.routedApiKeyId,
                        routedModelId = m.routedModelId,
                        webSearchEnabled = m.webSearchEnabled,
                        webSearchCount = m.webSearchCount
                    )
                )
                msgIdMap[m.id] = newId
            }

            for (a in bundle.attachments) {
                val newMsgId = msgIdMap[a.messageId] ?: continue
                db.attachments().insertAll(
                    listOf(
                        AttachmentEntity(
                            messageId = newMsgId,
                            uri = a.uri,
                            displayName = a.displayName,
                            mimeType = a.mimeType,
                            sizeBytes = a.sizeBytes
                        )
                    )
                )
            }
        }
    }

}

private fun ProviderEntity.toJson() = ProviderJson(id, name, baseUrl)
private fun ApiKeyEntity.toJson() = ApiKeyJson(id, providerId, nickname, apiKey, enabled)
private fun ModelEntity.toJson() = ModelJson(id, providerId, modelName, nickname, multimodal)
private fun ModelGroupEntity.toJson() = ModelGroupJson(id, name)
private fun GroupRouteEntity.toJson() = GroupRouteJson(id, groupId, providerId, apiKeyId, modelId, enabled)
private fun GroupProviderEntity.toJson() = GroupProviderJson(id, groupId, providerId, modelId, orderIndex, enabled)
private fun TavilyKeyEntity.toJson() = TavilyKeyJson(id, nickname, apiKey, enabled)
private fun ConversationEntity.toJson() = ConversationJson(id, title, createdAt, updatedAt)
private fun MessageEntity.toJson() = MessageJson(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    createdAt = createdAt,
    modelGroupId = modelGroupId,
    targetType = targetType,
    targetGroupId = targetGroupId,
    targetProviderId = targetProviderId,
    targetModelId = targetModelId,
    routedProviderId = routedProviderId,
    routedApiKeyId = routedApiKeyId,
    routedModelId = routedModelId,
    webSearchEnabled = webSearchEnabled,
    webSearchCount = webSearchCount
)
private fun AttachmentEntity.toJson() = AttachmentJson(id, messageId, uri, displayName, mimeType, sizeBytes)
