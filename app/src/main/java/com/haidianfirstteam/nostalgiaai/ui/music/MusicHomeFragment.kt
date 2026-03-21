package com.haidianfirstteam.nostalgiaai.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.FragmentMusicHomeBinding
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client
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
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var albumAdapter: AlbumAdapter

    private var api1Source: String = "netease"
    private var searchKind: MusicStore.Api1SearchKind = MusicStore.Api1SearchKind.TRACK

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
        albumAdapter = AlbumAdapter(
            onOpen = { album ->
                startActivity(MusicAlbumDetailActivity.newIntent(requireContext(), album))
            }
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
            showApi1SourceDialog()
        }
        b.btnSwitchSource.visibility = View.VISIBLE

        // Keep "Add playlist" hidden here; playlists live in "我的" tab.
        b.btnAddPlaylist.visibility = View.GONE

        // Search kind toggle (track/album)
        b.toggleSearchType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val next = if (checkedId == b.btnTypeAlbum.id) MusicStore.Api1SearchKind.ALBUM else MusicStore.Api1SearchKind.TRACK
            if (next == searchKind) return@addOnButtonCheckedListener
            searchKind = next
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { store.setApi1SearchKind(searchKind) }
            }
            // Auto re-search if keyword exists
            val kw = b.etSearch.text?.toString().orEmpty().trim()
            if (kw.isNotBlank()) runSearch(kw)
        }

        b.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) refreshHistory()
            // History panel must be above track list; otherwise rvTracks (declared later in XML)
            // will cover it and make items unclickable.
            b.historyPanel.visibility = if (hasFocus) View.VISIBLE else View.GONE
            b.rvTracks.visibility = if (hasFocus) View.GONE else View.VISIBLE
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

        // Load persisted source + kind
        lifecycleScope.launch {
            val src = withContext(Dispatchers.IO) { store.getApi1ProviderSource() }
            val kind = withContext(Dispatchers.IO) { store.getApi1SearchKind() }
            api1Source = src
            searchKind = kind
            // reflect UI
            val id = if (kind == MusicStore.Api1SearchKind.ALBUM) b.btnTypeAlbum.id else b.btnTypeTrack.id
            b.toggleSearchType.check(id)
        }
    }

    private fun showApi1SourceDialog() {
        val ctx = requireContext()
        val list = MusicApi1Sources.all
        val curIdx = list.indexOf(api1Source).let { if (it < 0) 0 else it }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("切换音乐源（API1）")
            .setSingleChoiceItems(list.toTypedArray(), curIdx) { dlg, which ->
                val picked = list[which]
                api1Source = picked
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.setApi1ProviderSource(picked) }
                }
                com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已切换：$picked")
                dlg.dismiss()
                // Auto re-search if keyword exists
                val kw = b.etSearch.text?.toString().orEmpty().trim()
                if (kw.isNotBlank()) runSearch(kw)
            }
            .setNegativeButton("取消", null)
            .show()
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
                    // "更多"里的下载保留“本次选择音源+音质”的能力
                    3 -> showDownloadDialog(t)
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
                val settings = withContext(Dispatchers.IO) { store.getSettings() }

                // Apply persisted playback speed (best-effort).
                MusicPlayerManager.setSpeed(settings.playbackSpeed)

                // First play: if default stream quality not set, prompt user here.
                if (settings.quality.streamBr == null) {
                    b.progress.visibility = View.GONE
                    pickApi1StreamQualityThenPlay(t, settings)
                    return@launch
                }

                val url = withContext(Dispatchers.IO) {
                    val br = settings.quality.streamBr ?: 320
                    api1.getPlayUrl(source = t.source.ifBlank { "netease" }, trackId = t.id, br = br).url
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

    private fun pickApi1StreamQualityThenPlay(t: MusicTrack, cur: com.haidianfirstteam.nostalgiaai.ui.music.data.MusicSettings) {
        val ctx = requireContext()
        val items = arrayOf("128", "192", "320", "740", "999")
        val dlg = MaterialAlertDialogBuilder(ctx)
            .setTitle("首次播放：选择默认音质")
            .setItems(items) { _, which ->
                val br = items[which].toInt()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        store.setSettings(cur.copy(quality = cur.quality.copy(streamBr = br)))
                    }
                    playTrack(t)
                }
            }
            .setNegativeButton("取消", null)
            .create()

        dlg.setOnShowListener {
            com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialController.maybeShowDialog(
                dlg,
                "music_first_play_quality",
                listOf(
                    com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialStep(android.R.id.list, "音质列表：选择一个默认音质（以后可在设置中修改）。"),
                    com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialStep(android.R.id.button2, "取消：暂不播放。"),
                )
            )
        }
        dlg.show()
    }

    private fun downloadTrack(t: MusicTrack) {
        val ctx = requireContext()
        lifecycleScope.launch {
            val cur = withContext(Dispatchers.IO) { store.getSettings() }

            // First download: if default download quality not set, prompt user here.
            if (cur.quality.downloadBr == null) {
                pickApi1DownloadQualityFirstTime(t, cur)
                return@launch
            }

            // Defaults are ready: one-click download.
            b.progress.visibility = View.VISIBLE
            try {
                val url = withContext(Dispatchers.IO) {
                    val br = cur.quality.downloadBr ?: 320
                    // API1 expects the provider source string, not our internal track.source.
                    api1.getPlayUrl(source = "netease", trackId = t.id, br = br).url
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

    private fun pickApi1DownloadQualityFirstTime(t: MusicTrack, cur: com.haidianfirstteam.nostalgiaai.ui.music.data.MusicSettings) {
        val ctx = requireContext()
        val items = arrayOf("128", "192", "320", "740", "999")
        val dlg = MaterialAlertDialogBuilder(ctx)
            .setTitle("首次下载：选择默认音质")
            .setItems(items) { _, which ->
                val br = items[which].toInt()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        store.setSettings(cur.copy(downloadSource = MusicSourceType.API1_GDSTUDIO, quality = cur.quality.copy(downloadBr = br)))
                    }
                    downloadTrack(t)
                }
            }
            .setNegativeButton("取消", null)
            .create()

        dlg.setOnShowListener {
            com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialController.maybeShowDialog(
                dlg,
                "music_first_download_quality",
                listOf(
                    com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialStep(android.R.id.list, "音质列表：选择一个默认下载音质（以后可在设置中修改）。"),
                    com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialStep(android.R.id.button2, "取消：暂不下载。"),
                )
            )
        }
        dlg.show()
    }

    private fun showDownloadDialog(t: MusicTrack) {
        val ctx = requireContext()
        lifecycleScope.launch {
            val cur = withContext(Dispatchers.IO) { store.getSettings() }
            // Source2 removed: keep a simple per-download quality picker.
            pickApi1DownloadQuality(t, cur)
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
                                    api1.getPlayUrl(source = "netease", trackId = t.id, br = br).url
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

    // Source2 removed: no source switch dialog.

    private fun showAddPlaylistDialog() {
        // Source2 removed; keep as a stub.
        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(requireContext(), "音源2已移除：歌单/专辑解析功能暂不可用")
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

        // Force UI into results mode (do not rely on focus callbacks).
        b.historyPanel.visibility = View.GONE
        b.rvTracks.visibility = View.VISIBLE

        // Hide keyboard & drop focus, otherwise history panel might come back.
        try {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(b.etSearch.windowToken, 0)
        } catch (_: Throwable) {
            // ignore
        }
        b.etSearch.clearFocus()
        b.root.requestFocus()

        // On some legacy devices, clearFocus() might not trigger onFocusChange reliably
        // (e.g. when no other focusable view takes focus), which can leave rvTracks hidden.
        // Make the UI state explicit before launching the search.
        b.rvTracks.visibility = View.VISIBLE
        b.etSearch.clearFocus()
        b.root.requestFocus()

        lifecycleScope.launch {
            b.progress.visibility = View.VISIBLE
            try {
                withContext(Dispatchers.IO) { store.addSearchHistory(keyword) }

                if (searchKind == MusicStore.Api1SearchKind.TRACK) {
                    val tracks: List<MusicTrack> = withContext(Dispatchers.IO) {
                        api1.search(source = api1Source, keyword = keyword, page = 1, count = 20)
                    }
                    b.rvTracks.adapter = trackAdapter
                    trackAdapter.submit(tracks)
                    if (tracks.isEmpty()) {
                        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(requireContext(), "没有搜索结果（或请求过于频繁被限制）")
                    }
                } else {
                    // Album mode: try *_album first, then fall back to base source.
                    val tracks: List<MusicTrack> = withContext(Dispatchers.IO) {
                        val s1 = api1Source + "_album"
                        val t1 = try { api1.search(source = s1, keyword = keyword, page = 1, count = 50) } catch (_: Throwable) { emptyList() }
                        if (t1.isNotEmpty()) t1 else api1.search(source = api1Source, keyword = keyword, page = 1, count = 50)
                    }
                    val albums = groupAsAlbums(tracks)
                    b.rvTracks.adapter = albumAdapter
                    albumAdapter.submit(albums)
                    if (albums.isEmpty()) {
                        com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(requireContext(), "没有专辑结果（或请求过于频繁被限制）")
                    }
                }

                // Make final UI state explicit (some legacy devices have flaky focus callbacks).
                b.historyPanel.visibility = View.GONE
                b.rvTracks.visibility = View.VISIBLE
                b.etSearch.clearFocus()
                b.root.requestFocus()

                if (tracks.isEmpty()) {
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(requireContext(), "没有搜索结果（或请求过于频繁被限制）")
                }
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

    private fun groupAsAlbums(tracks: List<MusicTrack>): List<MusicAlbumUi> {
        if (tracks.isEmpty()) return emptyList()
        val groups = tracks.filter { it.album?.isNotBlank() == true }
            .groupBy { (it.source.ifBlank { api1Source }) + "|" + (it.album ?: "") + "|" + (it.coverId ?: "") }
        val out = ArrayList<MusicAlbumUi>(groups.size)
        for ((key, list) in groups) {
            val first = list.firstOrNull() ?: continue
            val album = first.album ?: continue
            val artists = first.artists.joinToString("/").ifBlank { "未知歌手" }
            out.add(
                MusicAlbumUi(
                    key = key,
                    source = first.source.ifBlank { api1Source },
                    albumName = album,
                    coverId = first.coverId,
                    trackCount = list.size,
                    artistsSummary = artists,
                    tracks = list
                )
            )
        }
        // stable sort: most tracks first
        return out.sortedWith(compareByDescending<MusicAlbumUi> { it.trackCount }.thenBy { it.albumName })
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): MusicHomeFragment = MusicHomeFragment()
    }
}
