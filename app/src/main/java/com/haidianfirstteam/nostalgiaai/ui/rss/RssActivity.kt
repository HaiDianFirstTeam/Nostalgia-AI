package com.haidianfirstteam.nostalgiaai.ui.rss

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.databinding.ActivityRssBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * RSS订阅：推荐(聚合) / 订阅(源管理)
 */
class RssActivity : BaseActivity() {

    private lateinit var binding: ActivityRssBinding
    private lateinit var store: RssStore
    private val client = RssClient()
    private val parser = RssParser()

    private enum class Tab { RECOMMENDED, SOURCES }
    private var tab: Tab = Tab.RECOMMENDED

    private lateinit var sourceAdapter: RssSourceAdapter
    private lateinit var itemAdapter: RssItemAdapter

    private var pendingOpmlExport: String? = null
    private val REQ_IMPORT_OPML = 4101
    private val REQ_EXPORT_OPML = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRssBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        title = "RSS订阅"

        val app = application as NostalgiaApp
        store = RssStore(app.db)

        sourceAdapter = RssSourceAdapter(
            onClick = { src ->
                startActivity(RssSourceDetailActivity.newIntent(this, src.id))
            },
            onMenu = { src ->
                showSourceMenu(src)
            }
        )
        itemAdapter = RssItemAdapter(
            onClick = { item ->
                startActivity(RssItemDetailActivity.newIntent(this, item.id))
            }
        )

        binding.recycler.layoutManager = LinearLayoutManager(this)

        binding.toggleTabs.addOnButtonCheckedListener(object : MaterialButtonToggleGroup.OnButtonCheckedListener {
            override fun onButtonChecked(group: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean) {
                if (!isChecked) return
                tab = if (checkedId == binding.btnTabSources.id) Tab.SOURCES else Tab.RECOMMENDED
                refresh()
            }
        })
        binding.toggleTabs.check(binding.btnTabRecommended.id)

