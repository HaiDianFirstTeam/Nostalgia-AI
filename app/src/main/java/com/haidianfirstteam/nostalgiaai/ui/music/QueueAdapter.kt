package com.haidianfirstteam.nostalgiaai.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemMusicQueueBinding
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack

class QueueAdapter(
    private val onClick: (Int) -> Unit,
    private val onRemove: (Int) -> Unit,
) : RecyclerView.Adapter<QueueAdapter.VH>() {

    private val items = ArrayList<MusicTrack>()

    fun submit(list: List<MusicTrack>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMusicQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position, items[position])
    }

    inner class VH(private val b: ItemMusicQueueBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(pos: Int, t: MusicTrack) {
            b.tvName.text = t.name
            b.tvArtist.text = t.artists.joinToString("/")
            b.root.setOnClickListener { onClick(pos) }
            b.btnRemove.setOnClickListener { onRemove(pos) }
        }
    }
}
