package com.haidianfirstteam.nostalgiaai.net

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val gson: Gson = Gson()
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        // OkHttp 3.12 compatible with Android 4.4.2
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun chatCompletions(req: OpenAiChatRequest): OpenAiChatResponse {
        // IMPORTANT: Do NOT auto-append "/v1". Users may provide baseUrl with or without it.
        // We only append the endpoint path.
        val url = normalizeBaseUrl(baseUrl) + "/chat/completions"
        val json = gson.toJson(req)
        val body = RequestBody.create(MediaType.parse("application/json"), json)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body()?.string() ?: ""
            if (!resp.isSuccessful) {
                // Try parse OpenAI error
                return try {
                    val parsed = gson.fromJson(text, OpenAiChatResponse::class.java)
                    if (parsed.error == null) {
                        OpenAiChatResponse(error = OpenAiError(message = "HTTP ${resp.code()} ${resp.message()}\n$text"))
                    } else parsed
                } catch (e: Exception) {
                    OpenAiChatResponse(error = OpenAiError(message = "HTTP ${resp.code()} ${resp.message()}\n$text"))
                }
            }
            return gson.fromJson(text, OpenAiChatResponse::class.java)
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim()
        if (u.endsWith("/")) u = u.dropLast(1)
        return u
    }
}
