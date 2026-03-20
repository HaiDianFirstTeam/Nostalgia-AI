package com.haidianfirstteam.nostalgiaai.ui.music

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.databinding.SheetMusicQueueBinding
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi2Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicSourceType
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MusicQueueSheet {
    fun show(context: Context) {
        val dialog = BottomSheetDialog(context)
        val b = SheetMusicQueueBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(b.root)

        val app = context.applicationContext as? NostalgiaApp
        val store = app?.let { MusicStore(it.db) }
        val api1 = MusicApi1Client()
        val api2 = MusicApi2Client()

        val adapter = QueueAdapter(
            onClick = { idx ->
                val q = MusicPlayerManager.getQueue()
                val t = q.getOrNull(idx) ?: return@QueueAdapter
                if (store == null) return@QueueAdapter
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val url = withContext(Dispatchers.IO) {
                            val s = store.getSettings()
                            // Choose resolver by track source when possible.
                            if (t.source == "wyapi" || s.source == MusicSourceType.API2_WYAPI) {
                                val level = s.quality.streamLevel ?: "standard"
                                api2.getPlayUrl(t.id, level).url
                            } else {
                                val br = s.quality.streamBr ?: 320
                                api1.getPlayUrl(source = t.source.ifBlank { "netease" }, trackId = t.id, br = br).url
                            }
                        }
                        // keep queue, just play selected
                        MusicPlayerManager.setQueue(q, idx)
                        MusicPlayerManager.playAt(context.applicationContext, idx, url)
                        dialog.dismiss()
                    } catch (e: Throwable) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle("切歌失败")
                            .setMessage(e.message ?: "未知错误")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            },
            onRemove = { idx ->
                MusicPlayerManager.removeQueueItem(idx)
            }
        )
        b.rvQueue.layoutManager = LinearLayoutManager(context)
        b.rvQueue.adapter = adapter

        fun refresh() {
            adapter.submit(MusicPlayerManager.getQueue())
        }
        refresh()

        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                MusicPlayerManager.moveQueueItem(from, to)
                refresh()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        helper.attachToRecyclerView(b.rvQueue)

        dialog.show()
    }
}
