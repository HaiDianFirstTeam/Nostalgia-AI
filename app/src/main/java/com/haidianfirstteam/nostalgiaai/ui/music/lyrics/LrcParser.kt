package com.haidianfirstteam.nostalgiaai.ui.music.lyrics

data class LrcLine(
    val timeMs: Long,
    val text: String,
    val trans: String? = null,
)

object LrcParser {
    private val timeRe = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]")

    fun parse(lrc: String?): List<LrcLine> = parseInternal(lrc)

    /**
     * Parse bilingual lyrics (original + translated LRC).
     *
     * Strategy:
     * - parse both as timed lines
     * - align translation by timestamp (with tolerance)
     * - keep original line timestamps as the primary timeline
     */
    fun parseBilingual(originalLrc: String?, translatedLrc: String?): List<LrcLine> {
        val original = parseInternal(originalLrc)
        val translated = parseInternal(translatedLrc)
        if (original.isEmpty()) return emptyList()
        if (translated.isEmpty()) return original

        // Two-pointer merge on sorted timeMs.
        val tolMs = 180L
        val out = ArrayList<LrcLine>(original.size)
        var j = 0
        for (i in original.indices) {
            val o = original[i]
            while (j + 1 < translated.size && translated[j + 1].timeMs <= o.timeMs) {
                j++
            }
            val candA = translated.getOrNull(j)
            val candB = translated.getOrNull(j + 1)
            val best = pickClosest(o.timeMs, candA, candB)
            val trans = if (best != null && kotlin.math.abs(best.timeMs - o.timeMs) <= tolMs) best.text else null
            out.add(LrcLine(timeMs = o.timeMs, text = o.text, trans = trans))
        }
        return out
    }

    private fun pickClosest(target: Long, a: LrcLine?, b: LrcLine?): LrcLine? {
        if (a == null) return b
        if (b == null) return a
        val da = kotlin.math.abs(a.timeMs - target)
        val db = kotlin.math.abs(b.timeMs - target)
        return if (da <= db) a else b
    }

    private fun parseInternal(lrc: String?): List<LrcLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        val out = ArrayList<LrcLine>()
        val lines = lrc.split("\n")
        for (raw in lines) {
            val m = timeRe.findAll(raw).toList()
            if (m.isEmpty()) continue
            val text = raw.replace(timeRe, "").trim()
            if (text.isBlank()) continue
            for (mm in m) {
                val min = mm.groupValues[1].toLongOrNull() ?: 0
                val sec = mm.groupValues[2].toLongOrNull() ?: 0
                val msRaw = mm.groupValues.getOrNull(3).orEmpty()
                val ms = when (msRaw.length) {
                    0 -> 0
                    1 -> msRaw.toLongOrNull()?.times(100) ?: 0
                    2 -> msRaw.toLongOrNull()?.times(10) ?: 0
                    else -> msRaw.take(3).toLongOrNull() ?: 0
                }
                val t = (min * 60_000L) + (sec * 1000L) + ms
                out.add(LrcLine(timeMs = t, text = text, trans = null))
            }
        }
        out.sortBy { it.timeMs }
        return out
    }
}
