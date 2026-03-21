package com.haidianfirstteam.nostalgiaai.ui.music.data

import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicSourceType
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack

data class MusicPlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val tracks: List<MusicTrack> = emptyList(),
)

data class MusicPlayHistoryItem(
    val playedAt: Long,
    val track: MusicTrack,
)

data class LocalMusicItem(
    val uri: String,
    val name: String,
    val addedAt: Long,
)

/**
 * Download task record for system DownloadManager.
 *
 * NOTE:
 * - downloadId is the primary key for querying progress/status.
 * - localUri/path is not persisted here; it can be queried from DownloadManager.
 */
data class MusicDownloadItem(
    val downloadId: Long,
    val createdAt: Long,
    val fileName: String,
    val track: MusicTrack,
)

enum class MusicPlayMode {
    ORDER,
    SHUFFLE,
    LOOP_ONE,
}

data class MusicQualitySettings(
    // API1 br: 128/192/320/740/999
    val streamBr: Int? = null,
    val downloadBr: Int? = null,
    // API2 level: standard/exhigh/lossless/hires...
    val streamLevel: String? = null,
    val downloadLevel: String? = null,
)

data class MusicSettings(
    val source: MusicSourceType = MusicSourceType.API1_GDSTUDIO,
    // Download can use a different source from search/stream.
    val downloadSource: MusicSourceType = MusicSourceType.API1_GDSTUDIO,
    val playMode: MusicPlayMode = MusicPlayMode.ORDER,
    val quality: MusicQualitySettings = MusicQualitySettings(),
    // Playback speed (best-effort; depends on player capabilities)
    val playbackSpeed: Float = 1.0f,
)
