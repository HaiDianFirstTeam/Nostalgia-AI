package com.haidianfirstteam.nostalgiaai.net

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TavilyClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val gson: Gson = Gson()
) {
    private val client = LegacyTls.enableTls12OnPreLollipop(
        OkHttpClient.Builder()
    )
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun search(query: String, maxResults: Int): TavilySearchResponse {
        val url = normalizeBaseUrl(baseUrl) + "/search"
        val req = TavilySearchRequest(
            apiKey = apiKey,
            query = query,
            maxResults = maxResults.coerceAtLeast(1)
        )
        val json = gson.toJson(req)
        val body = RequestBody.create(MediaType.parse("application/json"), json)
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val bodyResp = resp.body()
            if (bodyResp == null) {
                return TavilySearchResponse(error = "HTTP ${resp.code()} ${resp.message()} (empty body)")
            }
            if (!resp.isSuccessful) {
                val text = try {
                    bodyResp.string()
                } catch (_: Throwable) {
                    ""
                }
                val short = if (text.length > 2000) text.substring(0, 2000) + "\n..." else text
                return TavilySearchResponse(error = "HTTP ${resp.code()} ${resp.message()} $short")
            }
            // Avoid holding the whole response string in memory.
            return bodyResp.charStream().use { reader ->
                gson.fromJson(reader, TavilySearchResponse::class.java)
            }
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim()
        if (u.endsWith("/")) u = u.dropLast(1)
        return u
    }
}

data class TavilySearchRequest(
    @SerializedName("api_key") val apiKey: String,
    @SerializedName("query") val query: String,
    @SerializedName("max_results") val maxResults: Int,
    @SerializedName("search_depth") val searchDepth: String = "basic",
    @SerializedName("include_answer") val includeAnswer: Boolean = false,
    @SerializedName("include_images") val includeImages: Boolean = false,
    @SerializedName("include_raw_content") val includeRawContent: Boolean = false
)

data class TavilySearchResponse(
    @SerializedName("query") val query: String? = null,
    @SerializedName("results") val results: List<TavilyResult> = emptyList(),
    @SerializedName("error") val error: String? = null
)

data class TavilyResult(
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("content") val content: String? = null
)
