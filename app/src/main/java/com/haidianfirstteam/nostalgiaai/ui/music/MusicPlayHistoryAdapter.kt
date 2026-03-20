package com.haidianfirstteam.nostalgiaai.ui.music

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemMusicPlayHistoryBinding
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicPlayHistoryItem

class MusicPlayHistoryAdapter(
    private val onDelete: (MusicPlayHistoryItem) -> Unit,
) : RecyclerView.Adapter<MusicPlayHistoryAdapter.VH>() {

    private val items = ArrayList<MusicPlayHistoryItem>()

    fun submit(list: List<MusicPlayHistoryItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMusicPlayHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemMusicPlayHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: MusicPlayHistoryItem) {
            b.tvName.text = item.track.name
            b.tvArtist.text = item.track.artists.joinToString("/")
            b.tvTime.text = DateFormat.format("yyyy-MM-dd HH:mm", item.playedAt)
            b.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
