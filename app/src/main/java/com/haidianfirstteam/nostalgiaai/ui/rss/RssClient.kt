package com.haidianfirstteam.nostalgiaai.ui.rss

import com.haidianfirstteam.nostalgiaai.net.HttpClients
import okhttp3.Request

class RssClient {
    data class Fetched(
        val finalUrl: String,
        val body: String
    )

    fun fetch(url: String): Fetched {
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.1")
            .build()
        val client = HttpClients.music() // legacy TLS-compatible shared client
        client.newCall(req).execute().use { resp ->
            val body = resp.body()?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code()} ${resp.message()} ${body.take(400)}")
            }
            val finalUrl = resp.request().url().toString()
            return Fetched(finalUrl = finalUrl, body = body)
        }
    }
}
