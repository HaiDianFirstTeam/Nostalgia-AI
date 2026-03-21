package com.haidianfirstteam.nostalgiaai.net

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader

class OpenAiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val gson: Gson = Gson(),
    private val client: OkHttpClient = HttpClients.openAi()
) {
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
        val deltaThinking: String? = null,
        val done: Boolean = false,
        val error: String? = null
    )

    /** Create an SSE streaming Call (not executed). */
    fun createChatCompletionsStreamCall(req: OpenAiChatRequest): Call {
        val url = normalizeBaseUrl(baseUrl) + "/chat/completions"
        val json = gson.toJson(req.copy(stream = true))
        val body = RequestBody.create(MediaType.parse("application/json"), json)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        return client.newCall(request)
    }

    /**
     * Execute an SSE Call and emit chunks.
     * IMPORTANT: the caller may cancel() this Call.
     */
    fun executeChatCompletionsStream(call: Call, onChunk: (StreamChunk) -> Unit) {
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val text = resp.body()?.string() ?: ""
                    onChunk(StreamChunk(error = "HTTP ${resp.code()} ${resp.message()}\n$text"))
                    return
                }
                val stream = resp.body()?.byteStream() ?: run {
                    onChunk(StreamChunk(error = "empty response body"))
                    return
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
                    parseAndEmitDelta(payload, onChunk)
                }
            }
        } catch (e: IOException) {
            if (call.isCanceled()) {
                onChunk(StreamChunk(done = true))
            } else {
                onChunk(StreamChunk(error = e.message))
            }
        }
    }

    private fun parseAndEmitDelta(payload: String, onChunk: (StreamChunk) -> Unit) {
        try {
            val rootEl = JsonParser.parseString(payload)
            if (!rootEl.isJsonObject) return
            val root = rootEl.asJsonObject

            // Error payload
            root.getAsJsonObject("error")?.get("message")?.let { msgEl ->
                val msg = msgEl.asStringOrNull()
                if (!msg.isNullOrBlank()) {
                    onChunk(StreamChunk(error = msg))
                    return
                }
            }

            val choices = root.getAsJsonArray("choices") ?: return
            if (choices.size() == 0) return
            val first = choices[0]
            if (!first.isJsonObject) return
            val choice = first.asJsonObject

            // chat.completions streaming: choices[0].delta.content
            val delta = choice.getAsJsonObject("delta")
            val deltaContent = delta?.get("content")
            // DeepSeek-like providers may stream reasoning separately.
            val deltaThinking = delta?.get("reasoning_content")?.asStringOrNull()
                ?: delta?.get("reasoning")?.asStringOrNull()
                ?: delta?.get("thinking")?.asStringOrNull()
            val text = extractText(deltaContent)
                ?: choice.get("text").asStringOrNull() // legacy completions
                ?: choice.getAsJsonObject("message")?.get("content")?.let { extractText(it) }

            if (!deltaThinking.isNullOrEmpty()) {
                onChunk(StreamChunk(deltaThinking = deltaThinking))
            }
            if (!text.isNullOrEmpty()) {
                onChunk(StreamChunk(deltaText = text))
            }
        } catch (_: Exception) {
            // ignore malformed chunks
        }
    }

    private fun extractText(el: JsonElement?): String? {
        if (el == null || el.isJsonNull) return null
        if (el.isJsonPrimitive) {
            val p = el.asJsonPrimitive
            if (p.isString) return p.asString
            return null
        }
        if (el.isJsonObject) {
            val obj = el.asJsonObject
            // Some compat providers may wrap text in {"text":"..."}
            obj.get("text")?.asStringOrNull()?.let { return it }
            return null
        }
        if (el.isJsonArray) {
            val arr = el.asJsonArray
            val sb = StringBuilder()
            for (i in 0 until arr.size()) {
                val item = arr[i]
                when {
                    item.isJsonPrimitive -> item.asStringOrNull()?.let { sb.append(it) }
                    item.isJsonObject -> {
                        val obj = item.asJsonObject
                        obj.get("text")?.asStringOrNull()?.let { sb.append(it) }
                        // also support {"type":"text","text":"..."}
                    }
                }
            }
            return if (sb.isNotEmpty()) sb.toString() else null
        }
        return null
    }

    private fun JsonElement?.asStringOrNull(): String? {
        if (this == null || this.isJsonNull) return null
        return try {
            if (this.isJsonPrimitive && this.asJsonPrimitive.isString) this.asString else null
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim()
        if (u.endsWith("/")) u = u.dropLast(1)
        return u
    }
}
