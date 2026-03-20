package com.haidianfirstteam.nostalgiaai.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemMusicTrackBinding
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack

class TrackAdapter(
    private val onPlay: (MusicTrack) -> Unit,
    private val onDownload: (MusicTrack) -> Unit,
    private val onMore: (MusicTrack) -> Unit,
) : RecyclerView.Adapter<TrackAdapter.VH>() {

    private val items = ArrayList<MusicTrack>()

    fun submit(list: List<MusicTrack>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMusicTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemMusicTrackBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(t: MusicTrack) {
            b.tvName.text = t.name
            b.tvArtist.text = t.artists.joinToString("/")
            b.btnPlay.setOnClickListener { onPlay(t) }
            b.btnDownload.setOnClickListener { onDownload(t) }
            b.btnMore.setOnClickListener { onMore(t) }
        }
    }
}
