package com.haidianfirstteam.nostalgiaai.ui.music.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.haidianfirstteam.nostalgiaai.net.HttpClients
import okhttp3.HttpUrl
import okhttp3.Request

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
            .addQueryParameter("name", keyword)
            .addQueryParameter("count", count.toString())
            .addQueryParameter("pages", page.toString())
            .build()

        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body()?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code()} ${resp.message()} ${body.take(300)}")
            }
            val el: JsonElement = JsonParser.parseString(body)
            if (!el.isJsonArray) return emptyList()
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
