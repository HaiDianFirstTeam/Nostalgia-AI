package com.haidianfirstteam.nostalgiaai.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemMusicAlbumBinding

class AlbumAdapter(
    private val onOpen: (MusicAlbumUi) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.VH>() {

    private val items = ArrayList<MusicAlbumUi>()

    fun submit(list: List<MusicAlbumUi>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMusicAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemMusicAlbumBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(a: MusicAlbumUi) {
            b.tvAlbum.text = a.albumName
            b.tvMeta.text = "${a.artistsSummary} · ${a.trackCount}首 · ${a.source}"
            b.btnOpen.setOnClickListener { onOpen(a) }
            b.root.setOnClickListener { onOpen(a) }
        }
    }
}
