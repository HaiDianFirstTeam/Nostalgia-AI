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
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicPlayHistoryItem
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlayHistoryActivity : BaseActivity() {
    private lateinit var binding: ActivityListBinding
    private lateinit var adapter: MusicPlayHistoryAdapter
    private lateinit var store: MusicStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = "历史记录"

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        adapter = MusicPlayHistoryAdapter(
            onDelete = { item ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.deletePlayHistory(item) }
                    refresh()
                }
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("清空历史")
                .setMessage("确定要清空全部听歌记录吗？")
                .setPositiveButton("清空") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.clearPlayHistory() }
                        refresh()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val list: List<MusicPlayHistoryItem> = withContext(Dispatchers.IO) { store.listPlayHistory() }
            adapter.submit(list)
            binding.empty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.empty.text = "暂无历史"
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, MusicPlayHistoryActivity::class.java)
    }
}
