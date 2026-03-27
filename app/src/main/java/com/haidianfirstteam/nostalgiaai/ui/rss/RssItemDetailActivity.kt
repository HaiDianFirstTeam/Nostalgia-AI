package com.haidianfirstteam.nostalgiaai.ui.rss

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityRssItemDetailBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssItemDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityRssItemDetailBinding
    private lateinit var store: RssStore

    private var itemId: Long = 0
    private var item: RssItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRssItemDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0)
        val app = application as NostalgiaApp
        store = RssStore(app.db)

        initWebView()
        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "复制链接")
        menu.add(0, 2, 1, "浏览器打开")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val cur = this.item
        return when (item.itemId) {
            1 -> {
                if (cur != null) copy(cur.link)
                true
            }
            2 -> {
                if (cur != null && cur.link.isNotBlank()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cur.link)))
                    } catch (_: Throwable) {
                        ToastUtil.show(this, "无法打开")
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initWebView() {
        val w = binding.web
        w.settings.javaScriptEnabled = false
        w.settings.domStorageEnabled = true
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }
    }

    private fun load() {
        lifecycleScope.launch {
            val it = withContext(Dispatchers.IO) { store.getItemById(itemId) }
            if (it == null) {
                finish()
                return@launch
            }
            item = it
            title = it.title

            val baseUrl = it.link.ifBlank { null }
            val html = buildHtml(it)
            binding.web.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
        }
    }

    private fun buildHtml(it: RssItem): String {
        val title = escape(it.title)
        val author = escape(it.author)
        val content = it.contentHtml
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                body { font-family: sans-serif; padding: 14px; line-height: 1.6; }
                img { max-width: 100%; height: auto; }
                pre, code { white-space: pre-wrap; word-break: break-word; }
                .meta { color: #666; font-size: 12px; margin-bottom: 10px; }
                a { word-break: break-word; }
              </style>
            </head>
            <body>
              <h2>$title</h2>
              <div class="meta">$author</div>
              <div>$content</div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escape(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun copy(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("link", text))
            ToastUtil.show(this, "已复制")
        } catch (e: Throwable) {
            MaterialAlertDialogBuilder(this)
                .setTitle("复制失败")
                .setMessage(e.message ?: "未知错误")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"

        fun newIntent(context: Context, itemId: Long): Intent {
            return Intent(context, RssItemDetailActivity::class.java).putExtra(EXTRA_ITEM_ID, itemId)
        }
    }
}
