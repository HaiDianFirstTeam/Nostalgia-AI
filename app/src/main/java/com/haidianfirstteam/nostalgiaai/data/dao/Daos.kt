package com.haidianfirstteam.nostalgiaai.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.haidianfirstteam.nostalgiaai.data.entities.*

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): LiveData<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id=:id")
    suspend fun getById(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun listAll(): List<ConversationEntity>

    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt=:updatedAt WHERE id=:id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("UPDATE conversations SET activeLeafMessageId=:leafId WHERE id=:id")
    suspend fun setActiveLeaf(id: Long, leafId: Long?)

    @Query("DELETE FROM conversations WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId=:conversationId ORDER BY createdAt ASC")
    fun observeByConversation(conversationId: Long): LiveData<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId=:conversationId ORDER BY createdAt ASC")
    suspend fun listByConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId=:conversationId AND parentId IS NULL ORDER BY createdAt ASC, id ASC")
    suspend fun listRoots(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId=:conversationId AND parentId=:parentId ORDER BY createdAt ASC, id ASC")
    suspend fun listChildren(conversationId: Long, parentId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId=:conversationId AND parentId IN (:parentIds) ORDER BY createdAt ASC, id ASC")
    suspend fun listChildrenForParents(conversationId: Long, parentIds: List<Long>): List<MessageEntity>

    @Query("SELECT * FROM messages")
    suspend fun listAll(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id=:id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId=:conversationId AND createdAt <= :createdAt ORDER BY createdAt ASC")
    suspend fun listUpToTime(conversationId: Long, createdAt: Long): List<MessageEntity>

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    @Update
    suspend fun update(entity: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId=:conversationId AND createdAt > :createdAt")
    suspend fun deleteAfter(conversationId: Long, createdAt: Long)

    @Query("DELETE FROM messages WHERE conversationId=:conversationId AND createdAt >= :createdAt")
    suspend fun deleteFromTime(conversationId: Long, createdAt: Long)

    @Query("DELETE FROM messages WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE messageId=:messageId")
    suspend fun listForMessage(messageId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    suspend fun listAll(): List<AttachmentEntity>

    @Insert
    suspend fun insertAll(items: List<AttachmentEntity>)

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY name ASC")
    fun observeAll(): LiveData<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id=:id")
    suspend fun getById(id: Long): ProviderEntity?

    @Query("SELECT * FROM providers")
    suspend fun listAll(): List<ProviderEntity>

    @Insert
    suspend fun insert(entity: ProviderEntity): Long

    @Update
    suspend fun update(entity: ProviderEntity)

    @Query("DELETE FROM providers WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM providers")
    suspend fun deleteAll()
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys WHERE providerId=:providerId ORDER BY nickname ASC")
    fun observeByProvider(providerId: Long): LiveData<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE providerId=:providerId ORDER BY nickname ASC")
    suspend fun listByProvider(providerId: Long): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE id=:id")
    suspend fun getById(id: Long): ApiKeyEntity?

    @Query("SELECT * FROM api_keys")
    suspend fun listAll(): List<ApiKeyEntity>

    @Insert
    suspend fun insert(entity: ApiKeyEntity): Long

    @Update
    suspend fun update(entity: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM api_keys")
    suspend fun deleteAll()
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models WHERE providerId=:providerId ORDER BY nickname ASC")
    fun observeByProvider(providerId: Long): LiveData<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id=:id")
    suspend fun getById(id: Long): ModelEntity?

    @Query("SELECT * FROM models")
    suspend fun listAll(): List<ModelEntity>

    @Query("SELECT * FROM models")
    fun observeAll(): LiveData<List<ModelEntity>>

    @Insert
    suspend fun insert(entity: ModelEntity): Long

    @Update
    suspend fun update(entity: ModelEntity)

    @Query("DELETE FROM models WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM models")
    suspend fun deleteAll()
}

@Dao
interface ModelGroupDao {
    @Query("SELECT * FROM model_groups ORDER BY name ASC")
    fun observeAll(): LiveData<List<ModelGroupEntity>>

    @Query("SELECT * FROM model_groups WHERE id=:id")
    suspend fun getById(id: Long): ModelGroupEntity?

    @Query("SELECT * FROM model_groups")
    suspend fun listAll(): List<ModelGroupEntity>

    @Insert
    suspend fun insert(entity: ModelGroupEntity): Long

    @Update
    suspend fun update(entity: ModelGroupEntity)

    @Query("DELETE FROM model_groups WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM model_groups")
    suspend fun deleteAll()
}

@Dao
interface GroupRouteDao {
    @Query("SELECT * FROM group_routes WHERE groupId=:groupId")
    fun observeByGroup(groupId: Long): LiveData<List<GroupRouteEntity>>

    @Query("SELECT * FROM group_routes WHERE groupId=:groupId")
    suspend fun listByGroup(groupId: Long): List<GroupRouteEntity>

    @Query("SELECT * FROM group_routes")
    suspend fun listAll(): List<GroupRouteEntity>

    @Query("SELECT * FROM group_routes WHERE id=:id")
    suspend fun getById(id: Long): GroupRouteEntity?

    @Insert
    suspend fun insert(entity: GroupRouteEntity): Long

    @Update
    suspend fun update(entity: GroupRouteEntity)

    @Query("DELETE FROM group_routes WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM group_routes")
    suspend fun deleteAll()
}

@Dao
interface GroupProviderDao {
    @Query("SELECT * FROM group_providers WHERE groupId=:groupId ORDER BY orderIndex ASC")
    suspend fun listByGroup(groupId: Long): List<GroupProviderEntity>

    @Query("SELECT * FROM group_providers")
    suspend fun listAll(): List<GroupProviderEntity>

    @Query("SELECT * FROM group_providers")
    fun observeAll(): LiveData<List<GroupProviderEntity>>

    @Insert
    suspend fun insert(entity: GroupProviderEntity): Long

    @Update
    suspend fun update(entity: GroupProviderEntity)

    @Query("DELETE FROM group_providers WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM group_providers")
    suspend fun deleteAll()
}

@Dao
interface GroupProviderStateDao {
    @Query("SELECT * FROM group_provider_states WHERE groupId=:groupId")
    suspend fun listByGroup(groupId: Long): List<GroupProviderStateEntity>

    @Query("SELECT * FROM group_provider_states WHERE groupId=:groupId AND providerId=:providerId")
    suspend fun get(groupId: Long, providerId: Long): GroupProviderStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupProviderStateEntity)

    @Query("DELETE FROM group_provider_states WHERE groupId=:groupId")
    suspend fun deleteByGroup(groupId: Long)

    @Query("DELETE FROM group_provider_states")
    suspend fun deleteAll()
}

@Dao
interface TavilyKeyDao {
    @Query("SELECT * FROM tavily_keys ORDER BY nickname ASC")
    fun observeAll(): LiveData<List<TavilyKeyEntity>>

    @Query("SELECT * FROM tavily_keys")
    suspend fun listAll(): List<TavilyKeyEntity>

    @Query("SELECT * FROM tavily_keys WHERE id=:id")
    suspend fun getById(id: Long): TavilyKeyEntity?

    @Insert
    suspend fun insert(entity: TavilyKeyEntity): Long

    @Update
    suspend fun update(entity: TavilyKeyEntity)

    @Query("DELETE FROM tavily_keys WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tavily_keys")
    suspend fun deleteAll()
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE `key`=:key")
    suspend fun get(key: String): AppSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: AppSettingEntity)

    @Query("DELETE FROM app_settings")
    suspend fun deleteAll()
}
