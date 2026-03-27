package com.haidianfirstteam.nostalgiaai.ui.rss

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemRssSourceBinding

class RssSourceAdapter(
    private val onClick: (RssSource) -> Unit,
    private val onMenu: (RssSource) -> Unit,
) : RecyclerView.Adapter<RssSourceAdapter.VH>() {

    private val items = ArrayList<RssSource>()

    fun submit(list: List<RssSource>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemRssSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemRssSourceBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: RssSource) {
            b.tvTitle.text = s.nick.ifBlank { s.title.ifBlank { s.url } }
            val sub = if (s.lastError.isNullOrBlank()) s.url else ("错误：" + s.lastError)
            b.tvSub.text = sub
            b.btnMore.setOnClickListener { onMenu(s) }
            b.root.setOnClickListener { onClick(s) }
        }
    }
}
