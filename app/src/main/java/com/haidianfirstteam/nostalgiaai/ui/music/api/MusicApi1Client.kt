package com.haidianfirstteam.nostalgiaai.ui.music.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.haidianfirstteam.nostalgiaai.net.HttpClients
import okhttp3.HttpUrl
import okhttp3.Request
import java.nio.charset.Charset

/**
 * API1: GD Studio music-api.gdstudio.xyz
 */
class MusicApi1Client(
    private val gson: Gson = Gson(),
    private val baseUrl: String = "https://music-api.gdstudio.xyz/api.php",
) {
    private val client = HttpClients.music()

    fun search(source: String, keyword: String, page: Int, count: Int): List<MusicTrack> {
        val url = HttpUrl.parse(baseUrl)!!.newBuilder()
            .addQueryParameter("types", "search")
            .addQueryParameter("source", source)
            // This API is known to behave better with GB18030-encoded keywords.
            // Using addEncodedQueryParameter prevents HttpUrl from re-encoding our percent-encoded bytes.
            .addEncodedQueryParameter("name", percentEncodeGbk(keyword))
            .addQueryParameter("count", count.toString())
            .addQueryParameter("pages", page.toString())
            .build()

        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val bytes = resp.body()?.bytes() ?: ByteArray(0)
            val body = decodeBestEffort(bytes)
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code()} ${resp.message()} ${body.take(300)}")
            }
            val el: JsonElement = try {
                JsonParser.parseString(body)
            } catch (e: Throwable) {
                throw IllegalStateException("invalid json: ${e.message}\n${body.take(300)}")
            }
            if (!el.isJsonArray) {
                // API may return error object: {"detail":"..."}
                val msg = try {
                    if (el.isJsonObject) el.asJsonObject.get("detail")?.asString ?: body.take(300) else body.take(300)
                } catch (_: Throwable) {
                    body.take(300)
                }
                throw IllegalStateException(msg)
            }
            val arr = el.asJsonArray
            val out = ArrayList<MusicTrack>(arr.size())
            for (e in arr) {
                val o = e.asJsonObject
                val id = o.get("id")?.asString ?: continue
                val name = o.get("name")?.asString ?: ""
                val album = o.get("album")?.asString
                val coverId = o.get("pic_id")?.asString
                val src = o.get("source")?.asString ?: source
                val artists = try {
                    val a = o.getAsJsonArray("artist")
                    a?.mapNotNull { it?.asString } ?: emptyList()
                } catch (_: Throwable) {
                    emptyList()
                }
                out.add(
                    MusicTrack(
                        id = id,
                        name = name,
                        artists = artists,
                        album = album,
                        coverId = coverId,
                        source = src,
                    )
                )
            }
            return out
        }
    }

    private fun decodeBestEffort(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        // This API sometimes returns non-UTF8 bytes but declares application/json.
        // Try UTF-8 first; if it contains lots of replacement chars, fall back to GB18030.
        fun decode(cs: Charset): String = try { String(bytes, cs) } catch (_: Throwable) { "" }
        val utf8 = decode(Charsets.UTF_8)
        val bad = utf8.count { it == '\uFFFD' }
        if (bad <= 2) return utf8
        val gb = decode(Charset.forName("GB18030"))
        return if (gb.isNotBlank()) gb else utf8
    }

    private fun percentEncodeGbk(s: String): String {
        val cs = Charset.forName("GB18030")
        val bytes = s.toByteArray(cs)
        val out = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            val c = v.toChar()
            val unreserved = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '-' || c == '_' || c == '.' || c == '~'
            if (unreserved) {
                out.append(c)
            } else {
                out.append('%')
                val hex = v.toString(16).uppercase()
                if (hex.length == 1) out.append('0')
                out.append(hex)
            }
        }
        return out.toString()
    }

    fun getPlayUrl(source: String, trackId: String, br: Int): MusicPlayUrl {
        val url = HttpUrl.parse(baseUrl)!!.newBuilder()
            .addQueryParameter("types", "url")
            .addQueryParameter("source", source)
            .addQueryParameter("id", trackId)
            .addQueryParameter("br", br.toString())
            .build()
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body()?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code()} ${resp.message()} ${body.take(300)}")
            }
            val o = JsonParser.parseString(body).asJsonObject
            val playUrl = o.get("url")?.asString ?: ""
            val actualBr = o.get("br")?.asString
            val size = o.get("size")?.asLong
            if (playUrl.isBlank()) throw IllegalStateException("empty url")
            return MusicPlayUrl(url = playUrl, qualityLabel = actualBr, sizeKb = size)
        }
    }

    fun getCoverUrl(source: String, picId: String, size: Int = 300): String? {
        val url = HttpUrl.parse(baseUrl)!!.newBuilder()
            .addQueryParameter("types", "pic")
            .addQueryParameter("source", source)
            .addQueryParameter("id", picId)
            .addQueryParameter("size", size.toString())
            .build()
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body()?.string().orEmpty()
            if (!resp.isSuccessful) return null
            return try {
                JsonParser.parseString(body).asJsonObject.get("url")?.asString
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun getLyric(source: String, lyricId: String): Pair<String?, String?> {
        val url = HttpUrl.parse(baseUrl)!!.newBuilder()
            .addQueryParameter("types", "lyric")
            .addQueryParameter("source", source)
            .addQueryParameter("id", lyricId)
            .build()
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body()?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code()} ${resp.message()} ${body.take(300)}")
            }
            val o = JsonParser.parseString(body).asJsonObject
            val lyric = o.get("lyric")?.asString
            val tlyric = o.get("tlyric")?.asString
            return Pair(lyric, tlyric)
        }
    }
}
