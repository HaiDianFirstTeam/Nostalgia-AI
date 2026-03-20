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
)