        binding.fab.visibility = View.VISIBLE
        binding.fab.setOnClickListener { showAddDialog() }

        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "刷新")
        menu.add(0, 2, 1, "导入OPML")
        menu.add(0, 3, 2, "导出OPML")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                refreshAllSources()
                true
            }
            2 -> {
                importOpml()
                true
            }
            3 -> {
                exportOpml()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        when (tab) {
            Tab.RECOMMENDED -> {
                binding.recycler.adapter = itemAdapter
                lifecycleScope.launch {
                    val items = withContext(Dispatchers.IO) { store.listRecommended() }
                    itemAdapter.submit(items)
                    binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.empty.text = if (items.isEmpty()) "暂无内容，先订阅一个源" else ""
                }
            }

            Tab.SOURCES -> {
                binding.recycler.adapter = sourceAdapter
                lifecycleScope.launch {
                    val sources = withContext(Dispatchers.IO) { store.listSources() }
                    sourceAdapter.submit(sources)
                    binding.empty.visibility = if (sources.isEmpty()) View.VISIBLE else View.GONE
                    binding.empty.text = if (sources.isEmpty()) "暂无订阅源，点右下角 + 添加" else ""
                }
            }
        }
    }

    private fun showAddDialog() {
        val input = android.widget.EditText(this)
        input.hint = "订阅链接（RSS/Atom 或网页URL）"
        MaterialAlertDialogBuilder(this)
            .setTitle("添加订阅")
            .setView(input)
            .setPositiveButton("下一步") { _, _ ->
                val url = input.text?.toString().orEmpty().trim()
                if (url.isBlank()) return@setPositiveButton
                handleAddUrl(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleAddUrl(url: String) {
        lifecycleScope.launch {
            binding.fab.isEnabled = false
            try {
                val fetched = withContext(Dispatchers.IO) { client.fetch(url) }
                val body = fetched.body
                val trimmed = body.trimStart()
                if (trimmed.startsWith("<rss") || trimmed.startsWith("<?xml") || trimmed.startsWith("<feed")) {
                    subscribeFeed(fetched.finalUrl, body)
                } else {
                    val links = HtmlExtract.discoverFeedLinks(body)
                    if (links.isEmpty()) {
                        MaterialAlertDialogBuilder(this@RssActivity)
                            .setTitle("未发现订阅链接")
                            .setMessage("该网页未提供 RSS/Atom 链接")
                            .setPositiveButton("确定", null)
                            .show()
                    } else {
                        val abs = links.map { toAbsolute(url, it) }.distinct()
                        val items = abs.toTypedArray()
                        MaterialAlertDialogBuilder(this@RssActivity)
                            .setTitle("选择订阅")
                            .setItems(items) { _, which ->
                                lifecycleScope.launch {
                                    val f2 = withContext(Dispatchers.IO) { client.fetch(items[which]) }
                                    subscribeFeed(f2.finalUrl, f2.body)
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            } catch (e: Throwable) {
                MaterialAlertDialogBuilder(this@RssActivity)
                    .setTitle("添加失败")
                    .setMessage(e.message ?: "未知错误")
                    .setPositiveButton("确定", null)
                    .show()
            } finally {
                binding.fab.isEnabled = true
            }
        }
    }

    private suspend fun subscribeFeed(feedUrl: String, xml: String) {
        val nickDefault = feedUrl
        val src = withContext(Dispatchers.IO) { store.addSource(feedUrl, nickDefault) }
        val parsed = withContext(Dispatchers.IO) { parser.parse(src.id, src.nick, xml) }
        withContext(Dispatchers.IO) {
            store.updateSourceMeta(src.id, parsed.meta.title, parsed.meta.iconUrl, lastSyncAt = System.currentTimeMillis(), lastError = null)
            store.upsertItems(src.id, parsed.items)
        }
        ToastUtil.show(this, "已订阅")
        // Switch to sources tab to manage
        binding.toggleTabs.check(binding.btnTabSources.id)
        tab = Tab.SOURCES
        refresh()
    }

    private fun showSourceMenu(src: RssSource) {
        val items = arrayOf("刷新", "改名", "复制链接", "删除")
        MaterialAlertDialogBuilder(this)
            .setTitle(src.nick.ifBlank { src.url })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> refreshOneSource(src.id)
                    1 -> renameSource(src)
                    2 -> copyToClipboard(src.url)
                    3 -> confirmDeleteSource(src)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun renameSource(src: RssSource) {
        val input = android.widget.EditText(this)
        input.setText(src.nick)
        MaterialAlertDialogBuilder(this)
            .setTitle("改名")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text?.toString().orEmpty()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.renameSource(src.id, name) }
                    refresh()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteSource(src: RssSource) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除订阅")
            .setMessage("确认删除该订阅源吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.deleteSource(src.id) }
                    refresh()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshAllSources() {
        lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) { store.listSources() }
            for (s in sources) {
                refreshOneSource(s.id)
                // Basic throttling to reduce chance of rate-limits.
                delay(600)
            }
            refresh()
        }
    }

    private fun refreshOneSource(sourceId: Long) {
        lifecycleScope.launch {
            val src = withContext(Dispatchers.IO) { store.getSourceById(sourceId) }
            if (src == null) return@launch
            try {
                val fetched = withContext(Dispatchers.IO) { client.fetch(src.url) }
                val parsed = withContext(Dispatchers.IO) { parser.parse(src.id, src.nick, fetched.body) }
                withContext(Dispatchers.IO) {
                    store.updateSourceMeta(src.id, parsed.meta.title, parsed.meta.iconUrl, System.currentTimeMillis(), lastError = null)
                    store.upsertItems(src.id, parsed.items)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.IO) {
                    store.updateSourceMeta(src.id, null, null, System.currentTimeMillis(), lastError = e.message ?: "sync error")
                }
            }
            refresh()
        }
    }

    private fun importOpml() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "text/*"
        startActivityForResult(i, REQ_IMPORT_OPML)
    }

    private fun exportOpml() {
        lifecycleScope.launch {
            val xml = withContext(Dispatchers.IO) { store.exportOpml() }
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "text/xml"
            i.putExtra(Intent.EXTRA_TITLE, "nostalgia-rss.opml")
            pendingOpmlExport = xml
            startActivityForResult(i, REQ_EXPORT_OPML)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return
        when (requestCode) {
            REQ_IMPORT_OPML -> {
                val uri = data.data ?: return
                lifecycleScope.launch {
                    try {
                        val text = withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.use { it.reader(Charsets.UTF_8).readText() }.orEmpty()
                        }
                        val urls = withContext(Dispatchers.IO) { store.importOpml(text) }
                        val added = withContext(Dispatchers.IO) { store.addSourcesIfMissing(urls) }
                        ToastUtil.show(this@RssActivity, "已导入 $added 个订阅")
                        refresh()
                    } catch (e: Throwable) {
                        MaterialAlertDialogBuilder(this@RssActivity)
                            .setTitle("导入失败")
                            .setMessage(e.message ?: "未知错误")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }
            REQ_EXPORT_OPML -> {
                val uri = data.data ?: return
                val xml = pendingOpmlExport ?: return
                pendingOpmlExport = null
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
                        }
                        ToastUtil.show(this@RssActivity, "已导出 OPML")
                    } catch (e: Throwable) {
                        MaterialAlertDialogBuilder(this@RssActivity)
                            .setTitle("导出失败")
                            .setMessage(e.message ?: "未知错误")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun toAbsolute(base: String, href: String): String {
        return try {
            URI(base).resolve(href).toString()
        } catch (_: Throwable) {
            href
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("rss", text))
            ToastUtil.show(this, "已复制")
        } catch (_: Throwable) {
            ToastUtil.show(this, "复制失败")
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, RssActivity::class.java)
    }
}
