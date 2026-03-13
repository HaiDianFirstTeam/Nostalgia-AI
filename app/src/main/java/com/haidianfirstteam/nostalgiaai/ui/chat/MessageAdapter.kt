package com.haidianfirstteam.nostalgiaai.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
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
    private val onEditAssistant: (MessageUi, String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = ArrayList<MessageUi>()

    fun submit(list: List<MessageUi>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
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
            // Markdown render
            markwon.setMarkdown(b.tvContent, item.content)
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
