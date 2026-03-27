package com.haidianfirstteam.nostalgiaai.ui.rss

/** RSS source (subscription). */
data class RssSource(
    val id: Long,
    val url: String,
    val nick: String,
    val title: String,
    val iconUrl: String?,
    val createdAt: Long,
    val lastSyncAt: Long,
    val lastError: String?,
)

/** RSS/Atom item (an entry/article). */
data class RssItem(
    val id: Long,
    val sourceId: Long,
    val guid: String,
    val title: String,
    val link: String,
    val author: String,
    val imageUrl: String?,
    val summary: String,
    val contentHtml: String,
    val publishedAt: Long,
    val fetchedAt: Long,
)
