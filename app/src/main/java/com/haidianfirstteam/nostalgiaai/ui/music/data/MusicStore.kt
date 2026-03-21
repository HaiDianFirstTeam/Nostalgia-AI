package com.haidianfirstteam.nostalgiaai.ui.music.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.AppSettingEntity
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicSourceType

class MusicStore(
    private val db: AppDatabase,
    private val gson: Gson = Gson()
) {
    private companion object {
        private const val KEY_SOURCE = "music_source"
        private const val KEY_SEARCH_HISTORY = "music_search_history"
        private const val KEY_SETTINGS = "music_settings"
        private const val KEY_PLAY_HISTORY = "music_play_history"
        private const val KEY_PLAYLISTS = "music_playlists"
        private const val KEY_LOCAL_MUSIC = "music_local_music"
        private const val MAX_HISTORY = 30
    }

    suspend fun getSource(): MusicSourceType {
        return getSettings().source
    }

    suspend fun setSource(type: MusicSourceType) {
        val cur = getSettings()
        setSettings(cur.copy(source = type))
    }

    suspend fun getSettings(): MusicSettings {
        val raw = db.appSettings().get(KEY_SETTINGS)?.value
        if (raw.isNullOrBlank()) {
            // Migrate old key_source if present.
            val legacy = db.appSettings().get(KEY_SOURCE)?.value
            // Source2 (API2) has been removed; always migrate to Source1.
            val src = MusicSourceType.API1_GDSTUDIO
            val s = MusicSettings(source = src, downloadSource = src)
            setSettings(s)
            return s
        }

        val parsed = try {
            gson.fromJson(raw, MusicSettings::class.java) ?: MusicSettings()
        } catch (_: Throwable) {
            MusicSettings()
        }

        // Source2 (API2) has been removed. Force-migrate persisted settings.
        if (parsed.source == MusicSourceType.API2_WYAPI || parsed.downloadSource == MusicSourceType.API2_WYAPI) {
            val next = parsed.copy(
                source = MusicSourceType.API1_GDSTUDIO,
                downloadSource = MusicSourceType.API1_GDSTUDIO,
                quality = parsed.quality.copy(streamLevel = null, downloadLevel = null)
            )
            setSettings(next)
            return next
        }

        return parsed
    }

    suspend fun setSettings(settings: MusicSettings) {
        db.appSettings().put(AppSettingEntity(KEY_SETTINGS, gson.toJson(settings)))
        // keep legacy key for backward compatibility
        db.appSettings().put(AppSettingEntity(KEY_SOURCE, settings.source.name))
    }

    suspend fun listPlaylists(): List<MusicPlaylist> {
        val raw = db.appSettings().get(KEY_PLAYLISTS)?.value ?: return emptyList()
        return try {
            val t = object : TypeToken<List<MusicPlaylist>>() {}.type
            val list: List<MusicPlaylist> = gson.fromJson(raw, t) ?: emptyList()
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun upsertPlaylist(pl: MusicPlaylist) {
        val list = listPlaylists().toMutableList()
        val idx = list.indexOfFirst { it.id == pl.id }
        if (idx >= 0) list[idx] = pl else list.add(0, pl)
        db.appSettings().put(AppSettingEntity(KEY_PLAYLISTS, gson.toJson(list)))
    }

    suspend fun deletePlaylist(id: Long) {
        val list = listPlaylists().filterNot { it.id == id }
        db.appSettings().put(AppSettingEntity(KEY_PLAYLISTS, gson.toJson(list)))
    }

    suspend fun createPlaylist(name: String): MusicPlaylist {
        val now = System.currentTimeMillis()
        val pl = MusicPlaylist(id = now, name = name.trim().ifBlank { "新歌单" }, createdAt = now, tracks = emptyList())
        upsertPlaylist(pl)
        return pl
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack) {
        val list = listPlaylists().toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        val cur = list[idx]
        val nextTracks = cur.tracks.toMutableList()
        // de-dup by (source,id)
        nextTracks.removeAll { it.source == track.source && it.id == track.id }
        nextTracks.add(track)
        list[idx] = cur.copy(tracks = nextTracks)
        db.appSettings().put(AppSettingEntity(KEY_PLAYLISTS, gson.toJson(list)))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, track: com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack) {
        val list = listPlaylists().toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        val cur = list[idx]
        val nextTracks = cur.tracks.filterNot { it.source == track.source && it.id == track.id }
        list[idx] = cur.copy(tracks = nextTracks)
        db.appSettings().put(AppSettingEntity(KEY_PLAYLISTS, gson.toJson(list)))
    }

    suspend fun listPlayHistory(): List<MusicPlayHistoryItem> {
        val raw = db.appSettings().get(KEY_PLAY_HISTORY)?.value ?: return emptyList()
        return try {
            val t = object : TypeToken<List<MusicPlayHistoryItem>>() {}.type
            val list: List<MusicPlayHistoryItem> = gson.fromJson(raw, t) ?: emptyList()
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun addPlayHistory(track: com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack) {
        val now = System.currentTimeMillis()
        val old = listPlayHistory().toMutableList()
        // keep recent unique by (source,id)
        old.removeAll { it.track.source == track.source && it.track.id == track.id }
        old.add(0, MusicPlayHistoryItem(playedAt = now, track = track))
        val next = old.take(200)
        db.appSettings().put(AppSettingEntity(KEY_PLAY_HISTORY, gson.toJson(next)))
    }

    suspend fun deletePlayHistory(item: MusicPlayHistoryItem) {
        val next = listPlayHistory().filterNot { it.playedAt == item.playedAt && it.track.id == item.track.id && it.track.source == item.track.source }
        db.appSettings().put(AppSettingEntity(KEY_PLAY_HISTORY, gson.toJson(next)))
    }

    suspend fun clearPlayHistory() {
        db.appSettings().put(AppSettingEntity(KEY_PLAY_HISTORY, gson.toJson(emptyList<MusicPlayHistoryItem>())))
    }

    suspend fun listSearchHistory(): List<String> {
        val raw = db.appSettings().get(KEY_SEARCH_HISTORY)?.value ?: return emptyList()
        return try {
            val t = object : TypeToken<List<String>>() {}.type
            val list: List<String> = gson.fromJson(raw, t) ?: emptyList()
            list.filter { it.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val old = listSearchHistory().toMutableList()
        old.removeAll { it.equals(q, ignoreCase = true) }
        old.add(0, q)
        val next = old.take(MAX_HISTORY)
        db.appSettings().put(AppSettingEntity(KEY_SEARCH_HISTORY, gson.toJson(next)))
    }

    suspend fun deleteSearchHistory(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val next = listSearchHistory().filterNot { it.equals(q, ignoreCase = true) }
        db.appSettings().put(AppSettingEntity(KEY_SEARCH_HISTORY, gson.toJson(next)))
    }

    suspend fun clearSearchHistory() {
        db.appSettings().put(AppSettingEntity(KEY_SEARCH_HISTORY, gson.toJson(emptyList<String>())))
    }

    suspend fun listLocalMusic(): List<LocalMusicItem> {
        val raw = db.appSettings().get(KEY_LOCAL_MUSIC)?.value ?: return emptyList()
        return try {
            val t = object : TypeToken<List<LocalMusicItem>>() {}.type
            val list: List<LocalMusicItem> = gson.fromJson(raw, t) ?: emptyList()
            // stable ordering: newest first
            list.sortedByDescending { it.addedAt }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun upsertLocalMusic(items: List<LocalMusicItem>) {
        val old = listLocalMusic().toMutableList()
        // de-dup by uri
        for (it in items) {
            old.removeAll { x -> x.uri == it.uri }
            old.add(0, it)
        }
        // keep a reasonable cap
        val next = old.take(1000)
        db.appSettings().put(AppSettingEntity(KEY_LOCAL_MUSIC, gson.toJson(next)))
    }

    suspend fun deleteLocalMusic(uris: Set<String>) {
        if (uris.isEmpty()) return
        val next = listLocalMusic().filterNot { uris.contains(it.uri) }
        db.appSettings().put(AppSettingEntity(KEY_LOCAL_MUSIC, gson.toJson(next)))
    }

    suspend fun clearLocalMusic() {
        db.appSettings().put(AppSettingEntity(KEY_LOCAL_MUSIC, gson.toJson(emptyList<LocalMusicItem>())))
    }
}
