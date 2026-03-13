package com.haidianfirstteam.nostalgiaai.data.entities

import androidx.room.*

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val createdAt: Long,

    // Snapshot of settings used when sending / generating
    // targetType: "group" | "direct"
    val targetType: String? = null,
    val targetGroupId: Long? = null,
    val targetProviderId: Long? = null,
    val targetModelId: Long? = null,

    // Actual routed choice for assistant (useful for debugging)
    val routedProviderId: Long? = null,
    val routedApiKeyId: Long? = null,
    val routedModelId: Long? = null,

    // legacy field kept for future compatibility
    val modelGroupId: Long? = null,
    val webSearchEnabled: Boolean = false,
    val webSearchCount: Int = 5
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String
)

@Entity(
    tableName = "api_keys",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId")]
)
data class ApiKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val nickname: String,
    val apiKey: String,
    val enabled: Boolean = true
)

@Entity(
    tableName = "models",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId")]
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val modelName: String,
    val nickname: String,
    val multimodal: Boolean = false
)

@Entity(tableName = "model_groups")
data class ModelGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "group_routes",
    foreignKeys = [
        ForeignKey(
            entity = ModelGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ApiKeyEntity::class,
            parentColumns = ["id"],
            childColumns = ["apiKeyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId"), Index("providerId"), Index("apiKeyId"), Index("modelId")]
)
data class GroupRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val providerId: Long,
    val apiKeyId: Long,
    val modelId: Long,
    val enabled: Boolean = true
)

/**
 * Group 级“提供商顺序”配置（你说要能调整顺序）。
 * 每一条代表：该组包含某 provider，并指定该 provider 下使用的 model。
 * provider 下的 api key 不在这里固定：会在该 provider 的 keys 中按策略选择。
 */
@Entity(
    tableName = "group_providers",
    foreignKeys = [
        ForeignKey(
            entity = ModelGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId"), Index("providerId"), Index("modelId")]
)
data class GroupProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val providerId: Long,
    val modelId: Long,
    val orderIndex: Int = 0,
    val enabled: Boolean = true
)

/**
 * 运行时“下调优先级”状态。
 * 每次 provider 调用失败：增加 penalty，使得该 provider 的 effective order 变大（更靠后）。
 */
@Entity(
    tableName = "group_provider_states",
    primaryKeys = ["groupId", "providerId"],
    indices = [Index("groupId"), Index("providerId")]
)
data class GroupProviderStateEntity(
    val groupId: Long,
    val providerId: Long,
    val penalty: Int = 0,
    val lastFailedAt: Long = 0,
    val failStreak: Int = 0
)

@Entity(tableName = "tavily_keys")
data class TavilyKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val apiKey: String,
    val enabled: Boolean = true
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
