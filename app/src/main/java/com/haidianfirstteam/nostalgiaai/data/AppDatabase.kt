package com.haidianfirstteam.nostalgiaai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.haidianfirstteam.nostalgiaai.data.dao.*
import com.haidianfirstteam.nostalgiaai.data.entities.*

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ProviderEntity::class,
        ApiKeyEntity::class,
        ModelEntity::class,
        ModelGroupEntity::class,
        GroupRouteEntity::class,
        GroupProviderEntity::class,
        GroupProviderStateEntity::class,
        TavilyKeyEntity::class,
        AppSettingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun attachments(): AttachmentDao
    abstract fun providers(): ProviderDao
    abstract fun apiKeys(): ApiKeyDao
    abstract fun models(): ModelDao
    abstract fun modelGroups(): ModelGroupDao
    abstract fun groupRoutes(): GroupRouteDao
    abstract fun groupProviders(): GroupProviderDao
    abstract fun groupProviderStates(): GroupProviderStateDao
    abstract fun tavilyKeys(): TavilyKeyDao
    abstract fun appSettings(): AppSettingDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // messages: add target/routing snapshot columns
                db.execSQL("ALTER TABLE messages ADD COLUMN targetType TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN targetGroupId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN targetProviderId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN targetModelId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN routedProviderId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN routedApiKeyId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN routedModelId INTEGER")

                // group providers (ordered provider entries per group)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS group_providers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                        "groupId INTEGER NOT NULL," +
                        "providerId INTEGER NOT NULL," +
                        "modelId INTEGER NOT NULL," +
                        "orderIndex INTEGER NOT NULL DEFAULT 0," +
                        "enabled INTEGER NOT NULL DEFAULT 1" +
                    ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_providers_groupId ON group_providers(groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_providers_providerId ON group_providers(providerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_providers_modelId ON group_providers(modelId)")

                // provider penalty state per group
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS group_provider_states (" +
                        "groupId INTEGER NOT NULL," +
                        "providerId INTEGER NOT NULL," +
                        "penalty INTEGER NOT NULL DEFAULT 0," +
                        "lastFailedAt INTEGER NOT NULL DEFAULT 0," +
                        "failStreak INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY(groupId, providerId)" +
                    ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_provider_states_groupId ON group_provider_states(groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_provider_states_providerId ON group_provider_states(providerId)")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // conversations: add active leaf pointer
                db.execSQL("ALTER TABLE conversations ADD COLUMN activeLeafMessageId INTEGER")
                // messages: add parent pointer
                db.execSQL("ALTER TABLE messages ADD COLUMN parentId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_parentId ON messages(parentId)")

                // Backfill parentId as a simple linear chain by createdAt for existing data,
                // and set activeLeafMessageId to the last message.
                val convCursor = db.query("SELECT id FROM conversations")
                convCursor.use { cc ->
                    while (cc.moveToNext()) {
                        val convId = cc.getLong(0)
                        val msgCursor = db.query(
                            "SELECT id FROM messages WHERE conversationId=? ORDER BY createdAt ASC, id ASC",
                            arrayOf(convId.toString())
                        )
                        var prev: Long? = null
                        var last: Long? = null
                        msgCursor.use { mc ->
                            while (mc.moveToNext()) {
                                val id = mc.getLong(0)
                                if (prev != null) {
                                    db.execSQL(
                                        "UPDATE messages SET parentId=? WHERE id=?",
                                        arrayOf(prev as Any, id as Any)
                                    )
                                }
                                prev = id
                                last = id
                            }
                        }
                        if (last != null) {
                            db.execSQL(
                                "UPDATE conversations SET activeLeafMessageId=? WHERE id=?",
                                arrayOf(last as Any, convId as Any)
                            )
                        }
                    }
                }
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nostalgia_ai.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
