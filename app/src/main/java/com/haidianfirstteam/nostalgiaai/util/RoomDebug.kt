package com.haidianfirstteam.nostalgiaai.util

import android.content.Context
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RoomDebug {
    /**
     * Minimal runtime sanity checks. Not a test suite.
     */
    suspend fun check(context: Context, db: AppDatabase): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("DB ok\n")
        sb.append("providers=").append(db.providers().listAll().size).append('\n')
        sb.append("keys=").append(db.apiKeys().listAll().size).append('\n')
        sb.append("models=").append(db.models().listAll().size).append('\n')
        sb.append("groups=").append(db.modelGroups().listAll().size).append('\n')
        sb.append("groupProviders=").append(db.groupProviders().listAll().size).append('\n')
        sb.append("conversations=").append(db.conversations().listAll().size).append('\n')
        sb.append("messages=").append(db.messages().listAll().size).append('\n')
        sb.toString()
    }
}
