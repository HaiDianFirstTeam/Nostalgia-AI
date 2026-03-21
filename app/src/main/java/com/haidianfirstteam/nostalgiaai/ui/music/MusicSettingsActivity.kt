package com.haidianfirstteam.nostalgiaai.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityListBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineAdapter
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicSettings
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicSettingsActivity : BaseActivity() {
    private lateinit var binding: ActivityListBinding
    private lateinit var store: MusicStore
    private lateinit var adapter: TwoLineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = "音乐设置"

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        adapter = TwoLineAdapter(
            onClick = { item -> onItem(item) },
            onLongClick = { _ -> }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fab.visibility = android.view.View.GONE
        binding.empty.visibility = android.view.View.GONE

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val s = withContext(Dispatchers.IO) { store.getSettings() }
            val items = listOf(
                TwoLineItem(1, "在线播放默认音质", "br=${s.quality.streamBr ?: "未设置"}"),
                TwoLineItem(2, "下载默认音质", "br=${s.quality.downloadBr ?: "未设置"}"),
            )
            adapter.submit(items)
        }
    }

    private fun onItem(item: TwoLineItem) {
        lifecycleScope.launch {
            val cur = withContext(Dispatchers.IO) { store.getSettings() }
            when (item.id) {
                1L -> pickApi1Br(cur, forDownload = false)
                2L -> pickApi1Br(cur, forDownload = true)
            }
        }
    }

    private fun pickApi1Br(cur: MusicSettings, forDownload: Boolean) {
        val items = arrayOf("128", "192", "320", "740", "999")
        MaterialAlertDialogBuilder(this)
            .setTitle(if (forDownload) "下载默认音质" else "在线播放默认音质")
            .setItems(items) { _, which ->
                val br = items[which].toInt()
                lifecycleScope.launch {
                    val next = if (forDownload) {
                        cur.copy(quality = cur.quality.copy(downloadBr = br))
                    } else {
                        cur.copy(quality = cur.quality.copy(streamBr = br))
                    }
                    withContext(Dispatchers.IO) { store.setSettings(next) }
                    refresh()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, MusicSettingsActivity::class.java)
    }
}
