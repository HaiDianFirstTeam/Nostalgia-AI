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
        fun bind(item: RssItem) {
            b.tvTitle.text = item.title
            b.tvMeta.text = item.author
            b.tvSummary.text = item.summary

            b.imgCover.visibility = if (item.imageUrl.isNullOrBlank()) View.GONE else View.VISIBLE
            if (!item.imageUrl.isNullOrBlank()) {
                ImageLoader.loadInto(item.imageUrl, b.imgCover)
            } else {
                b.imgCover.setImageDrawable(null)
            }

            // Avoid shadowing: View.OnClickListener lambda uses `it` as View.
            b.root.setOnClickListener { onClick(item) }
        }
    }
}
