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

class MessageAdapter(
    private val markwon: Markwon,
    private val onEditResendUser: (MessageUi, String) -> Unit,
    private val onRetry: (MessageUi) -> Unit,
    private val onEditAssistant: (MessageUi, String) -> Unit,
    private val onDeletePair: (MessageUi) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    private val items = ArrayList<MessageUi>()

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
        // If only last item content is changing (streaming), update that item only.
        if (old.size == items.size) {
            val lastIdx = items.size - 1
            var samePrefix = true
            for (i in 0 until lastIdx) {
                if (old[i].id != items[i].id) { samePrefix = false; break }
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

            // Markdown render
            if (item.content.isBlank()) {
                // Avoid showing an ever-growing empty bubble during streaming
                b.tvContent.visibility = View.GONE
                b.tvContent.text = ""
            } else {
                b.tvContent.visibility = View.VISIBLE
                markwon.setMarkdown(b.tvContent, item.content)
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
}
