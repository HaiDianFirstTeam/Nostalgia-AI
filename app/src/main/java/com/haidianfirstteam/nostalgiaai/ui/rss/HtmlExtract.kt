package com.haidianfirstteam.nostalgiaai.ui.rss

object HtmlExtract {
    private val imgRe = Regex("<img\\s+[^>]*src=\\\"([^\\\"]+)\\\"[^>]*>", RegexOption.IGNORE_CASE)
    private val aRe = Regex("<a\\s+[^>]*href=\\\"([^\\\"]+)\\\"[^>]*>", RegexOption.IGNORE_CASE)
    private val tagRe = Regex("<[^>]+>")
    private val brRe = Regex("<(br|/p|/div|/li|/h\\d)\\s*>", setOf(RegexOption.IGNORE_CASE))
    private val entityRe = Regex("&(#?)([a-zA-Z0-9]+);")

    fun firstImageUrl(html: String): String? {
        if (html.isBlank()) return null
        val m = imgRe.find(html) ?: return null
        val url = m.groupValues.getOrNull(1)?.trim().orEmpty()
        if (url.isBlank()) return null
        return url
    }

    fun toPlainText(html: String): String {
        if (html.isBlank()) return ""
        var s = html
        // Convert some block-ish tags to newlines
        s = brRe.replace(s, "\n")
        // Strip tags
        s = tagRe.replace(s, "")
        // Decode minimal entities
        s = entityRe.replace(s) { m0 ->
            val isNum = m0.groupValues[1] == "#"
            val body = m0.groupValues[2]
            if (isNum) {
                val code = body.toIntOrNull() ?: return@replace ""
                code.toChar().toString()
            } else {
                when (body.lowercase()) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    else -> ""
                }
            }
        }
        // Normalize whitespace
        s = s.replace("\r", "")
        s = s.replace(Regex("[\\t ]+"), " ")
        s = s.replace(Regex("\n{3,}"), "\n\n")
        return s.trim()
    }

    fun discoverFeedLinks(html: String): List<String> {
        // Simple discovery: <link rel="alternate" type="application/rss+xml" href="...">
        val re = Regex("<link\\s+[^>]*rel=\\\"alternate\\\"[^>]*>", RegexOption.IGNORE_CASE)
        val typeRe = Regex("type=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
        val hrefRe = Regex("href=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
        val out = ArrayList<String>()
        for (m in re.findAll(html)) {
            val tag = m.value
            val type = typeRe.find(tag)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
            if (type != "application/rss+xml" && type != "application/atom+xml") continue
            val href = hrefRe.find(tag)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (href.isNotBlank()) out.add(href)
        }
        return out.distinct()
    }
}
