package com.haidianfirstteam.nostalgiaai.export

import com.google.gson.annotations.SerializedName

data class ExportBundle(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("providers") val providers: List<ProviderJson> = emptyList(),
    @SerializedName("api_keys") val apiKeys: List<ApiKeyJson> = emptyList(),
    @SerializedName("models") val models: List<ModelJson> = emptyList(),
    @SerializedName("model_groups") val modelGroups: List<ModelGroupJson> = emptyList(),
    @SerializedName("group_routes") val groupRoutes: List<GroupRouteJson> = emptyList(),
    @SerializedName("group_providers") val groupProviders: List<GroupProviderJson> = emptyList(),
    @SerializedName("tavily") val tavily: TavilyJson? = null,
    @SerializedName("settings") val settings: Map<String, String> = emptyMap(),
    @SerializedName("conversations") val conversations: List<ConversationJson> = emptyList(),
    @SerializedName("messages") val messages: List<MessageJson> = emptyList(),
    @SerializedName("attachments") val attachments: List<AttachmentJson> = emptyList()
)

data class GroupProviderJson(
    @SerializedName("id") val id: Long,
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("provider_id") val providerId: Long,
    @SerializedName("model_id") val modelId: Long,
    @SerializedName("order_index") val orderIndex: Int,
    @SerializedName("enabled") val enabled: Boolean
)

data class ProviderJson(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("base_url") val baseUrl: String
)

data class ApiKeyJson(
    @SerializedName("id") val id: Long,
    @SerializedName("provider_id") val providerId: Long,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("api_key") val apiKey: String,
    @SerializedName("enabled") val enabled: Boolean
)

data class ModelJson(
    @SerializedName("id") val id: Long,
    @SerializedName("provider_id") val providerId: Long,
    @SerializedName("model_name") val modelName: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("multimodal") val multimodal: Boolean
)

data class ModelGroupJson(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String
)

data class GroupRouteJson(
    @SerializedName("id") val id: Long,
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("provider_id") val providerId: Long,
    @SerializedName("api_key_id") val apiKeyId: Long,
    @SerializedName("model_id") val modelId: Long,
    @SerializedName("enabled") val enabled: Boolean
)

data class TavilyJson(
    @SerializedName("base_url") val baseUrl: String,
    @SerializedName("keys") val keys: List<TavilyKeyJson>
)

data class TavilyKeyJson(
    @SerializedName("id") val id: Long,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("api_key") val apiKey: String,
    @SerializedName("enabled") val enabled: Boolean
)

data class ConversationJson(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long
)

data class MessageJson(
    @SerializedName("id") val id: Long,
    @SerializedName("conversation_id") val conversationId: Long,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("model_group_id") val modelGroupId: Long?,
    @SerializedName("target_type") val targetType: String? = null,
    @SerializedName("target_group_id") val targetGroupId: Long? = null,
    @SerializedName("target_provider_id") val targetProviderId: Long? = null,
    @SerializedName("target_model_id") val targetModelId: Long? = null,
    @SerializedName("routed_provider_id") val routedProviderId: Long? = null,
    @SerializedName("routed_api_key_id") val routedApiKeyId: Long? = null,
    @SerializedName("routed_model_id") val routedModelId: Long? = null,
    @SerializedName("web_search_enabled") val webSearchEnabled: Boolean,
    @SerializedName("web_search_count") val webSearchCount: Int
)

data class AttachmentJson(
    @SerializedName("id") val id: Long,
    @SerializedName("message_id") val messageId: Long,
    @SerializedName("uri") val uri: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("size_bytes") val sizeBytes: Long
)
