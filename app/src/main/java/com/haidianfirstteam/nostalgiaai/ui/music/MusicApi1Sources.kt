package com.haidianfirstteam.nostalgiaai.ui.music

/**
 * Source names as described in 音源API1.md.
 *
 * NOTE: Some sources may be unstable. We keep a stable subset first.
 */
object MusicApi1Sources {
    val stable: List<String> = listOf(
        "netease",
        "kuwo",
        "joox",
        "bilibili"
    )

    val all: List<String> = listOf(
        // stable first
        "netease",
        "kuwo",
        "joox",
        "bilibili",
        // other documented options
        "tencent",
        "migu",
        "kugou",
        "ximalaya",
        "apple",
        "spotify",
        "ytmusic",
        "qobuz",
        "deezer",
        "tidal"
    ).distinct()
}
