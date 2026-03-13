package com.haidianfirstteam.nostalgiaai.ui.chat

import android.content.Context
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler
import okhttp3.OkHttpClient

object MarkwonFactory {
    fun create(context: Context): Markwon {
        val client = OkHttpClient.Builder().build()
        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create { plugin ->
                // Use OkHttp for http/https image loading.
                plugin.addSchemeHandler(OkHttpNetworkSchemeHandler.create(client))
            })
            .build()
    }
}
