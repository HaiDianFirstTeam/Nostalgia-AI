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
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicPlaylist
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlaylistsActivity : BaseActivity() {
    private lateinit var binding: ActivityListBinding
    private lateinit var adapter: TwoLineAdapter
    private lateinit var store: MusicStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = "歌单"

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        adapter = TwoLineAdapter(
            onClick = { item ->
                startActivity(MusicPlaylistDetailActivity.newIntent(this, item.id))
            },
            onLongClick = { item ->
                showPlaylistActions(item)
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener {
            showCreateDialog()
        }

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val list: List<MusicPlaylist> = withContext(Dispatchers.IO) { store.listPlaylists() }
            adapter.submit(
                list.map {
                    TwoLineItem(
                        id = it.id,
                        title = it.name,
                        subtitle = "${it.tracks.size} 首"
                    )
                }
            )
            binding.empty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.empty.text = "暂无歌单，点击右下角 + 创建"
        }
    }

    private fun showCreateDialog() {
        val input = android.widget.EditText(this)
        input.hint = "歌单名称"
        MaterialAlertDialogBuilder(this)
            .setTitle("新建歌单")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text?.toString().orEmpty()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.createPlaylist(name) }
                    refresh()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPlaylistActions(item: TwoLineItem) {
        val actions = arrayOf("删除")
        MaterialAlertDialogBuilder(this)
            .setTitle(item.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { store.deletePlaylist(item.id) }
                            refresh()
                        }
                    }
                }
            }
            .show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, MusicPlaylistsActivity::class.java)
    }
}
