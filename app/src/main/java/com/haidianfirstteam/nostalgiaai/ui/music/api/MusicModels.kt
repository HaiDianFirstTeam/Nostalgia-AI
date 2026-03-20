package com.haidianfirstteam.nostalgiaai.ui.music.api

data class MusicTrack(
    val id: String,
    val name: String,
    val artists: List<String>,
    val album: String? = null,
    val coverId: String? = null,
    val source: String,
)

data class MusicPlayUrl(
    val url: String,
    val qualityLabel: String? = null,
    val sizeKb: Long? = null
)

enum class MusicSourceType {
    API1_GDSTUDIO,
    API2_WYAPI,
}
