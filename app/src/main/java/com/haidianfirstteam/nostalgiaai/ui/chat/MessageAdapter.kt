package com.haidianfirstteam.nostalgiaai.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.LinearLayout
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.databinding.ItemMessageAiBinding
import com.haidianfirstteam.nostalgiaai.databinding.ItemMessageUserBinding
import io.noties.markwon.Markwon
import android.text.SpannableStringBuilder


class MessageAdapter(
    private val markwon: Markwon,
    private val onEditResendUser: (MessageUi, String) -> Unit,
    private val onRetry: (MessageUi) -> Unit,
    private val onEditAssistant: (MessageUi, String) -> Unit,
    private val onDeletePair: (MessageUi) -> Unit,
    private val onSwitchVariant: (MessageUi, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    private val items = ArrayList<MessageUi>()

    private val thinkingExpanded = HashMap<Long, Boolean>()
    private val lastMarkdownRenderAt = HashMap<Long, Long>()
    private val lastMarkdownRenderedHash = HashMap<Long, Int>()
    private val lastMarkdownRenderedLen = HashMap<Long, Int>()

    // When streaming, rendering markdown on every tiny update can cause heavy relayout/jitter.
    // We render plain text for the current streaming assistant bubble, and apply markdown after done.
    private var streamingMessageId: Long? = null

    private data class StreamingMdState(
        var fullText: String = "",
        var rendered: SpannableStringBuilder = SpannableStringBuilder(),
        var pending: StringBuilder = StringBuilder()
    )

    private val streamingStates = HashMap<Long, StreamingMdState>()

    fun setStreamingMessageId(id: Long?) {
        if (streamingMessageId != null && streamingMessageId != id) {
            // Drop previous streaming incremental state to avoid leaks.
            streamingStates.remove(streamingMessageId!!)
        }
        streamingMessageId = id
    }

    fun submit(list: List<MessageUi>) {
        // Minimal diff to reduce jitter during streaming.
        // Assumes stable ordering by createdAt/id.
        val old = items.toList()
        items.clear()
        items.addAll(list)

        if (old.isEmpty()) {
            notifyDataSetChanged()
            return
        }

        // Fast path: old is a prefix of new (append)
        run {
            val min = kotlin.math.min(old.size, items.size)
            var prefixOk = true
            for (i in 0 until min) {
                if (old[i].id != items[i].id) {
                    prefixOk = false
                    break
                }
            }
            if (prefixOk) {
                when {
                    items.size > old.size -> {
                        notifyItemRangeInserted(old.size, items.size - old.size)
                        return
                    }
                    items.size < old.size -> {
                        notifyItemRangeRemoved(items.size, old.size - items.size)
                        return
                    }
                }
            }
        }

        // If only last item content is changing (streaming), update that item only.
        if (old.size == items.size && items.isNotEmpty()) {
            val lastIdx = items.size - 1
            var samePrefix = true
            for (i in 0 until lastIdx) {
                if (old[i].id != items[i].id) {
                    samePrefix = false
                    break
                }
            }
            if (samePrefix && old[lastIdx].id == items[lastIdx].id) {
                notifyItemChanged(lastIdx)
                return
            }
        }

        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return items[position].id
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].role == "user") 1 else 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            VHUser(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            VHAi(ItemMessageAiBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is VHUser -> holder.bind(item)
            is VHAi -> holder.bind(item)
        }
    }

    private fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("message", text))
    }

    private fun showEditDialog(context: Context, title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(context)
        input.setText(initial)
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定") { _, _ -> onOk(input.text?.toString() ?: "") }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class VHUser(private val b: ItemMessageUserBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: MessageUi) {
            b.tvContent.text = item.content

            if (item.branchEnabled && item.branchTotal > 1) {
                b.branchBar.visibility = View.VISIBLE
                b.tvBranchInfo.text = "${item.branchIndex}/${item.branchTotal}"
                b.btnBranchPrev.setOnClickListener { onSwitchVariant(item, -1) }
                b.btnBranchNext.setOnClickListener { onSwitchVariant(item, +1) }
            } else {
                b.branchBar.visibility = View.GONE
            }

            b.btnDelete.setOnClickListener { onDeletePair(item) }
            b.btnCopy.setOnClickListener { copy(b.root.context, item.content) }
            b.btnRetry.setOnClickListener { onRetry(item) }
            b.btnEditResend.setOnClickListener {
                showEditDialog(b.root.context, "编辑并重发", item.content) { newText ->
                    onEditResendUser(item, newText)
                }
            }
        }
    }

    inner class VHAi(private val b: ItemMessageAiBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: MessageUi) {
            // Web links chips (from Tavily)
            val ctx = b.root.context
            if (item.webLinks.isNotEmpty()) {
                b.chipsScroll.visibility = View.VISIBLE
                b.btnExpandChips.visibility = if (item.webLinks.size > 5) View.VISIBLE else View.GONE
                b.btnExpandChips.rotation = 0f
                var expanded = false

                fun makeChip(link: WebLinkUi): View {
                    val tv = android.widget.TextView(ctx)
                    tv.text = link.title
                    tv.setSingleLine(true)
                    tv.ellipsize = android.text.TextUtils.TruncateAt.END
                    tv.setPadding(24, 10, 24, 10)
                    tv.setBackgroundResource(com.haidianfirstteam.nostalgiaai.R.drawable.bg_chip)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 0, 12, 12)
                    tv.layoutParams = lp
                    tv.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                            ctx.startActivity(intent)
                        } catch (_: Exception) {
                            // ignore
                        }
                    }
                    return tv
                }

                fun renderChips() {
                    b.chipsContainer.removeAllViews()
                    val list = if (!expanded && item.webLinks.size > 5) item.webLinks.take(5) else item.webLinks
                    if (!expanded) {
                        b.chipsContainer.orientation = LinearLayout.HORIZONTAL
                        // single row with horizontal scroll
                        list.forEach { link ->
                            b.chipsContainer.addView(makeChip(link))
                        }
                        b.chipsScroll.isHorizontalScrollBarEnabled = false
                    } else {
                        b.chipsContainer.orientation = LinearLayout.VERTICAL
                        // multi-row: render as vertical rows of chips
                        // Simple heuristic: 3 chips per row.
                        val rowSize = 3
                        var row: LinearLayout? = null
                        list.forEachIndexed { idx, link ->
                            if (idx % rowSize == 0) {
                                row = LinearLayout(ctx).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                }
                                b.chipsContainer.addView(row)
                            }
                            row?.addView(makeChip(link))
                        }
                        b.chipsScroll.isHorizontalScrollBarEnabled = false
                    }
                }

                renderChips()
                b.btnExpandChips.setOnClickListener {
                    expanded = !expanded
                    b.btnExpandChips.rotation = if (expanded) 180f else 0f
                    renderChips()
                }
            } else {
                b.chipsScroll.visibility = View.GONE
                b.btnExpandChips.visibility = View.GONE
                b.chipsContainer.removeAllViews()
            }

            // Mermaid extraction (rendered separately via WebView)
            val parsed = extractMermaidBlocks(item.content)
            val mermaids = parsed.diagrams
            renderMermaids(b, mermaids)

            // Thinking panel
            if (item.thinking.isNotBlank()) {
                b.thinkingBar.visibility = View.VISIBLE
                val expanded = thinkingExpanded[item.id] == true
                b.tvThinking.text = item.thinking
                b.tvThinking.visibility = if (expanded) View.VISIBLE else View.GONE
                b.btnThinkingToggle.rotation = if (expanded) 180f else 0f
                b.btnThinkingToggle.setOnClickListener {
                    thinkingExpanded[item.id] = !(thinkingExpanded[item.id] == true)
                    notifyItemChanged(bindingAdapterPosition)
                }
            } else {
                b.thinkingBar.visibility = View.GONE
                b.tvThinking.text = ""
                b.tvThinking.visibility = View.GONE
            }

            // Main content: text with display math ($$...$$ / \[...\]) inline via KaTeX
            val contentForMarkdown = parsed.text
            val sid = streamingMessageId
            if (sid != null && item.id == sid) {
                // Streaming: show tvContent with incremental rendering
                b.contentContainer.visibility = View.GONE
                b.contentContainer.removeAllViews()
                b.mermaidContainer.visibility = View.GONE
                b.mermaidContainer.removeAllViews()

                if (contentForMarkdown.isBlank()) {
                    b.tvContent.visibility = View.GONE
                    b.tvContent.text = ""
                } else {
                    b.tvContent.visibility = View.VISIBLE

                    val st = streamingStates.getOrPut(item.id) { StreamingMdState() }
                    val prev = st.fullText
                    val next = contentForMarkdown
                    if (prev.isNotEmpty() && !next.startsWith(prev)) {
                        st.fullText = ""
                        st.rendered = SpannableStringBuilder()
                        st.pending = StringBuilder()
                    }
                    val appended = if (next.length >= prev.length) next.substring(prev.length) else next
                    st.fullText = next
                    st.pending.append(appended)

                    while (true) {
                        val cut = findSafeChunkEnd(st.pending)
                        if (cut <= 0) break
                        val chunk = st.pending.substring(0, cut)
                        val sp = markwon.toMarkdown(chunk)
                        st.rendered.append(sp)
                        st.pending.delete(0, cut)
                    }

                    val combined = SpannableStringBuilder()
                    combined.append(st.rendered)
                    combined.append(st.pending.toString())
                    b.tvContent.text = combined
                }
            } else {
                // Non-streaming: build segmented content with inline math
                b.tvContent.visibility = View.GONE
                b.tvContent.text = ""

                if (contentForMarkdown.isBlank()) {
                    b.contentContainer.visibility = View.GONE
                } else {
                    val segments = segmentContent(contentForMarkdown)
                    renderContent(b, segments)
                }
            }

            if (item.branchEnabled && item.branchTotal > 1) {
                b.branchBar.visibility = View.VISIBLE
                b.tvBranchInfo.text = "${item.branchIndex}/${item.branchTotal}"
                b.btnBranchPrev.setOnClickListener { onSwitchVariant(item, -1) }
                b.btnBranchNext.setOnClickListener { onSwitchVariant(item, +1) }
            } else {
                b.branchBar.visibility = View.GONE
            }

            b.btnDelete.setOnClickListener { onDeletePair(item) }
            b.btnCopy.setOnClickListener { copy(b.root.context, item.content) }
            b.btnRetry.setOnClickListener { onRetry(item) }
            b.btnEdit.setOnClickListener {
                showEditDialog(b.root.context, "编辑", item.content) { newText ->
                    onEditAssistant(item, newText)
                }
            }
        }
    }

    private data class MermaidParsed(
        val text: String,
        val diagrams: List<String>
    )

    private sealed class MessageSegment {
        data class Text(val content: String) : MessageSegment()
        data class Math(val latex: String) : MessageSegment()
    }

    private fun extractMermaidBlocks(markdown: String): MermaidParsed {
        if (markdown.isBlank()) return MermaidParsed(markdown, emptyList())
        val lines = markdown.split('\n')
        val out = ArrayList<String>(lines.size)
        val diagrams = ArrayList<String>()

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val t = raw.trim()
            val fence = when {
                t.startsWith("```") -> "```"
                t.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (fence != null) {
                val after = t.removePrefix(fence).trimStart()
                val lang = after.takeWhile { !it.isWhitespace() }
                if (lang.equals("mermaid", ignoreCase = true)) {
                    val sb = StringBuilder()
                    i++
                    while (i < lines.size) {
                        val line = lines[i]
                        if (line.trimStart().startsWith(fence)) break
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append(line)
                        i++
                    }
                    diagrams.add(sb.toString().trim())

                    // skip closing fence
                    while (i < lines.size && !lines[i].trimStart().startsWith(fence)) i++
                    if (i < lines.size) i++

                    // keep a small separator so surrounding text keeps spacing
                    out.add("")
                    continue
                }
            }

            out.add(raw)
            i++
        }

        return MermaidParsed(out.joinToString("\n").trim(), diagrams)
    }

    /**
     * Split markdown text into a list of [MessageSegment]s at display math boundaries
     * ($$...$$, \[...\]). Regular text becomes [MessageSegment.Text] and math blocks
     * become [MessageSegment.Math], preserving original order.
     */
    private fun segmentContent(markdown: String): List<MessageSegment> {
        if (markdown.isBlank()) return emptyList()
        val regex = Regex("""\$\$[\s\S]*?\$\$|\\\[[\s\S]*?\\\]""")
        val segments = ArrayList<MessageSegment>()
        var lastEnd = 0
        for (match in regex.findAll(markdown)) {
            val before = markdown.substring(lastEnd, match.range.first)
            if (before.isNotBlank()) {
                segments.add(MessageSegment.Text(before))
            }
            val content = match.value
            val latex = when {
                content.startsWith("$$") -> content.substring(2, content.length - 2).trim()
                content.startsWith("\\[") -> content.substring(2, content.length - 2).trim()
                else -> content.trim()
            }
            segments.add(MessageSegment.Math(latex))
            lastEnd = match.range.last + 1
        }
        val after = markdown.substring(lastEnd)
        if (after.isNotBlank()) {
            segments.add(MessageSegment.Text(after))
        }
        return segments
    }

    private fun renderContent(b: ItemMessageAiBinding, segments: List<MessageSegment>) {
        val container = b.contentContainer
        container.removeAllViews()

        if (segments.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE

        // Resolve theme text color for Text segment TextViews
        val ta = b.root.context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val textColor = ta.getColor(0, 0xFF000000.toInt())
        ta.recycle()

        for (segment in segments) {
            when (segment) {
                is MessageSegment.Text -> {
                    val tv = android.widget.TextView(b.root.context)
                    tv.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    tv.setTextColor(textColor)
                    markwon.setMarkdown(tv, segment.content)
                    container.addView(tv)
                }
                is MessageSegment.Math -> {
                    val mv = MathWebView(b.root.context)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = (4f * b.root.resources.displayMetrics.density).toInt()
                    mv.layoutParams = lp
                    mv.setLatex(segment.latex)
                    container.addView(mv)
                }
            }
        }
    }

    private fun renderMermaids(b: ItemMessageAiBinding, diagrams: List<String>) {
        if (diagrams.isEmpty()) {
            b.mermaidContainer.visibility = View.GONE
            b.mermaidContainer.removeAllViews()
            return
        }
        b.mermaidContainer.visibility = View.VISIBLE

        // Reuse existing MermaidView instances when possible.
        while (b.mermaidContainer.childCount > diagrams.size) {
            b.mermaidContainer.removeViewAt(b.mermaidContainer.childCount - 1)
        }
        while (b.mermaidContainer.childCount < diagrams.size) {
            val mv = MermaidView(b.root.context)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4f * b.root.resources.displayMetrics.density).toInt()
            mv.layoutParams = lp
            b.mermaidContainer.addView(mv)
        }

        for (idx in diagrams.indices) {
            val v = b.mermaidContainer.getChildAt(idx)
            if (v is MermaidView) {
                v.setDiagram(diagrams[idx])
            }
        }
    }

    private fun containsMarkdownTokens(s: String): Boolean {
        if (s.isBlank()) return false
        // Keep it cheap: we only need a heuristic.
        // - Backticks: inline/code fences
        // - Asterisks/underscores: emphasis
        // - Brackets/parentheses: links/images
        // - $ or \\: math
        // - '==': highlight
        // - '<': html tags
        return s.indexOf('`') >= 0 ||
            s.indexOf('*') >= 0 ||
            s.indexOf('_') >= 0 ||
            s.indexOf('[') >= 0 ||
            s.indexOf(']') >= 0 ||
            s.indexOf('(') >= 0 ||
            s.indexOf(')') >= 0 ||
            s.indexOf('!') >= 0 ||
            s.indexOf('$') >= 0 ||
            s.indexOf('\\') >= 0 ||
            s.indexOf('<') >= 0 ||
            s.contains("==")
    }

    /**
     * Find a safe cut position (in characters) for incremental markdown rendering.
     * Returns 0 if no safe boundary.
     */
    private fun findSafeChunkEnd(pending: StringBuilder): Int {
        if (pending.isEmpty()) return 0
        val text = pending.toString()
        if (!text.contains('\n')) return 0

        var inFence: String? = null
        var latexBlock = 0
        var casesBlock = 0
        var inDollarBlock = false
        var lastSafe = -1

        var lineStart = 0
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\n') {
                val line = text.substring(lineStart, i + 1)
                val t = line.trimStart()

                // Fence toggle (``` or ~~~)
                val marker = when {
                    t.startsWith("```") -> "```"
                    t.startsWith("~~~") -> "~~~"
                    else -> null
                }
                if (marker != null) {
                    if (inFence == null) inFence = marker else if (inFence == marker) inFence = null
                }

                if (inFence == null) {
                    // Track \[ ... \] (can start/end in same line)
                    // Count occurrences to be robust.
                    latexBlock += countOccurrences(line, "\\[")
                    latexBlock -= countOccurrences(line, "\\]")
                    if (latexBlock < 0) latexBlock = 0

                    // Track \begin{cases} ... \end{cases}
                    casesBlock += countOccurrences(line, "\\begin{cases}")
                    casesBlock -= countOccurrences(line, "\\end{cases}")
                    if (casesBlock < 0) casesBlock = 0

                    // Track $$...$$ across lines (each occurrence toggles)
                    val dollarCount = countOccurrences(line, "$$")
                    if (dollarCount % 2 == 1) inDollarBlock = !inDollarBlock
                }

                if (inFence == null && latexBlock == 0 && casesBlock == 0 && !inDollarBlock) {
                    lastSafe = i + 1
                }

                lineStart = i + 1
            }
            i++
        }

        return if (lastSafe > 0) lastSafe else 0
    }

    private fun countOccurrences(hay: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var idx = 0
        var count = 0
        while (true) {
            val p = hay.indexOf(needle, idx)
            if (p < 0) break
            count++
            idx = p + needle.length
        }
        return count
    }
}
