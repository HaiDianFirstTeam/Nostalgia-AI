package com.haidianfirstteam.nostalgiaai.ui.translate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haidianfirstteam.nostalgiaai.databinding.ItemTranslateChatBinding

class TranslateChatAdapter : RecyclerView.Adapter<TranslateChatAdapter.VH>() {

    private var input: String = ""
    private var output: String = ""

    fun submit(input: String, output: String) {
        this.input = input
        this.output = output
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTranslateChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(input, output)
    }

    inner class VH(private val b: ItemTranslateChatBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(input: String, output: String) {
            b.tvInput.text = input
            b.tvOutput.text = output
        }
    }
}
