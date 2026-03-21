package com.haidianfirstteam.nostalgiaai.ui.chat

import android.content.Context
import android.graphics.Color
import android.text.style.BackgroundColorSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.RenderProps
import io.noties.markwon.SpanFactory
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.simple.ext.SimpleExtPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import okhttp3.OkHttpClient
import com.haidianfirstteam.nostalgiaai.net.HttpClients

object MarkwonFactory {
    fun create(context: Context): Markwon {
        // Share a singleton OkHttpClient. Creating many OkHttpClient instances can
        // exhaust threads/memory on low-end legacy devices.
        val client: OkHttpClient = HttpClients.tavily()

        val prism4j = Prism4j(NostalgiaGrammarLocator())
        val prismTheme = Prism4jThemeDefault.create()

        val latexTextSizePx = 16F * context.resources.displayMetrics.scaledDensity
        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun processMarkdown(markdown: String): String {
                    return preprocessMarkdown(markdown)
                }
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            // Custom inline syntaxes.
            .usePlugin(
                SimpleExtPlugin.create { plugin ->
                    // Highlight: ==text==
                    // Note: keep it subtle to work on both light/dark themes.
                    val bg = Color.argb(60, 255, 235, 59) // ~#3CFFEB3B
                    plugin.addExtension(2, '=', object : SpanFactory {
                        override fun getSpans(configuration: MarkwonConfiguration, props: RenderProps): Any {
                            return BackgroundColorSpan(bg)
                        }
                    })
                }
            )
            // Inline parser is required by some extensions (e.g. LaTeX inlines).
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(latexTextSizePx) { builder ->
                builder.inlinesEnabled(true)
            })
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, prismTheme))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create { plugin ->
                // Use OkHttp for http/https image loading.
                plugin.addSchemeHandler(OkHttpNetworkSchemeHandler.create(client))
            })
            .build()
    }

    private fun preprocessMarkdown(markdown: String): String {
        if (markdown.isBlank()) return markdown

        // Work line-by-line to avoid touching fenced code blocks.
        val lines = markdown.split("\n")
        val processed = ArrayList<String>(lines.size)

        // Footnotes
        // - reference: [^id]
        // - definition: [^id]: content (continuation lines must be indented)
        val footnoteDefs = LinkedHashMap<String, StringBuilder>()

        // Definition lists (Markdown Extra style):
        // Term
        // : Definition
        // We'll transform it into:
        // **Term**
        // - Definition

        var inFence = false
        var fenceMarker: String? = null

        fun fenceMarkerFor(line: String): String? {
            val t = line.trimStart()
            return when {
                t.startsWith("```") -> "```"
                t.startsWith("~~~") -> "~~~"
                else -> null
            }
        }

        val footnoteDefStart = Regex("^\\[\\^([^\\]]+)]\\s*:\\s*(.*)$")
        var pendingFootnoteId: String? = null

        for (rawLine in lines) {
            val marker = fenceMarkerFor(rawLine)
            if (marker != null) {
                if (!inFence) {
                    inFence = true
                    fenceMarker = marker
                } else if (fenceMarker == marker) {
                    inFence = false
                    fenceMarker = null
                }
                pendingFootnoteId = null
                processed.add(rawLine)
                continue
            }

            if (inFence) {
                pendingFootnoteId = null
                processed.add(rawLine)
                continue
            }

            // Footnote definitions (collect + remove from body)
            val m = footnoteDefStart.find(rawLine)
            if (m != null) {
                val id = m.groupValues[1].trim()
                val content = m.groupValues[2]
                val sb = footnoteDefs.getOrPut(id) { StringBuilder() }
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(content)
                pendingFootnoteId = id
                continue
            }

            // Footnote continuation line: 2+ leading spaces or a tab
            if (pendingFootnoteId != null && (rawLine.startsWith("  ") || rawLine.startsWith("\t"))) {
                val sb = footnoteDefs.getOrPut(pendingFootnoteId!!) { StringBuilder() }
                sb.append('\n')
                sb.append(rawLine.trimStart())
                continue
            }
            pendingFootnoteId = null

            processed.add(rawLine)
        }

        // Now post-process non-fence content for: TOC, definition lists, sup/sub, inline math, emoji shortcuts, footnote references.
        val body = processed

        // 1) TOC: replace [TOC] (case-insensitive) with a plain directory list of headings.
        val hasTocMarker = body.any { it.trim().equals("[TOC]", ignoreCase = true) }
        val headings = if (hasTocMarker) {
            body.mapNotNull { line ->
                val t = line.trimStart()
                val hashes = t.takeWhile { it == '#' }
                if (hashes.isEmpty() || hashes.length > 6) return@mapNotNull null
                val title = t.drop(hashes.length).trim()
                if (title.isBlank()) return@mapNotNull null
                hashes.length to title
            }
        } else emptyList()

        // 2) Build footnote numbering by first appearance in body.
        val footnoteRef = Regex("\\[\\^([^\\]]+)]")
        val footnoteOrder = LinkedHashMap<String, Int>()
        fun noteNumber(id: String): Int {
            return footnoteOrder.getOrPut(id) { footnoteOrder.size + 1 }
        }

        // Emoji shortcuts (minimal, keep unicode emojis as-is)
        val emojiMap: Map<String, String> = mapOf(
            "+1" to "👍",
            "thumbsup" to "👍",
            "-1" to "👎",
            "thumbsdown" to "👎",
            "smile" to "😄",
            "grin" to "😁",
            "laughing" to "😆",
            "wink" to "😉",
            "sob" to "😭",
            "cry" to "😢",
            "heart" to "❤️",
            "broken_heart" to "💔",
            "fire" to "🔥",
            "star" to "⭐",
            "sparkles" to "✨",
            "ok" to "🆗",
            "x" to "❌",
            "heavy_check_mark" to "✔️",
            "warning" to "⚠️",
            "rocket" to "🚀"
        )
        // Hyphen is last to avoid escaping in regex character class.
        val emojiShortcut = Regex(":([a-z0-9_+-]+):")

        // Superscript/subscript via HTML tags (HtmlPlugin supports <sup>/<sub>).
        val sup = Regex("(?<!\\^)\\^([^\\^\\n]+)\\^(?!\\^)")
        // Avoid clashing with strikethrough (~~text~~) by matching only single tildes.
        val sub = Regex("(?<!~)~([^~\\n]+)~(?!~)")

        // Inline math: convert $...$ -> $$...$$ (only when it looks like a formula)
        val inlineDollar = Regex("(?<!\\$)\\$(?!\\$)([^\\n$]+)\\$(?!\\$)")
        fun looksLikeMath(s: String): Boolean {
            val t = s.trim()
            if (t.isEmpty()) return false
            // Heuristics: must include typical math tokens to avoid currency.
            return t.any { it in listOf('^', '_', '=', '+', '-', '*', '/', '{', '}', '\\') }
        }

        val out = ArrayList<String>(body.size + 16)
        var i = 0
        while (i < body.size) {
            val line = body[i]
            val trimmed = line.trim()

            // TOC marker line
            if (hasTocMarker && trimmed.equals("[TOC]", ignoreCase = true)) {
                if (headings.isEmpty()) {
                    out.add("(目录为空：没有找到标题)")
                } else {
                    out.add("目录：")
                    for ((level, title) in headings) {
                        val indent = "  ".repeat((level - 1).coerceAtLeast(0))
                        out.add("${indent}- $title")
                    }
                }
                i++
                continue
            }

            // Definition list transform: Term + one or more ': ' definitions
            if (trimmed.isNotBlank() && i + 1 < body.size) {
                val next = body[i + 1]
                val nextTrim = next.trimStart()
                if (nextTrim.startsWith(": ") || nextTrim.startsWith(":\t") || nextTrim.startsWith(":\u3000")) {
                    out.add("**${trimmed}**")
                    var j = i + 1
                    while (j < body.size) {
                        val n = body[j].trimStart()
                        if (!(n.startsWith(": ") || n.startsWith(":\t") || n.startsWith(":\u3000"))) break
                        out.add("- " + n.drop(1).trimStart())
                        j++
                    }
                    i = j
                    continue
                }
            }

            var s = line

            // Emoji shortcuts
            if (s.indexOf(':') >= 0) {
                s = emojiShortcut.replace(s) { m0 ->
                    val key = m0.groupValues[1]
                    emojiMap[key] ?: m0.value
                }
            }

            // Footnote references: [^id] -> <sup>[n]</sup>
            if (s.contains("[^")) {
                s = footnoteRef.replace(s) { m0 ->
                    val id = m0.groupValues[1].trim()
                    val n = noteNumber(id)
                    "<sup>[$n]</sup>"
                }
            }

            // Sup/sub
            if (s.indexOf('^') >= 0) {
                s = sup.replace(s) { m0 -> "<sup>${m0.groupValues[1]}</sup>" }
            }
            if (s.indexOf('~') >= 0) {
                s = sub.replace(s) { m0 -> "<sub>${m0.groupValues[1]}</sub>" }
            }

            // Inline math
            if (s.indexOf('$') >= 0) {
                s = inlineDollar.replace(s) { m0 ->
                    val inner = m0.groupValues[1]
                    if (looksLikeMath(inner)) "$$${inner}$$" else m0.value
                }
            }

            out.add(s)
            i++
        }

        // Append footnotes section (only those referenced or defined).
        if (footnoteDefs.isNotEmpty() && (footnoteOrder.isNotEmpty() || footnoteDefs.isNotEmpty())) {
            out.add("")
            out.add("---")
            out.add("脚注：")

            // Prefer referenced order; include unreferenced defs after.
            val emitted = HashSet<String>()
            for ((id, n) in footnoteOrder) {
                val text = footnoteDefs[id]?.toString()?.trim().orEmpty()
                if (text.isBlank()) continue
                out.add("$n. $text")
                emitted.add(id)
            }
            for ((id, sb) in footnoteDefs) {
                if (emitted.contains(id)) continue
                val text = sb.toString().trim()
                if (text.isBlank()) continue
                val n = noteNumber(id)
                out.add("$n. $text")
            }
        }

        return out.joinToString("\n")
    }
}
