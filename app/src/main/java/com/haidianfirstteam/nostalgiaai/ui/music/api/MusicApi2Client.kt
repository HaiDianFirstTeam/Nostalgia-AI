package com.haidianfirstteam.nostalgiaai.ui.music.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.haidianfirstteam.nostalgiaai.net.HttpClients
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody

/**
 * API2: wyapi-eo.toubiec.cn
 * Endpoints are POST + JSON body.
 *
 * This client is intentionally tolerant to response shape differences.
 */
class MusicApi2Client(
    private val gson: Gson = Gson(),
    private val baseUrl: String = "https://wyapi-eo.toubiec.cn",
) {
    private val client = HttpClients.music()
    private val json = MediaType.parse("application/json; charset=utf-8")!!

    fun search(keyword: String, page: Int): List<MusicTrack> {
        val body = gson.toJson(mapOf("keywords" to keyword, "page" to page))
        val el = postJson("/api/music/search", body)
        val tracks = findArrayCandidates(el)
        val out = ArrayList<MusicTrack>()
        for (e in tracks) {
            val o = e.asJsonObjectOrNull() ?: continue
            val id = (o["id"] ?: o["songId"] ?: o["trackId"])?.asStringOrNull() ?: continue
            val name = (o["name"] ?: o["songName"] ?: o["title"])?.asStringOrNull().orEmpty()
            val album = (o["al"]?.asJsonObjectOrNull()?.get("name") ?: o["album"] ?: o["albumName"])?.asStringOrNull()
            val pic = (o["picUrl"] ?: o["cover"] ?: o["coverUrl"] ?: o["pic"])?.asStringOrNull()
            val artists = parseArtists(o)
            out.add(
                MusicTrack(
                    id = id,
                    name = name,
                    artists = artists,
                    album = album,
                    coverId = pic,
                    source = "wyapi",
                )
            )
        }
        return out
    }

    fun getPlayUrl(songId: String, level: String): MusicPlayUrl {
        val body = gson.toJson(mapOf("id" to songId, "level" to level))
        val el = postJson("/api/music/url", body)
        val url = findFirstString(el, listOf("url", "data.url", "data.0.url"))
        if (url.isNullOrBlank()) throw IllegalStateException("empty url")
        return MusicPlayUrl(url = url, qualityLabel = level, sizeKb = null)
    }

    fun getDetail(songId: String): JsonObject {
        val body = gson.toJson(mapOf("id" to songId))
        val el = postJson("/api/music/detail", body)
        return el.asJsonObjectOrNull() ?: JsonObject()
    }

    fun getPlaylistTracks(playlistId: String, page: Int? = null): List<MusicTrack> {
        val map = linkedMapOf<String, Any>("id" to playlistId)
        if (page != null) map["page"] = page
        val body = gson.toJson(map)
        val el = postJson("/api/music/playlist", body)
        val tracks = findArrayCandidates(el)
        val out = ArrayList<MusicTrack>()
        for (e in tracks) {
            val o = e.asJsonObjectOrNull() ?: continue
            val id = (o["id"] ?: o["songId"] ?: o["trackId"])?.asStringOrNull() ?: continue
            val name = (o["name"] ?: o["songName"] ?: o["title"])?.asStringOrNull().orEmpty()
            val album = (o["al"]?.asJsonObjectOrNull()?.get("name") ?: o["album"] ?: o["albumName"])?.asStringOrNull()
            val pic = (o["picUrl"] ?: o["cover"] ?: o["coverUrl"] ?: o["pic"])?.asStringOrNull()
            val artists = parseArtists(o)
            out.add(MusicTrack(id = id, name = name, artists = artists, album = album, coverId = pic, source = "wyapi"))
        }
        return out
    }

    /**
     * Returns Pair(mainLyricLrc, translatedLyricLrc).
     * Translated lyric is best-effort; may be null even if main lyric exists.
     */
    fun getLyricPair(songId: String): Pair<String?, String?> {
        val body = gson.toJson(mapOf("id" to songId))
        val el = postJson("/api/music/lyric", body)

        // Sometimes the response itself can be a string.
        val asStr = el.asStringOrNull()
        if (!asStr.isNullOrBlank()) return Pair(asStr, null)

        val obj = el.asJsonObjectOrNull() ?: return Pair(null, null)

        val main = findFirstLyricString(obj, listOf(
            "lyric",
            "lrc",
            "data.lyric",
            "data.lrc",
            "data",
        ))

        val trans = findFirstLyricString(obj, listOf(
            "tlyric",
            "tLyric",
            "trans",
            "translation",
            "data.tlyric",
            "data.tLyric",
            "data.trans",
            "data.translation",
            "data.tlyric.lyric",
            "data.tlyric.lrc",
        ))

        return Pair(main, trans)
    }

    fun getLyric(songId: String): String? {
        return getLyricPair(songId).first
    }

    private fun findFirstLyricString(obj: JsonObject, paths: List<String>): String? {
        for (p in paths) {
            val v = findByPath(obj, p)
            val s = v?.asStringOrNull()
            if (!s.isNullOrBlank()) return s
            // Sometimes it is object {lyric:...}
            val o = v?.asJsonObjectOrNull()
            val s2 = o?.get("lyric")?.asStringOrNull()
                ?: o?.get("lrc")?.asStringOrNull()
                ?: o?.get("tlyric")?.asStringOrNull()
                ?: o?.get("trans")?.asStringOrNull()
            if (!s2.isNullOrBlank()) return s2
        }
        return null
    }

    fun getAlbumTracks(albumId: String): List<MusicTrack> {
        val body = gson.toJson(mapOf("id" to albumId))
        val el = postJson("/api/music/album", body)
        val tracks = findArrayCandidates(el)
        val out = ArrayList<MusicTrack>()
        for (e in tracks) {
            val o = e.asJsonObjectOrNull() ?: continue
            val id = (o["id"] ?: o["songId"] ?: o["trackId"])?.asStringOrNull() ?: continue
            val name = (o["name"] ?: o["songName"] ?: o["title"])?.asStringOrNull().orEmpty()
            val album = (o["al"]?.asJsonObjectOrNull()?.get("name") ?: o["album"] ?: o["albumName"])?.asStringOrNull()
            val pic = (o["picUrl"] ?: o["cover"] ?: o["coverUrl"] ?: o["pic"])?.asStringOrNull()
            val artists = parseArtists(o)
            out.add(MusicTrack(id = id, name = name, artists = artists, album = album, coverId = pic, source = "wyapi"))
        }
        return out
    }

    private fun postJson(path: String, bodyJson: String): JsonElement {
        val url = baseUrl.trimEnd('/') + path
        val reqBody = RequestBody.create(json, bodyJson)
        val req = Request.Builder().url(url).post(reqBody).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body()?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code()} ${resp.message()} ${body.take(500)}")
            }
            return JsonParser.parseString(body)
        }
    }

    private fun parseArtists(o: JsonObject): List<String> {
        // Candidates: ar:[{name}], artists:[{name}] , artistNames:[...]
        val out = ArrayList<String>()
        try {
            val ar = o.getAsJsonArray("ar")
            if (ar != null) {
                for (e in ar) {
                    val name = e.asJsonObjectOrNull()?.get("name")?.asStringOrNull()
                    if (!name.isNullOrBlank()) out.add(name)
                }
            }
        } catch (_: Throwable) {}
        try {
            val artists = o.getAsJsonArray("artists")
            if (artists != null) {
                for (e in artists) {
                    val name = e.asJsonObjectOrNull()?.get("name")?.asStringOrNull()
                    if (!name.isNullOrBlank()) out.add(name)
                }
            }
        } catch (_: Throwable) {}
        if (out.isNotEmpty()) return out
        try {
            val names = o.getAsJsonArray("artistNames")
            if (names != null) {
                for (e in names) {
                    val n = e.asStringOrNull()
                    if (!n.isNullOrBlank()) out.add(n)
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun findArrayCandidates(el: JsonElement): JsonArray {
        // try common fields.
        val obj = el.asJsonObjectOrNull()
        val direct = el.asJsonArrayOrNull()
        if (direct != null) return direct

        val candidates = listOf(
            "data",
            "result",
            "results",
            "songs",
            "data.songs",
            "data.result",
            "data.tracks",
            "tracks",
            "playlist.tracks",
            "data.playlist.tracks",
        )
        for (p in candidates) {
            val a = findByPath(obj, p)?.asJsonArrayOrNull()
            if (a != null) return a
        }
        // fallback: first array inside object
        if (obj != null) {
            for ((_, v) in obj.entrySet()) {
                val a = v.asJsonArrayOrNull()
                if (a != null) return a
            }
        }
        return JsonArray()
    }

    private fun findFirstString(el: JsonElement, paths: List<String>): String? {
        val obj = el.asJsonObjectOrNull() ?: return null
        for (p in paths) {
            val v = findByPath(obj, p)
            val s = v?.asStringOrNull()
            if (!s.isNullOrBlank()) return s
        }
        return null
    }

    private fun findByPath(obj: JsonObject?, path: String): JsonElement? {
        if (obj == null) return null
        var cur: JsonElement = obj
        val parts = path.split('.')
        for (part in parts) {
            if (cur.isJsonObject) {
                cur = cur.asJsonObject.get(part) ?: return null
            } else if (cur.isJsonArray) {
                val idx = part.toIntOrNull() ?: return null
                val arr = cur.asJsonArray
                if (idx < 0 || idx >= arr.size()) return null
                cur = arr[idx]
            } else {
                return null
            }
        }
        return cur
    }

    private fun JsonElement.asStringOrNull(): String? {
        return try {
            if (isJsonNull) null else asString
        } catch (_: Throwable) {
            null
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return try {
            if (isJsonObject) asJsonObject else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun JsonElement.asJsonArrayOrNull(): JsonArray? {
        return try {
            if (isJsonArray) asJsonArray else null
        } catch (_: Throwable) {
            null
        }
    }
}
