package com.haidianfirstteam.nostalgiaai.ui.translate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemTranslateChatBinding

/**
 * Multi-turn conversation adapter for memory mode.
 */
class TranslateConversationAdapter : RecyclerView.Adapter<TranslateConversationAdapter.VH>() {

    private val turns = ArrayList<TranslateHistoryItem>()

    fun submit(list: List<TranslateHistoryItem>) {
        turns.clear()
        turns.addAll(list)
        notifyDataSetChanged()
    }

    fun append(item: TranslateHistoryItem) {
        turns.add(item)
        notifyItemInserted(turns.size - 1)
    }

    fun updateOutput(id: Long, output: String) {
        val idx = turns.indexOfFirst { it.id == id }
        if (idx < 0) return
        val old = turns[idx]
        turns[idx] = old.copy(output = output)
        notifyItemChanged(idx)
    }

    fun snapshot(): List<TranslateHistoryItem> = turns.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTranslateChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = turns.size.coerceAtLeast(1)

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (turns.isEmpty()) {
            holder.bind("", "")
        } else {
            val t = turns[position]
            holder.bind(t.input, t.output)
        }
    }

    inner class VH(private val b: ItemTranslateChatBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(input: String, output: String) {
            b.tvInput.text = input
            b.tvOutput.text = output
        }
    }
}
