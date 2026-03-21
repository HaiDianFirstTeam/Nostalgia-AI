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
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicPlayMode
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicDownloadItem
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlaylistDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityListBinding
    private lateinit var store: MusicStore
    private lateinit var trackAdapter: TrackAdapter
    private var playlistId: Long = 0
    private var tracks: List<MusicTrack> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistId = intent.getLongExtra(EXTRA_ID, 0)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        trackAdapter = TrackAdapter(
            onPlay = { t ->
                // Use home fragment play logic by starting single playback.
                MaterialAlertDialogBuilder(this)
                    .setTitle("提示")
                    .setMessage("请从搜索页点击播放（此处播放将在后续版本完善为直接播放）。")
                    .setPositiveButton("确定", null)
                    .show()
            },
            onDownload = { _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("提示")
                    .setMessage("请从搜索页点击下载（后续版本支持歌单批量下载）。")
                    .setPositiveButton("确定", null)
                    .show()
            },
            onMore = { t ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(t.name)
                    .setItems(arrayOf("从歌单移除")) { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { store.removeTrackFromPlaylist(playlistId, t) }
                            refresh()
                        }
                    }
                    .show()
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = trackAdapter

        binding.fab.setOnClickListener {
            showActionsDialog()
        }

        refresh()
    }

    private fun showActionsDialog() {
        val items = arrayOf("播放模式/设为播放列表", "批量下载")
        MaterialAlertDialogBuilder(this)
            .setTitle("操作")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showPlayModeDialog()
                    1 -> batchDownload()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun batchDownload() {
        if (tracks.isEmpty()) return
        val inputQ1 = arrayOf("128", "192", "320", "740", "999")
        MaterialAlertDialogBuilder(this)
            .setTitle("批量下载：选择音质")
            .setItems(inputQ1) { _, w ->
                val br = inputQ1[w].toInt()
                doBatchDownloadApi1(br)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doBatchDownloadApi1(br: Int) {
        val api1 = com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client()
        lifecycleScope.launch {
            val errors = ArrayList<String>()
            withContext(Dispatchers.IO) {
                for (t in tracks) {
                    try {
                        val url = api1.getPlayUrl(source = "netease", trackId = t.id, br = br).url
                        val enq = MusicDownloader.enqueue(this@MusicPlaylistDetailActivity, t, url)
                        store.addDownload(
                            MusicDownloadItem(
                                downloadId = enq.downloadId,
                                createdAt = System.currentTimeMillis(),
                                fileName = enq.fileName,
                                track = t
                            )
                        )
                    } catch (e: Throwable) {
                        errors.add(t.name)
                    }
                }
            }
            if (errors.isEmpty()) {
                com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(this@MusicPlaylistDetailActivity, "已加入批量下载")
            } else {
                MaterialAlertDialogBuilder(this@MusicPlaylistDetailActivity)
                    .setTitle("部分失败")
                    .setMessage(errors.take(30).joinToString("\n"))
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    private fun showPlayModeDialog() {
        val items = arrayOf("顺序播放", "随机播放", "单曲循环")
        MaterialAlertDialogBuilder(this)
            .setTitle("播放模式")
            .setItems(items) { _, which ->
                val mode = when (which) {
                    1 -> MusicPlayMode.SHUFFLE
                    2 -> MusicPlayMode.LOOP_ONE
                    else -> MusicPlayMode.ORDER
                }
                MusicPlayerManager.setMode(mode)
                if (tracks.isNotEmpty()) {
                    val list = when (mode) {
                        MusicPlayMode.SHUFFLE -> tracks.shuffled()
                        else -> tracks
                    }
                    MusicPlayerManager.setQueue(list, 0)
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(this, "已设置播放列表")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val pl = withContext(Dispatchers.IO) { store.listPlaylists().firstOrNull { it.id == playlistId } }
            if (pl == null) {
                finish();
                return@launch
            }
            title = pl.name
            tracks = pl.tracks
            trackAdapter.submit(tracks)
            binding.empty.visibility = if (tracks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.empty.text = "歌单为空"
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        fun newIntent(context: Context, playlistId: Long): Intent =
            Intent(context, MusicPlaylistDetailActivity::class.java).putExtra(EXTRA_ID, playlistId)
    }
}
