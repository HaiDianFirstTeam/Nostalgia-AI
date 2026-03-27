package com.haidianfirstteam.nostalgiaai.ui.rss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityRssSourceDetailBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssSourceDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityRssSourceDetailBinding
    private lateinit var store: RssStore
    private val client = RssClient()
    private val parser = RssParser()

    private lateinit var adapter: RssItemAdapter
    private var sourceId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRssSourceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, 0)

        val app = application as NostalgiaApp
        store = RssStore(app.db)

        adapter = RssItemAdapter { item ->
            startActivity(RssItemDetailActivity.newIntent(this, item.id))
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "刷新")
        menu.add(0, 2, 1, "源信息")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                refreshFromNetwork()
                true
            }
            2 -> {
                showInfo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            val src = withContext(Dispatchers.IO) { store.getSourceById(sourceId) }
            if (src == null) {
                finish()
                return@launch
            }
            title = src.nick.ifBlank { src.title.ifBlank { "订阅源" } }
            val items = withContext(Dispatchers.IO) { store.listItemsForSource(sourceId) }
            adapter.submit(items)
            binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.empty.text = if (items.isEmpty()) "暂无内容" else ""
        }
    }

    private fun showInfo() {
        lifecycleScope.launch {
            val src = withContext(Dispatchers.IO) { store.getSourceById(sourceId) } ?: return@launch
            val msg = "链接：${src.url}\n\n标题：${src.title}\n\n最后刷新：${src.lastSyncAt}\n\n错误：${src.lastError ?: "无"}"
            MaterialAlertDialogBuilder(this@RssSourceDetailActivity)
                .setTitle("源信息")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun refreshFromNetwork() {
        lifecycleScope.launch {
            val src = withContext(Dispatchers.IO) { store.getSourceById(sourceId) } ?: return@launch
            try {
                val fetched = withContext(Dispatchers.IO) { client.fetch(src.url) }
                val parsed = withContext(Dispatchers.IO) { parser.parse(src.id, src.nick, fetched.body) }
                withContext(Dispatchers.IO) {
                    store.updateSourceMeta(src.id, parsed.meta.title, parsed.meta.iconUrl, System.currentTimeMillis(), lastError = null)
                    store.upsertItems(src.id, parsed.items)
                }
                ToastUtil.show(this@RssSourceDetailActivity, "已刷新")
            } catch (e: Throwable) {
                withContext(Dispatchers.IO) {
                    store.updateSourceMeta(src.id, null, null, System.currentTimeMillis(), lastError = e.message ?: "sync error")
                }
                ToastUtil.show(this@RssSourceDetailActivity, "刷新失败")
            }
            refresh()
        }
    }

    companion object {
        private const val EXTRA_SOURCE_ID = "source_id"

        fun newIntent(context: Context, sourceId: Long): Intent {
            return Intent(context, RssSourceDetailActivity::class.java).putExtra(EXTRA_SOURCE_ID, sourceId)
        }
    }
}
