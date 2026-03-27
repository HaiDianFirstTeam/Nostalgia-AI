package com.haidianfirstteam.nostalgiaai.ui.rss

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.entities.AppSettingEntity

/**
 * RSS storage backed by AppSettingEntity JSON (no Room schema changes).
 *
 * Caching policy:
 * - Keep up to [maxDays] days of items.
 * - Cap items per source to [maxItemsPerSource].
 */
class RssStore(
    private val db: AppDatabase,
    private val gson: Gson = Gson(),
    private val maxDays: Int = 7,
    private val maxItemsPerSource: Int = 200,
) {
    private companion object {
        private const val KEY_SOURCES = "rss_sources"
        private const val KEY_ITEMS = "rss_items"
        private const val KEY_NEXT_SOURCE_ID = "rss_next_source_id"
        private const val KEY_NEXT_ITEM_ID = "rss_next_item_id"
    }

    suspend fun listSources(): List<RssSource> {
        val raw = db.appSettings().get(KEY_SOURCES)?.value ?: return emptyList()
        return try {
            val t = object : TypeToken<List<RssSource>>() {}.type
            val list: List<RssSource> = gson.fromJson(raw, t) ?: emptyList()
            list.sortedByDescending { it.createdAt }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun getSourceById(id: Long): RssSource? = listSources().firstOrNull { it.id == id }

    suspend fun upsertSource(source: RssSource) {
        val list = listSources().toMutableList()
        val idx = list.indexOfFirst { it.id == source.id }
        if (idx >= 0) list[idx] = source else list.add(0, source)
        db.appSettings().put(AppSettingEntity(KEY_SOURCES, gson.toJson(list)))
    }

    suspend fun addSource(url: String, nick: String): RssSource {
        val now = System.currentTimeMillis()
        val id = nextId(KEY_NEXT_SOURCE_ID)
        val s = RssSource(
            id = id,
            url = url.trim(),
            nick = nick.trim().ifBlank { url.trim() },
            title = "",
            iconUrl = null,
            createdAt = now,
            lastSyncAt = 0L,
            lastError = null,
        )
        upsertSource(s)
        return s
    }

    suspend fun renameSource(sourceId: Long, newNick: String) {
        val s = getSourceById(sourceId) ?: return
        val t = newNick.trim()
        if (t.isBlank()) return
        upsertSource(s.copy(nick = t))
    }

    suspend fun updateSourceMeta(sourceId: Long, title: String?, iconUrl: String?, lastSyncAt: Long?, lastError: String?) {
        val s = getSourceById(sourceId) ?: return
        val next = s.copy(
            title = title?.trim().orEmpty().ifBlank { s.title },
            iconUrl = iconUrl?.trim().takeUnless { it.isNullOrBlank() } ?: s.iconUrl,
            lastSyncAt = lastSyncAt ?: s.lastSyncAt,
            lastError = lastError
        )
        upsertSource(next)
    }

    suspend fun deleteSource(sourceId: Long) {
        val nextSources = listSources().filterNot { it.id == sourceId }
        db.appSettings().put(AppSettingEntity(KEY_SOURCES, gson.toJson(nextSources)))

        val nextItems = listItemsAll().filterNot { it.sourceId == sourceId }
        db.appSettings().put(AppSettingEntity(KEY_ITEMS, gson.toJson(nextItems)))
    }

    suspend fun listItemsAll(): List<RssItem> {
        val raw = db.appSettings().get(KEY_ITEMS)?.value ?: return emptyList()
        return try {
            val t = object : TypeToken<List<RssItem>>() {}.type
            val list: List<RssItem> = gson.fromJson(raw, t) ?: emptyList()
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun getItemById(id: Long): RssItem? = listItemsAll().firstOrNull { it.id == id }

    suspend fun listItemsForSource(sourceId: Long): List<RssItem> {
        return listItemsAll().filter { it.sourceId == sourceId }
            .sortedByDescending { it.publishedAt }
    }

    suspend fun listRecommended(): List<RssItem> {
        return listItemsAll().sortedByDescending { it.publishedAt }
    }

    suspend fun upsertItems(sourceId: Long, newItems: List<RssItem>) {
        if (newItems.isEmpty()) {
            purgeOldItems()
            return
        }

        val now = System.currentTimeMillis()
        val existing = listItemsAll().toMutableList()

        // De-dup within source by guid.
        val existingByGuid = HashMap<String, RssItem>()
        for (it in existing) {
            if (it.sourceId == sourceId) {
                existingByGuid[it.guid] = it
            }
        }

        val merged = ArrayList<RssItem>()
        // Keep other sources
        merged.addAll(existing.filterNot { it.sourceId == sourceId })

        // Merge this source
        val srcList = ArrayList<RssItem>()
        for (ni in newItems) {
            val old = existingByGuid[ni.guid]
            if (old != null) {
                // keep id, update fields
                srcList.add(
                    old.copy(
                        title = ni.title,
                        link = ni.link,
                        author = ni.author,
                        imageUrl = ni.imageUrl,
                        summary = ni.summary,
                        contentHtml = ni.contentHtml,
                        publishedAt = ni.publishedAt,
                        fetchedAt = now,
                    )
                )
            } else {
                srcList.add(ni.copy(id = nextId(KEY_NEXT_ITEM_ID), sourceId = sourceId, fetchedAt = now))
            }
        }

        // Also keep existing items not present in fetched set (offline cache)
        val fetchedGuids = newItems.map { it.guid }.toHashSet()
        existing.filter { it.sourceId == sourceId && !fetchedGuids.contains(it.guid) }.forEach { srcList.add(it) }

        // Cap per source
        val capped = srcList.sortedByDescending { it.publishedAt }.take(maxItemsPerSource)
        merged.addAll(capped)

        // Purge by age
        val minTs = now - (maxDays.toLong() * 24L * 60L * 60L * 1000L)
        val merged2 = merged.filter { (it.publishedAt.coerceAtLeast(it.fetchedAt)) >= minTs }

        db.appSettings().put(AppSettingEntity(KEY_ITEMS, gson.toJson(merged2)))
    }

    suspend fun purgeOldItems() {
        val now = System.currentTimeMillis()
        val minTs = now - (maxDays.toLong() * 24L * 60L * 60L * 1000L)
        val list = listItemsAll().filter { (it.publishedAt.coerceAtLeast(it.fetchedAt)) >= minTs }
        db.appSettings().put(AppSettingEntity(KEY_ITEMS, gson.toJson(list)))
    }

    suspend fun exportOpml(): String {
        val sources = listSources()
        val sb = StringBuilder(4096)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head><title>Nostalgia-AI RSS</title></head>\n")
        sb.append("  <body>\n")
        for (s in sources) {
            val title = escapeXml(s.nick.ifBlank { s.title }.ifBlank { s.url })
            val url = escapeXml(s.url)
            sb.append("    <outline text=\"").append(title).append("\" title=\"").append(title)
                .append("\" type=\"rss\" xmlUrl=\"").append(url).append("\"/>\n")
        }
        sb.append("  </body>\n</opml>\n")
        return sb.toString()
    }

    suspend fun importOpml(xml: String): List<String> {
        // Minimal OPML extractor: find xmlUrl="..."
        val re = Regex("xmlUrl=\"([^\"]+)\"")
        return re.findAll(xml).map { it.groupValues[1] }.map { it.trim() }.filter { it.isNotBlank() }.distinct().toList()
    }

    suspend fun addSourcesIfMissing(urls: List<String>): Int {
        if (urls.isEmpty()) return 0
        val cur = listSources()
        val existing = cur.map { normalizeUrl(it.url) }.toHashSet()
        var added = 0
        for (u0 in urls) {
            val u = normalizeUrl(u0)
            if (u.isBlank()) continue
            if (existing.contains(u)) continue
            addSource(u, nick = u)
            existing.add(u)
            added++
        }
        return added
    }

    private fun normalizeUrl(url: String): String {
        val t = url.trim()
        // Remove trailing fragments/spaces.
        return t
            .replace("\u3000", " ")
            .trim()
    }

    private suspend fun nextId(key: String): Long {
        val raw = db.appSettings().get(key)?.value
        val cur = raw?.toLongOrNull() ?: 1L
        val next = cur + 1L
        db.appSettings().put(AppSettingEntity(key, next.toString()))
        return cur
    }

    private fun escapeXml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
