package com.haidianfirstteam.nostalgiaai.ui.rss

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemRssCardBinding

class RssItemAdapter(
    private val onClick: (RssItem) -> Unit
) : RecyclerView.Adapter<RssItemAdapter.VH>() {

    private val items = ArrayList<RssItem>()

    fun submit(list: List<RssItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemRssCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemRssCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(it: RssItem) {
            b.tvTitle.text = it.title
            b.tvMeta.text = it.author
            b.tvSummary.text = it.summary

            b.imgCover.visibility = if (it.imageUrl.isNullOrBlank()) View.GONE else View.VISIBLE
            if (!it.imageUrl.isNullOrBlank()) {
                ImageLoader.loadInto(it.imageUrl, b.imgCover)
            } else {
                b.imgCover.setImageDrawable(null)
            }

            b.root.setOnClickListener { onClick(it) }
        }
    }
}
