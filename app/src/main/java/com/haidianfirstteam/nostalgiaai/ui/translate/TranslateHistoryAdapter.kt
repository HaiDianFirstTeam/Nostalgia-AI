package com.haidianfirstteam.nostalgiaai.ui.translate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemTranslateHistoryCardBinding

class TranslateHistoryAdapter(
    private val onClick: (TranslateHistoryItem) -> Unit,
    private val onDelete: (TranslateHistoryItem) -> Unit,
) : RecyclerView.Adapter<TranslateHistoryAdapter.VH>() {

    private val items = ArrayList<TranslateHistoryItem>()

    fun submit(list: List<TranslateHistoryItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTranslateHistoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position, items[position])
    }

    inner class VH(private val b: ItemTranslateHistoryCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(pos: Int, item: TranslateHistoryItem) {
            b.tvTitle.text = makeCardTitle(item.input)
            // Alternate colors by column.
            val isLeft = (pos % 2 == 0)
            val color = if (isLeft) {
                com.haidianfirstteam.nostalgiaai.R.color.translate_card_left
            } else {
                com.haidianfirstteam.nostalgiaai.R.color.translate_card_right
            }
            b.card.setCardBackgroundColor(b.root.context.resources.getColor(color))
            b.root.setOnClickListener { onClick(item) }
            b.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    private fun makeCardTitle(input: String): String {
        val t = input.trim().replace("\n", " ")
        if (t.isBlank()) return "(空)"
        // Determine by whitespace
        val parts = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        val head = if (parts.size <= 1) {
            t.take(18)
        } else {
            parts.take(2).joinToString(" ").take(18)
        }
        return if (head.length < t.length) head + "..." else head
    }
}
