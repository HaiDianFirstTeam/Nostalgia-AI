package com.haidianfirstteam.nostalgiaai.ui.rss

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RssParser {

    data class FeedMeta(
        val title: String,
        val iconUrl: String?,
    )

    data class Parsed(
        val meta: FeedMeta,
        val items: List<RssItem>
    )

    fun parse(sourceId: Long, authorNick: String, xml: String): Parsed {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(StringReader(xml))

        // Determine RSS vs Atom by first start tag
        moveToStartTag(p)
        val root = p.name ?: ""
        return when {
            root.equals("rss", ignoreCase = true) -> parseRss2(sourceId, authorNick, p)
            root.equals("feed", ignoreCase = true) -> parseAtom(sourceId, authorNick, p)
            else -> parseRss2(sourceId, authorNick, p)
        }
    }

    private fun parseRss2(sourceId: Long, authorNick: String, p: XmlPullParser): Parsed {
        var channelTitle = ""
        var iconUrl: String? = null
        val items = ArrayList<RssItem>()

        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> {
                        // no-op
                    }
                    "title" -> {
                        if (channelTitle.isBlank() && isInChannel(p)) {
                            channelTitle = readText(p)
                        }
                    }
                    "image" -> {
                        iconUrl = iconUrl ?: readImageUrl(p)
                    }
                    "icon" -> {
                        // webfeeds:icon
                        if (iconUrl.isNullOrBlank()) {
                            iconUrl = readText(p).trim().ifBlank { null }
                        }
                    }
                    "logo" -> {
                        // webfeeds:logo
                        if (iconUrl.isNullOrBlank()) {
                            iconUrl = readText(p).trim().ifBlank { null }
                        }
                    }
                    "item" -> {
                        items.add(readRssItem(sourceId, authorNick, p))
                    }
                }
            }
            p.next()
        }

        return Parsed(
            meta = FeedMeta(title = channelTitle, iconUrl = iconUrl),
            items = items
        )
    }

    private fun readImageUrl(p: XmlPullParser): String? {
        // <image><url>...</url>...</image>
        var url: String? = null
        val depth = p.depth
        while (!(p.eventType == XmlPullParser.END_TAG && p.depth == depth && p.name == "image")) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "url") {
                url = readText(p).trim().ifBlank { null }
            }
            p.next()
        }
        return url
    }

    private fun readRssItem(sourceId: Long, authorNick: String, p: XmlPullParser): RssItem {
        var title = ""
        var link = ""
        var guid = ""
        var pubDate = 0L
        var desc = ""
        var content = ""
        var image: String? = null

        val depth = p.depth
        while (!(p.eventType == XmlPullParser.END_TAG && p.depth == depth && p.name == "item")) {
            if (p.eventType == XmlPullParser.START_TAG) {
                when (p.name) {
                    "title" -> title = readText(p)
                    "link" -> link = readText(p)
                    "guid" -> guid = readText(p)
                    "pubDate" -> pubDate = parseDate(readText(p))
                    "description" -> desc = readText(p)
                    "encoded" -> {
                        // content:encoded
                        content = readText(p)
                    }
                    "enclosure" -> {
                        if (image.isNullOrBlank()) {
                            val type = p.getAttributeValue(null, "type") ?: ""
                            val url = p.getAttributeValue(null, "url")
                            if (url != null && type.startsWith("image/")) image = url
                        }
                    }
                }
            }
            p.next()
        }

        val html = if (content.isNotBlank()) content else desc
        val cover = image ?: HtmlExtract.firstImageUrl(html)
        val summary = HtmlExtract.toPlainText(desc.ifBlank { html }).take(280)
        val g = guid.ifBlank { link.ifBlank { title } }
        return RssItem(
            id = 0L,
            sourceId = sourceId,
            guid = g,
            title = title.ifBlank { g },
            link = link,
            author = authorNick,
            imageUrl = cover,
            summary = summary,
            contentHtml = html,
            publishedAt = pubDate,
            fetchedAt = 0L,
        )
    }

    private fun parseAtom(sourceId: Long, authorNick: String, p: XmlPullParser): Parsed {
        var title = ""
        var iconUrl: String? = null
        val items = ArrayList<RssItem>()

        val rootDepth = p.depth
        while (!(p.eventType == XmlPullParser.END_TAG && p.depth == rootDepth && p.name == "feed")) {
            if (p.eventType == XmlPullParser.START_TAG) {
                when (p.name) {
                    "title" -> if (title.isBlank()) title = readText(p)
                    "icon" -> if (iconUrl.isNullOrBlank()) iconUrl = readText(p).trim().ifBlank { null }
                    "logo" -> if (iconUrl.isNullOrBlank()) iconUrl = readText(p).trim().ifBlank { null }
                    "entry" -> items.add(readAtomEntry(sourceId, authorNick, p))
                }
            }
            p.next()
        }

        return Parsed(FeedMeta(title = title, iconUrl = iconUrl), items)
    }

    private fun readAtomEntry(sourceId: Long, authorNick: String, p: XmlPullParser): RssItem {
        var title = ""
        var link = ""
        var guid = ""
        var updated = 0L
        var summary = ""
        var content = ""

        val depth = p.depth
        while (!(p.eventType == XmlPullParser.END_TAG && p.depth == depth && p.name == "entry")) {
            if (p.eventType == XmlPullParser.START_TAG) {
                when (p.name) {
                    "title" -> title = readText(p)
                    "id" -> guid = readText(p)
                    "updated" -> updated = parseDate(readText(p))
                    "published" -> if (updated == 0L) updated = parseDate(readText(p))
                    "summary" -> summary = readText(p)
                    "content" -> content = readText(p)
                    "link" -> {
                        val rel = p.getAttributeValue(null, "rel") ?: ""
                        val href = p.getAttributeValue(null, "href")
                        if (href != null && (rel.isBlank() || rel == "alternate") && link.isBlank()) {
                            link = href
                        }
                    }
                }
            }
            p.next()
        }

        val html = if (content.isNotBlank()) content else summary
        val cover = HtmlExtract.firstImageUrl(html)
        val plain = HtmlExtract.toPlainText(summary.ifBlank { html }).take(280)
        val g = guid.ifBlank { link.ifBlank { title } }
        return RssItem(
            id = 0L,
            sourceId = sourceId,
            guid = g,
            title = title.ifBlank { g },
            link = link,
            author = authorNick,
            imageUrl = cover,
            summary = plain,
            contentHtml = html,
            publishedAt = updated,
            fetchedAt = 0L,
        )
    }

    private fun readText(p: XmlPullParser): String {
        // Works for both text nodes and CDATA
        var result = ""
        if (p.next() == XmlPullParser.TEXT) {
            result = p.text ?: ""
            p.nextTag()
        }
        return result
    }

    private fun moveToStartTag(p: XmlPullParser) {
        while (p.eventType != XmlPullParser.START_TAG && p.eventType != XmlPullParser.END_DOCUMENT) {
            p.next()
        }
    }

    private fun isInChannel(p: XmlPullParser): Boolean {
        // Heuristic: title under channel appears before any item
        return true
    }

    private fun parseDate(raw: String): Long {
        val t = raw.trim()
        if (t.isBlank()) return 0L

        // RSS: RFC822
        val fmts = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss Z",
            // Atom: ISO8601
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        )
        for (f in fmts) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(t)
                if (d != null) return d.time
            } catch (_: ParseException) {
            } catch (_: Throwable) {
            }
        }
        return 0L
    }
}
