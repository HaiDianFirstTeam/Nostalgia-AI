package com.haidianfirstteam.nostalgiaai.ui.music

import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack

data class MusicAlbumUi(
    val key: String,
    val source: String,
    val albumName: String,
    val coverId: String?,
    val trackCount: Int,
    val artistsSummary: String,
    val tracks: List<MusicTrack>
)
