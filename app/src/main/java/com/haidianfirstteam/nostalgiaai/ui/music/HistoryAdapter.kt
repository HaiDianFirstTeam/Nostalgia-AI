package com.haidianfirstteam.nostalgiaai.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemMusicHistoryBinding

class HistoryAdapter(
    private val onClick: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = ArrayList<String>()

    fun submit(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMusicHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemMusicHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(q: String) {
            b.tvQuery.text = q
            b.root.setOnClickListener { onClick(q) }
            b.btnDelete.setOnClickListener { onDelete(q) }
        }
    }
}
