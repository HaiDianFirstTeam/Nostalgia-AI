package com.haidianfirstteam.nostalgiaai.ui.drawer

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.data.entities.ConversationEntity
import com.haidianfirstteam.nostalgiaai.databinding.ItemConversationBinding

class ConversationAdapter(
    private val onClick: (ConversationEntity) -> Unit,
    private val onLongClick: (ConversationEntity) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    private val items = ArrayList<ConversationEntity>()

    fun submit(list: List<ConversationEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemConversationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ConversationEntity) {
            b.tvTitle.text = item.title
            b.tvTime.text = DateFormat.format("yyyy-MM-dd HH:mm", item.updatedAt)
            b.root.setOnClickListener { onClick(item) }
            b.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }
}
