package com.haidianfirstteam.nostalgiaai.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.graphics.BlurMaskFilter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.haidianfirstteam.nostalgiaai.databinding.ItemLyricLineBinding
import com.haidianfirstteam.nostalgiaai.ui.music.lyrics.LrcLine

class LyricsAdapter(
    private val onClick: (pos: Int, line: LrcLine) -> Unit,
) : RecyclerView.Adapter<LyricsAdapter.VH>() {
    private val items = ArrayList<LrcLine>()
    private var activeIndex: Int = -1

    fun submit(list: List<LrcLine>) {
        items.clear()
        items.addAll(list)
        activeIndex = -1
        notifyDataSetChanged()
    }

    fun setActiveTimeMs(timeMs: Int): Int {
        if (items.isEmpty()) return -1
        // Find last line with time <= current
        var lo = 0
        var hi = items.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (items[mid].timeMs <= timeMs.toLong()) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (ans != activeIndex) {
            val prev = activeIndex
            activeIndex = ans
            if (prev >= 0) notifyItemChanged(prev)
            notifyItemChanged(activeIndex)
        }
        return activeIndex
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemLyricLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position, items[position])
    }

    inner class VH(private val b: ItemLyricLineBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(pos: Int, line: LrcLine) {
            b.tvLine.text = line.text

            val trans = line.trans?.trim().orEmpty()
            if (trans.isNotBlank()) {
                b.tvTrans.visibility = View.VISIBLE
                b.tvTrans.text = trans
            } else {
                b.tvTrans.visibility = View.GONE
                b.tvTrans.text = ""
            }

            val dist = if (activeIndex < 0) 99 else kotlin.math.abs(pos - activeIndex)
            val isActive = dist == 0
            val alpha = when (dist) {
                0 -> 1.0f
                1 -> 0.88f
                2 -> 0.72f
                3 -> 0.60f
                else -> 0.45f
            }
            b.tvLine.alpha = alpha
            b.tvTrans.alpha = alpha * 0.82f

            // Make far-away lines look "blurred" (frosted) to reduce visual noise.
            // NOTE: Must reset on every bind because RecyclerView recycles views.
            val blurRadius = when {
                dist >= 4 -> 6f
                dist == 3 -> 3f
                else -> 0f
            }
            if (blurRadius > 0f) {
                b.tvLine.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                b.tvTrans.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                val f = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                b.tvLine.paint.maskFilter = f
                b.tvTrans.paint.maskFilter = f
            } else {
                b.tvLine.paint.maskFilter = null
                b.tvTrans.paint.maskFilter = null
                // Keep default layer type.
                b.tvLine.setLayerType(View.LAYER_TYPE_NONE, null)
                b.tvTrans.setLayerType(View.LAYER_TYPE_NONE, null)
            }

            b.tvLine.textSize = when (dist) {
                0 -> 20f
                1 -> 17f
                else -> 15f
            }

            b.tvTrans.textSize = when (dist) {
                0 -> 14f
                1 -> 13f
                else -> 12f
            }

            val c = ContextCompat.getColor(
                b.root.context,
                if (isActive) com.haidianfirstteam.nostalgiaai.R.color.lyric_active
                else com.haidianfirstteam.nostalgiaai.R.color.lyric_normal
            )
            b.tvLine.setTextColor(c)
            b.tvTrans.setTextColor(c)

            b.root.setOnClickListener {
                onClick(pos, line)
            }
        }
    }
}
