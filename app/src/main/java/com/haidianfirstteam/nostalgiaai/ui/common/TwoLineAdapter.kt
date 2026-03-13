package com.haidianfirstteam.nostalgiaai.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemTwoLineBinding

data class TwoLineItem(
    val id: Long,
    val title: String,
    val subtitle: String
)

class TwoLineAdapter(
    private val onClick: (TwoLineItem) -> Unit,
    private val onLongClick: (TwoLineItem) -> Unit
) : RecyclerView.Adapter<TwoLineAdapter.VH>() {

    private val items = ArrayList<TwoLineItem>()

    fun submit(list: List<TwoLineItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTwoLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemTwoLineBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: TwoLineItem) {
            b.title.text = item.title
            b.subtitle.text = item.subtitle
            b.root.setOnClickListener { onClick(item) }
            b.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }
}
