package com.haidianfirstteam.nostalgiaai.ui.translate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemTranslateHistoryCardBinding

data class TranslateConversationCard(
    val id: Long,
    val updatedAt: Long,
    val title: String,
)

class TranslateConversationHistoryAdapter(
    private val onClick: (TranslateConversationCard) -> Unit,
    private val onDelete: (TranslateConversationCard) -> Unit,
) : RecyclerView.Adapter<TranslateConversationHistoryAdapter.VH>() {

    private val items = ArrayList<TranslateConversationCard>()

    fun submit(list: List<TranslateConversationCard>) {
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
        fun bind(pos: Int, item: TranslateConversationCard) {
            b.tvTitle.text = item.title
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
}
