package com.haidianfirstteam.nostalgiaai.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.FragmentMusicHomeBinding
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi2Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicSourceType
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicHomeFragment : Fragment() {

    private var _b: FragmentMusicHomeBinding? = null
    private val b get() = _b!!

    private lateinit var store: MusicStore
    private val api1 = MusicApi1Client()
    private val api2 = MusicApi2Client()
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentMusicHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as NostalgiaApp
        store = MusicStore(app.db)

        trackAdapter = TrackAdapter(
            onPlay = { t -> playTrack(t) },
            onDownload = { t -> downloadTrack(t) },
            onMore = { t -> showTrackActions(t) }
        )
        b.rvTracks.layoutManager = LinearLayoutManager(requireContext())
        b.rvTracks.adapter = trackAdapter

        historyAdapter = HistoryAdapter(
            onClick = { q ->
                b.etSearch.setText(q)
                b.etSearch.setSelection(q.length)
                runSearch(q)
            },
            onDelete = { q ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.deleteSearchHistory(q) }
                    refreshHistory()
                }
            }
        )
        b.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        b.rvHistory.adapter = historyAdapter

        b.btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { store.clearSearchHistory() }
                refreshHistory()
            }
        }

        b.btnSwitchSource.setOnClickListener {
            showSourceDialog()
        }

        b.btnAddPlaylist.setOnClickListener {
            showAddPlaylistDialog()
        }

        b.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) refreshHistory()
            b.historyPanel.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
        b.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(b.etSearch.text?.toString().orEmpty())
                true
            } else false
        }
        b.btnSearch.setOnClickListener {
            runSearch(b.etSearch.text?.toString().orEmpty())
        }

        refreshHistory()
    }

    private fun showTrackActions(t: MusicTrack) {
        val ctx = requireContext()
        val items = arrayOf("在线播放", "加入播放列表", "添加到歌单", "下载")
        MaterialAlertDialogBuilder(ctx)
            .setTitle(t.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> playTrack(t)
                    1 -> {
                        // add to queue tail
                        val q = MusicPlayerManager.getQueue().toMutableList()
                        q.add(t)
                        MusicPlayerManager.setQueue(q, startIndex = (MusicPlayerManager.state.value?.index ?: -1).coerceAtLeast(0))
                        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已加入播放列表")
                    }
                    2 -> addToPlaylist(t)
                    3 -> downloadTrack(t)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addToPlaylist(t: MusicTrack) {
        val ctx = requireContext()
        lifecycleScope.launch {
            val pls = withContext(Dispatchers.IO) { store.listPlaylists() }
            if (pls.isEmpty()) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("暂无歌单")
                    .setMessage("先创建一个歌单")
                    .setPositiveButton("创建") { _, _ ->
                        createPlaylistAndAdd(t)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return@launch
            }
            val names = pls.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(ctx)
                .setTitle("添加到歌单")
                .setItems(names) { _, which ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.addTrackToPlaylist(pls[which].id, t) }
                        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已添加")
                    }
                }
                .setPositiveButton("新建") { _, _ ->
                    createPlaylistAndAdd(t)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun createPlaylistAndAdd(t: MusicTrack) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx)
        input.hint = "歌单名称"
        MaterialAlertDialogBuilder(ctx)
            .setTitle("新建歌单")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text?.toString().orEmpty()
                lifecycleScope.launch {
                    val pl = withContext(Dispatchers.IO) { store.createPlaylist(name) }
                    withContext(Dispatchers.IO) { store.addTrackToPlaylist(pl.id, t) }
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已创建并添加")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun playTrack(t: MusicTrack) {
        val ctx = requireContext()
        lifecycleScope.launch {
            b.progress.visibility = View.VISIBLE
            try {
                val (url, _) = withContext(Dispatchers.IO) {
                    val s = store.getSettings()
                    val src = s.source
                    if (src == MusicSourceType.API2_WYAPI) {
                        val level = s.quality.streamLevel ?: throw IllegalStateException("请先在设置里选择在线播放默认音质(API2)")
                        val u = api2.getPlayUrl(t.id, level).url
                        Pair(u, "${level}")
                    } else {
                        val br = s.quality.streamBr ?: throw IllegalStateException("请先在设置里选择在线播放默认音质(API1)")
                        val u = api1.getPlayUrl(source = t.source.ifBlank { "netease" }, trackId = t.id, br = br).url
                        Pair(u, "${br}")
                    }
                }
                withContext(Dispatchers.IO) { store.addPlayHistory(t) }
                MusicPlayerManager.playSingle(requireContext().applicationContext, t, url)
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

    private fun downloadTrack(t: MusicTrack) {
        showDownloadDialog(t)
    }

    private fun showDownloadDialog(t: MusicTrack) {
        val ctx = requireContext()
        lifecycleScope.launch {
            val cur = withContext(Dispatchers.IO) { store.getSettings() }
            val sources = arrayOf("API1", "API2")
            val sourceValues = arrayOf(MusicSourceType.API1_GDSTUDIO, MusicSourceType.API2_WYAPI)
            val checked = sourceValues.indexOf(cur.downloadSource).coerceAtLeast(0)
            MaterialAlertDialogBuilder(ctx)
                .setTitle("选择下载音源")
                .setSingleChoiceItems(sources, checked) { d, which ->
                    d.dismiss()
                    val src = sourceValues[which]
                    if (src == MusicSourceType.API2_WYAPI) {
                        pickApi2DownloadQuality(t, cur)
                    } else {
                        pickApi1DownloadQuality(t, cur)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun pickApi1DownloadQuality(t: MusicTrack, cur: com.haidianfirstteam.nostalgiaai.ui.music.data.MusicSettings) {
        val ctx = requireContext()
        val items = arrayOf("128", "192", "320", "740", "999")
        MaterialAlertDialogBuilder(ctx)
            .setTitle("选择音质(API1)")
            .setItems(items) { _, which ->
                val br = items[which].toInt()
                val cb = android.widget.CheckBox(ctx).apply { text = "设为默认" }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("确认下载")
                    .setMessage("${t.name} (${br})")
                    .setView(cb)
                    .setPositiveButton("下载") { _, _ ->
                        lifecycleScope.launch {
                            b.progress.visibility = View.VISIBLE
                            try {
                                val url = withContext(Dispatchers.IO) {
                                    api1.getPlayUrl(source = t.source.ifBlank { "netease" }, trackId = t.id, br = br).url
                                }
                                if (cb.isChecked) {
                                    val next = cur.copy(downloadSource = MusicSourceType.API1_GDSTUDIO, quality = cur.quality.copy(downloadBr = br))
                                    withContext(Dispatchers.IO) { store.setSettings(next) }
                                }
                                MusicDownloader.enqueue(ctx, t, url)
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
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickApi2DownloadQuality(t: MusicTrack, cur: com.haidianfirstteam.nostalgiaai.ui.music.data.MusicSettings) {
        val ctx = requireContext()
        val items = arrayOf("standard", "exhigh", "lossless", "hires")
        MaterialAlertDialogBuilder(ctx)
            .setTitle("选择音质(API2)")
            .setItems(items) { _, which ->
                val level = items[which]
                val cb = android.widget.CheckBox(ctx).apply { text = "设为默认" }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("确认下载")
                    .setMessage("${t.name} (${level})")
                    .setView(cb)
                    .setPositiveButton("下载") { _, _ ->
                        lifecycleScope.launch {
                            b.progress.visibility = View.VISIBLE
                            try {
                                val url = withContext(Dispatchers.IO) {
                                    api2.getPlayUrl(t.id, level).url
                                }
                                if (cb.isChecked) {
                                    val next = cur.copy(downloadSource = MusicSourceType.API2_WYAPI, quality = cur.quality.copy(downloadLevel = level))
                                    withContext(Dispatchers.IO) { store.setSettings(next) }
                                }
                                MusicDownloader.enqueue(ctx, t, url)
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
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSourceDialog() {
        val ctx = requireContext()
        lifecycleScope.launch {
            val cur = withContext(Dispatchers.IO) { store.getSource() }
            val items = arrayOf("GD Studio（API1）", "网易云无损（API2）")
            val values = arrayOf(MusicSourceType.API1_GDSTUDIO, MusicSourceType.API2_WYAPI)
            val checked = values.indexOf(cur).coerceAtLeast(0)
            MaterialAlertDialogBuilder(ctx)
                .setTitle("切换音源")
                .setSingleChoiceItems(items, checked) { d, which ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.setSource(values[which]) }
                        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已切换")
                    }
                    d.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun showAddPlaylistDialog() {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx)
        input.hint = "歌单ID 或 专辑ID"
        MaterialAlertDialogBuilder(ctx)
            .setTitle("添加并解析")
            .setView(input)
            .setPositiveButton("解析") { _, _ ->
                val raw = input.text?.toString().orEmpty().trim()
                if (raw.isBlank()) return@setPositiveButton
                // Keep it simple: extract digits.
                val id = raw.filter { it.isDigit() }
                if (id.isBlank()) {
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "无效ID")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    b.progress.visibility = View.VISIBLE
                    try {
                        val tracks = withContext(Dispatchers.IO) {
                            api2.getPlaylistTracks(id)
                        }
                        val nameInput = android.widget.EditText(ctx)
                        nameInput.setText("歌单${id}")
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("保存为歌单")
                            .setView(nameInput)
                            .setPositiveButton("保存") { _, _ ->
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        val pl = store.createPlaylist(nameInput.text?.toString().orEmpty())
                                        store.upsertPlaylist(pl.copy(tracks = tracks))
                                    }
                                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已保存歌单 (${tracks.size}首)")
                                }
                            }
                            .setNegativeButton("仅查看", null)
                            .show()

                        trackAdapter.submit(tracks)
                    } catch (t: Throwable) {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("解析失败")
                            .setMessage(t.message ?: "未知错误")
                            .setPositiveButton("确定", null)
                            .show()
                    } finally {
                        b.progress.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshHistory() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { store.listSearchHistory() }
            historyAdapter.submit(items)
            b.btnClearHistory.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun runSearch(keywordRaw: String) {
        val keyword = keywordRaw.trim()
        if (keyword.isBlank()) return

        b.historyPanel.visibility = View.GONE
        b.etSearch.clearFocus()

        lifecycleScope.launch {
            b.progress.visibility = View.VISIBLE
            try {
                val tracks: List<MusicTrack> = withContext(Dispatchers.IO) {
                    store.addSearchHistory(keyword)
                    when (store.getSource()) {
                        MusicSourceType.API2_WYAPI -> api2.search(keyword = keyword, page = 1)
                        MusicSourceType.API1_GDSTUDIO -> api1.search(source = "netease", keyword = keyword, page = 1, count = 20)
                    }
                }
                trackAdapter.submit(tracks)
            } catch (t: Throwable) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("搜索失败")
                    .setMessage(t.message ?: "未知错误")
                    .setPositiveButton("确定", null)
                    .show()
            } finally {
                b.progress.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): MusicHomeFragment = MusicHomeFragment()
    }
}
