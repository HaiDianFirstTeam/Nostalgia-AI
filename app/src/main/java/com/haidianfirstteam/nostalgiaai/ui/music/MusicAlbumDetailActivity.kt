package com.haidianfirstteam.nostalgiaai.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityMusicAlbumDetailBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicDownloadItem
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicAlbumDetailActivity : BaseActivity() {

    private lateinit var b: ActivityMusicAlbumDetailBinding
    private lateinit var store: MusicStore
    private val api1 = MusicApi1Client()
    private val gson = Gson()

    private lateinit var trackAdapter: TrackAdapter
    private var album: MusicAlbumUi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMusicAlbumDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        album = parseAlbum(intent)
        val a = album
        if (a == null) {
            finish()
            return
        }

        b.tvAlbum.text = a.albumName
        b.tvMeta.text = "${a.artistsSummary} · ${a.trackCount}首 · ${a.source}"

        trackAdapter = TrackAdapter(
            onPlay = { t -> playTrack(t) },
            onDownload = { t -> downloadSingle(t) },
            onMore = { t -> showTrackActions(t) }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = trackAdapter
        trackAdapter.submit(a.tracks)

        b.btnAddQueue.setOnClickListener { addAllToQueue() }
        b.btnSavePlaylist.setOnClickListener { saveAllToPlaylist() }
        b.btnBatchDownload.setOnClickListener { batchDownload() }
    }

    private fun parseAlbum(intent: Intent): MusicAlbumUi? {
        val json = intent.getStringExtra(EXTRA_ALBUM_JSON) ?: return null
        return try {
            gson.fromJson(json, MusicAlbumUi::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    private fun addAllToQueue() {
        val a = album ?: return
        val tracks = a.tracks
        if (tracks.isEmpty()) return
        val q = MusicPlayerManager.getQueue().toMutableList()
        val startIndex = if (q.isEmpty()) 0 else (MusicPlayerManager.state.value?.index ?: 0)
        q.addAll(tracks)
        MusicPlayerManager.setQueue(q, startIndex = startIndex.coerceAtLeast(0))
        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(this, "已加入播放列表：${tracks.size}首")
    }

    private fun saveAllToPlaylist() {
        val a = album ?: return
        lifecycleScope.launch {
            val pls = withContext(Dispatchers.IO) { store.listPlaylists() }
            val ctx = this@MusicAlbumDetailActivity
            if (pls.isEmpty()) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("暂无歌单")
                    .setMessage("先创建一个歌单")
                    .setPositiveButton("创建") { _, _ ->
                        createPlaylistAndSaveAll(a)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return@launch
            }
            val names = pls.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(ctx)
                .setTitle("保存到歌单")
                .setItems(names) { _, which ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        for (t in a.tracks) {
                            store.addTrackToPlaylist(pls[which].id, t)
                        }
                    }
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已保存：${a.tracks.size}首")
                }
                .setPositiveButton("新建") { _, _ ->
                    createPlaylistAndSaveAll(a)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun createPlaylistAndSaveAll(a: MusicAlbumUi) {
        val ctx = this
        val input = android.widget.EditText(ctx)
        input.hint = "歌单名称"
        MaterialAlertDialogBuilder(ctx)
            .setTitle("新建歌单")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text?.toString().orEmpty()
                lifecycleScope.launch(Dispatchers.IO) {
                    val pl = store.createPlaylist(name)
                    for (t in a.tracks) {
                        store.addTrackToPlaylist(pl.id, t)
                    }
                    withContext(Dispatchers.Main) {
                        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已创建并保存：${a.tracks.size}首")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun batchDownload() {
        val a = album ?: return
        if (a.tracks.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("批量下载")
            .setMessage("将按默认下载音质逐首加入下载队列（可能触发频率限制）。继续？")
            .setPositiveButton("继续") { _, _ ->
                lifecycleScope.launch {
                    b.progress.visibility = View.VISIBLE
                    try {
                        val cur = withContext(Dispatchers.IO) { store.getSettings() }
                        val br = cur.quality.downloadBr ?: 320
                        var ok = 0
                        for (t in a.tracks) {
                            try {
                                val url = withContext(Dispatchers.IO) {
                                    api1.getPlayUrl(source = t.source.ifBlank { a.source }, trackId = t.id, br = br).url
                                }
                                val enq = MusicDownloader.enqueue(this@MusicAlbumDetailActivity, t, url)
                                withContext(Dispatchers.IO) {
                                    store.addDownload(
                                        MusicDownloadItem(
                                            downloadId = enq.downloadId,
                                            createdAt = System.currentTimeMillis(),
                                            fileName = enq.fileName,
                                            track = t
                                        )
                                    )
                                }
                                ok++
                            } catch (_: Throwable) {
                                // ignore single failures
                            }
                            // Slow down to reduce chance of hitting API limit (50/5min)
                            delay(150)
                        }
                        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(this@MusicAlbumDetailActivity, "已加入下载：$ok/${a.tracks.size}")
                    } finally {
                        b.progress.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun playTrack(t: MusicTrack) {
        val ctx = this
        lifecycleScope.launch {
            b.progress.visibility = View.VISIBLE
            try {
                val settings = withContext(Dispatchers.IO) { store.getSettings() }
                MusicPlayerManager.setSpeed(settings.playbackSpeed)
                val url = withContext(Dispatchers.IO) {
                    val br = settings.quality.streamBr ?: 320
                    api1.getPlayUrl(source = t.source.ifBlank { "netease" }, trackId = t.id, br = br).url
                }
                MusicPlayerManager.playSingle(applicationContext, t, url)
                com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "开始播放")
            } catch (e: Throwable) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("播放失败")
                    .setMessage(e.message ?: "未知错误")
                    .setPositiveButton("确定", null)
                    .show()
            } finally {
                b.progress.visibility = View.GONE
            }
        }
    }

    private fun downloadSingle(t: MusicTrack) {
        val ctx = this
        lifecycleScope.launch {
            val cur = withContext(Dispatchers.IO) { store.getSettings() }
            val br = cur.quality.downloadBr ?: 320
            b.progress.visibility = View.VISIBLE
            try {
                val url = withContext(Dispatchers.IO) {
                    api1.getPlayUrl(source = t.source.ifBlank { "netease" }, trackId = t.id, br = br).url
                }
                val enq = MusicDownloader.enqueue(ctx, t, url)
                withContext(Dispatchers.IO) {
                    store.addDownload(
                        MusicDownloadItem(
                            downloadId = enq.downloadId,
                            createdAt = System.currentTimeMillis(),
                            fileName = enq.fileName,
                            track = t
                        )
                    )
                }
                com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已加入下载")
            } catch (e: Throwable) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("下载失败")
                    .setMessage(e.message ?: "未知错误")
                    .setPositiveButton("确定", null)
                    .show()
            } finally {
                b.progress.visibility = View.GONE
            }
        }
    }

    private fun showTrackActions(t: MusicTrack) {
        val ctx = this
        val items = arrayOf("在线播放", "加入播放列表", "添加到歌单", "下载")
        MaterialAlertDialogBuilder(ctx)
            .setTitle(t.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> playTrack(t)
                    1 -> {
                        val q = MusicPlayerManager.getQueue().toMutableList()
                        q.add(t)
                        MusicPlayerManager.setQueue(q, startIndex = (MusicPlayerManager.state.value?.index ?: -1).coerceAtLeast(0))
                        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已加入播放列表")
                    }
                    2 -> addToPlaylist(t)
                    3 -> downloadSingle(t)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addToPlaylist(t: MusicTrack) {
        val ctx = this
        lifecycleScope.launch {
            val pls = withContext(Dispatchers.IO) { store.listPlaylists() }
            if (pls.isEmpty()) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("暂无歌单")
                    .setMessage("先创建一个歌单")
                    .setPositiveButton("创建") { _, _ ->
                        val input = android.widget.EditText(ctx)
                        input.hint = "歌单名称"
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("新建歌单")
                            .setView(input)
                            .setPositiveButton("确定") { _, _ ->
                                val name = input.text?.toString().orEmpty()
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val pl = store.createPlaylist(name)
                                    store.addTrackToPlaylist(pl.id, t)
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return@launch
            }
            val names = pls.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(ctx)
                .setTitle("添加到歌单")
                .setItems(names) { _, which ->
                    lifecycleScope.launch(Dispatchers.IO) { store.addTrackToPlaylist(pls[which].id, t) }
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已添加")
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    companion object {
        private const val EXTRA_ALBUM_JSON = "album_json"

        fun newIntent(context: Context, album: MusicAlbumUi): Intent {
            val json = Gson().toJson(album)
            return Intent(context, MusicAlbumDetailActivity::class.java).putExtra(EXTRA_ALBUM_JSON, json)
        }
    }
}
