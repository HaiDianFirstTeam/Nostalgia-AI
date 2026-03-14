package com.haidianfirstteam.nostalgiaai.net

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
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

    data class StreamChunk(
        val deltaText: String? = null,
        val done: Boolean = false,
        val error: String? = null
    )

    /**
     * OpenAI-compatible streaming chat completions (SSE).
     * - Reads lines starting with "data:"
     * - Stops on "[DONE]"
     * - For each JSON payload, extracts choices[].delta.content (best-effort)
     */
    @Throws(IOException::class)
    fun chatCompletionsStream(
        req: OpenAiChatRequest,
        onChunk: (StreamChunk) -> Unit
    ): Call {
        val url = normalizeBaseUrl(baseUrl) + "/chat/completions"
        val json = gson.toJson(req.copy(stream = true))
        val body = RequestBody.create(MediaType.parse("application/json"), json)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val call = client.newCall(request)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val text = resp.body()?.string() ?: ""
                    onChunk(StreamChunk(error = "HTTP ${resp.code()} ${resp.message()}\n$text"))
                    return call
                }
                val stream = resp.body()?.byteStream() ?: run {
                    onChunk(StreamChunk(error = "empty response body"))
                    return call
                }
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    val t = line.trim()
                    if (t.isEmpty()) continue
                    if (!t.startsWith("data:")) continue
                    val payload = t.removePrefix("data:").trim()
                    if (payload == "[DONE]") {
                        onChunk(StreamChunk(done = true))
                        break
                    }
                    // Try parse chunk JSON
                    try {
                        val obj = gson.fromJson(payload, java.util.Map::class.java)
                        val choices = obj["choices"] as? java.util.List<*>
                        val first = choices?.firstOrNull() as? java.util.Map<*, *>
                        val delta = first?.get("delta") as? java.util.Map<*, *>
                        val content = delta?.get("content") as? String
                        if (!content.isNullOrEmpty()) {
                            onChunk(StreamChunk(deltaText = content))
                        }
                    } catch (_: Exception) {
                        // ignore malformed chunks
                    }
                }
            }
        } catch (e: IOException) {
            if (call.isCanceled()) {
                onChunk(StreamChunk(done = true))
            } else {
                onChunk(StreamChunk(error = e.message))
            }
        }
        return call
    }

    private fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim()
        if (u.endsWith("/")) u = u.dropLast(1)
        return u
    }
}
