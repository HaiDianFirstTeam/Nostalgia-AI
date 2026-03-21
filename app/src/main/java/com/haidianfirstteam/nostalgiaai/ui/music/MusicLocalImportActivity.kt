package com.haidianfirstteam.nostalgiaai.ui.music

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.databinding.ActivityMusicLocalManagerBinding
import com.haidianfirstteam.nostalgiaai.databinding.ItemLocalMusicBinding
import com.haidianfirstteam.nostalgiaai.databinding.ItemMusicDownloadBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack
import com.haidianfirstteam.nostalgiaai.ui.music.data.LocalMusicItem
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicDownloadItem
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import com.haidianfirstteam.nostalgiaai.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地/下载管理：
 * - 本地：导入文件/文件夹、列表、多选、删除、加入歌单/队列、重命名(仅列表名)
 * - 下载：显示 DownloadManager 下载条目、进度、删除、播放
 */
class MusicLocalImportActivity : BaseActivity() {

    private lateinit var binding: ActivityMusicLocalManagerBinding
    private lateinit var store: MusicStore

    private lateinit var localAdapter: LocalMusicAdapter
    private lateinit var downloadAdapter: DownloadAdapter

    private var actionMode: ActionMode? = null
    private val selected = LinkedHashSet<String>()

    private enum class Tab { LOCAL, DOWNLOADS }
    private var tab: Tab = Tab.LOCAL
    private var downloadsRefreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicLocalManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = "本地/下载管理"

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        binding.recycler.layoutManager = LinearLayoutManager(this)

        localAdapter = LocalMusicAdapter(
            onClick = { item ->
                if (actionMode != null) {
                    toggleSelect(item.uri)
                } else {
                    // Single tap: play immediately (enqueue as single)
                    val t = item.toTrack()
                    MusicPlayerManager.playSingle(applicationContext, t, item.uri)
                    ToastUtil.show(this, "开始播放")
                }
            },
            onLongClick = { item ->
                if (actionMode == null) startSelectionMode()
                toggleSelect(item.uri)
            },
            isSelected = { uri -> selected.contains(uri) },
            selectionMode = { actionMode != null }
        )

        downloadAdapter = DownloadAdapter(
            onPlay = { ui -> playDownload(ui) },
            onDelete = { ui -> deleteDownload(ui) }
        )

        binding.fab.setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_input_add))
        binding.fab.setOnClickListener { showAddDialog() }

        binding.toggleTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val next = if (checkedId == binding.btnTabDownloads.id) Tab.DOWNLOADS else Tab.LOCAL
            if (next == tab) return@addOnButtonCheckedListener
            setTab(next)
        }

        // default tab
        binding.toggleTabs.check(binding.btnTabLocal.id)
        setTab(Tab.LOCAL)
    }

    override fun onDestroy() {
        downloadsRefreshJob?.cancel()
        downloadsRefreshJob = null
        super.onDestroy()
    }

    private fun setTab(next: Tab) {
        tab = next
        actionMode?.finish()

        when (tab) {
            Tab.LOCAL -> {
                downloadsRefreshJob?.cancel()
                downloadsRefreshJob = null
                binding.fab.visibility = View.VISIBLE
                binding.recycler.adapter = localAdapter
                refreshLocal()
            }

            Tab.DOWNLOADS -> {
                binding.fab.visibility = View.GONE
                binding.recycler.adapter = downloadAdapter
                startDownloadsRefreshLoop()
            }
        }
    }

    private fun refreshLocal() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { store.listLocalMusic() }
            localAdapter.submit(list)
            binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.empty.text = "暂无本地歌曲，点右下角 + 导入"
        }
    }

    private fun startDownloadsRefreshLoop() {
        downloadsRefreshJob?.cancel()
        downloadsRefreshJob = lifecycleScope.launch {
            while (isActive && tab == Tab.DOWNLOADS) {
                refreshDownloadsOnce()
                delay(1000)
            }
        }
    }

    private suspend fun refreshDownloadsOnce() {
        val list = withContext(Dispatchers.IO) { store.listDownloads() }
        if (list.isEmpty()) {
            downloadAdapter.submit(emptyList())
            binding.empty.visibility = View.VISIBLE
            binding.empty.text = "暂无下载任务"
            return
        }
        val ids = list.map { it.downloadId }
        val snapshots = withContext(Dispatchers.IO) { MusicDownloader.query(this@MusicLocalImportActivity, ids) }
        val ui = list.map { DownloadUi(item = it, snapshot = snapshots[it.downloadId]) }
        downloadAdapter.submit(ui)
        binding.empty.visibility = View.GONE
    }

    private fun showAddDialog() {
        val items = ArrayList<String>()
        items.add("选择音频文件")
        if (Build.VERSION.SDK_INT >= 21) items.add("选择文件夹(导入)")
        MaterialAlertDialogBuilder(this)
            .setTitle("导入")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) pickFiles() else pickFolder()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickFiles() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "audio/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, REQ_PICK_FILES)
    }

    private fun pickFolder() {
        if (Build.VERSION.SDK_INT < 21) {
            ToastUtil.show(this, "当前系统不支持选择文件夹")
            return
        }
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(i, REQ_PICK_FOLDER)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        when (requestCode) {
            REQ_PICK_FILES -> handlePickFiles(data)
            REQ_PICK_FOLDER -> handlePickFolder(data)
        }
    }

    private fun handlePickFiles(data: Intent) {
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                val u = clip.getItemAt(i)?.uri
                if (u != null) uris.add(u)
            }
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val out = ArrayList<LocalMusicItem>(uris.size)
                for (u in uris) {
                    takePersistable(u)
                    val name = queryName(u) ?: (u.lastPathSegment ?: "audio")
                    out.add(LocalMusicItem(uri = u.toString(), name = name, addedAt = System.currentTimeMillis()))
                }
                out
            }
            withContext(Dispatchers.IO) { store.upsertLocalMusic(items) }
            refreshLocal()
            ToastUtil.show(this@MusicLocalImportActivity, "已导入 ${items.size} 首")
        }
    }

    private fun handlePickFolder(data: Intent) {
        if (Build.VERSION.SDK_INT < 21) return
        val treeUri = data.data ?: return
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                takePersistable(treeUri)
                val root = DocumentFile.fromTreeUri(this@MusicLocalImportActivity, treeUri)
                if (root == null) return@withContext emptyList<LocalMusicItem>()
                val uris = ArrayList<Uri>()
                collectAudioUris(root, uris, limit = 500)
                val out = ArrayList<LocalMusicItem>(uris.size)
                for (u in uris) {
                    takePersistable(u)
                    val name = queryName(u) ?: (u.lastPathSegment ?: "audio")
                    out.add(LocalMusicItem(uri = u.toString(), name = name, addedAt = System.currentTimeMillis()))
                }
                out
            }
            if (items.isEmpty()) {
                ToastUtil.show(this@MusicLocalImportActivity, "文件夹内未找到音频")
                return@launch
            }
            withContext(Dispatchers.IO) { store.upsertLocalMusic(items) }
            refreshLocal()
            ToastUtil.show(this@MusicLocalImportActivity, "已导入 ${items.size} 首")
        }
    }

    private fun collectAudioUris(dir: DocumentFile, out: MutableList<Uri>, limit: Int) {
        if (out.size >= limit) return
        val children = dir.listFiles()
        for (f in children) {
            if (out.size >= limit) return
            if (f.isDirectory) {
                collectAudioUris(f, out, limit)
            } else {
                val mt = f.type.orEmpty()
                if (mt.startsWith("audio/")) {
                    out.add(f.uri)
                } else {
                    val name = f.name.orEmpty().lowercase()
                    if (name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ogg")) {
                        out.add(f.uri)
                    }
                }
            }
        }
    }

    private fun takePersistable(u: Uri) {
        try {
            val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(u, flags)
        } catch (_: Throwable) {
        }
    }

    private fun queryName(u: Uri): String? {
        var c: Cursor? = null
        return try {
            c = contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (c != null && c.moveToFirst()) c.getString(0) else null
        } catch (_: Throwable) {
            null
        } finally {
            try { c?.close() } catch (_: Throwable) {}
        }
    }

    private fun startSelectionMode() {
        actionMode = startSupportActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
                mode.menuInflater.inflate(R.menu.menu_local_music_selection, menu)
                updateSelectionTitle()
                localAdapter.notifyDataSetChanged()
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
                menu.findItem(R.id.action_rename)?.isVisible = selected.size == 1
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_delete -> {
                        confirmDeleteSelected()
                        true
                    }

                    R.id.action_add_to_queue -> {
                        addSelectedToQueue()
                        mode.finish()
                        true
                    }

                    R.id.action_add_to_playlist -> {
                        addSelectedToPlaylist()
                        true
                    }

                    R.id.action_rename -> {
                        renameSelected()
                        true
                    }

                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                selected.clear()
                localAdapter.notifyDataSetChanged()
            }
        })
    }

    private fun updateSelectionTitle() {
        actionMode?.title = "已选 ${selected.size}"
        actionMode?.invalidate()
    }

    private fun toggleSelect(uri: String) {
        if (selected.contains(uri)) selected.remove(uri) else selected.add(uri)
        updateSelectionTitle()
        localAdapter.notifyDataSetChanged()
    }

    private fun confirmDeleteSelected() {
        if (selected.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("从列表移除")
            .setMessage("确认从本地列表移除所选歌曲吗？（不会删除原文件）")
            .setPositiveButton("确认") { _, _ ->
                lifecycleScope.launch {
                    val uris = selected.toSet()
                    withContext(Dispatchers.IO) { store.deleteLocalMusic(uris) }
                    actionMode?.finish()
                    refreshLocal()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun renameSelected() {
        val uri = selected.firstOrNull() ?: return
        val cur = localAdapter.findByUri(uri) ?: return
        val input = android.widget.EditText(this)
        input.setText(cur.name)
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text?.toString().orEmpty()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.renameLocalMusic(uri, name) }
                    refreshLocal()
                    actionMode?.finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addSelectedToQueue() {
        if (selected.isEmpty()) return
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { store.listLocalMusic() }
            val tracks = list.filter { selected.contains(it.uri) }.map { it.toTrack() }
            if (tracks.isEmpty()) return@launch
            val q = MusicPlayerManager.getQueue().toMutableList()
            q.addAll(tracks)
            MusicPlayerManager.setQueue(q, startIndex = (MusicPlayerManager.state.value?.index ?: -1).coerceAtLeast(0))
            ToastUtil.show(this@MusicLocalImportActivity, "已加入播放列表")
        }
    }

    private fun addSelectedToPlaylist() {
        if (selected.isEmpty()) return
        lifecycleScope.launch {
            val pls = withContext(Dispatchers.IO) { store.listPlaylists() }
            if (pls.isEmpty()) {
                ToastUtil.show(this@MusicLocalImportActivity, "暂无歌单")
                return@launch
            }
            val names = pls.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(this@MusicLocalImportActivity)
                .setTitle("加入歌单")
                .setItems(names) { _, which ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val list = store.listLocalMusic()
                        val tracks = list.filter { selected.contains(it.uri) }.map { it.toTrack() }
                        for (t in tracks) {
                            store.addTrackToPlaylist(pls[which].id, t)
                        }
                        withContext(Dispatchers.Main) {
                            ToastUtil.show(this@MusicLocalImportActivity, "已加入歌单")
                            actionMode?.finish()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun playDownload(ui: DownloadUi) {
        val snap = ui.snapshot
        val local = snap?.localUri
        if (local.isNullOrBlank()) {
            ToastUtil.show(this, "暂无本地文件")
            return
        }
        val t = ui.item.track.copy(id = local, source = "local")
        MusicPlayerManager.playSingle(applicationContext, t, local)
        ToastUtil.show(this, "开始播放")
    }

    private fun deleteDownload(ui: DownloadUi) {
        val id = ui.item.downloadId
        MaterialAlertDialogBuilder(this)
            .setTitle("删除下载")
            .setMessage("确认删除该下载任务/文件吗？")
            .setPositiveButton("确认") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        MusicDownloader.remove(this@MusicLocalImportActivity, longArrayOf(id))
                        store.removeDownloads(setOf(id))
                    }
                    refreshDownloadsOnce()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun LocalMusicItem.toTrack(): MusicTrack {
        return MusicTrack(
            id = uri,
            name = name,
            artists = emptyList(),
            album = null,
            coverId = null,
            source = "local",
        )
    }

    companion object {
        private const val REQ_PICK_FILES = 2101
        private const val REQ_PICK_FOLDER = 2102

        fun newIntent(context: Context): Intent = Intent(context, MusicLocalImportActivity::class.java)
    }

    private class LocalMusicAdapter(
        private val onClick: (LocalMusicItem) -> Unit,
        private val onLongClick: (LocalMusicItem) -> Unit,
        private val isSelected: (String) -> Boolean,
        private val selectionMode: () -> Boolean,
    ) : RecyclerView.Adapter<LocalMusicAdapter.VH>() {

        private val items = ArrayList<LocalMusicItem>()

        fun submit(list: List<LocalMusicItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun findByUri(uri: String): LocalMusicItem? = items.firstOrNull { it.uri == uri }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val b = ItemLocalMusicBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        inner class VH(private val b: ItemLocalMusicBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: LocalMusicItem) {
                b.tvTitle.text = item.name
                b.tvSub.text = item.uri

                // Android 4.4 StaticLayout ellipsize crash workaround
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                    try {
                        b.tvTitle.ellipsize = null
                        b.tvSub.ellipsize = null
                        b.tvTitle.maxLines = 2
                        b.tvSub.maxLines = 2
                    } catch (_: Throwable) {
                    }
                }

                val selMode = selectionMode()
                b.cb.visibility = if (selMode) View.VISIBLE else View.GONE
                b.cb.isChecked = isSelected(item.uri)
                b.root.setOnClickListener { onClick(item) }
                b.root.setOnLongClickListener {
                    onLongClick(item)
                    true
                }
            }
        }
    }

    private data class DownloadUi(
        val item: MusicDownloadItem,
        val snapshot: MusicDownloader.Snapshot?
    )

    private class DownloadAdapter(
        private val onPlay: (DownloadUi) -> Unit,
        private val onDelete: (DownloadUi) -> Unit,
    ) : RecyclerView.Adapter<DownloadAdapter.VH>() {

        private val items = ArrayList<DownloadUi>()

        fun submit(list: List<DownloadUi>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val b = ItemMusicDownloadBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        inner class VH(private val b: ItemMusicDownloadBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(ui: DownloadUi) {
                val t = ui.item.track
                b.tvTitle.text = t.name

                // Android 4.4 StaticLayout ellipsize crash workaround
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                    try {
                        b.tvTitle.ellipsize = null
                        b.tvStatus.ellipsize = null
                        b.tvTitle.maxLines = 2
                        b.tvStatus.maxLines = 2
                        b.tvTitle.setSingleLine(false)
                        b.tvStatus.setSingleLine(false)
                    } catch (_: Throwable) {
                        // ignore
                    }
                }

                val snap = ui.snapshot
                if (snap == null) {
                    b.tvStatus.text = "未知状态"
                    b.progress.isIndeterminate = true
                    b.tvPercent.text = "-"
                } else {
                    b.tvStatus.text = statusText(snap.status, snap.reason)
                    val total = snap.totalBytes
                    val soFar = snap.bytesSoFar
                    if (total > 0) {
                        val pct = ((soFar * 100f) / total).toInt().coerceIn(0, 100)
                        b.progress.isIndeterminate = false
                        b.progress.progress = pct
                        b.tvPercent.text = "${pct}%"
                    } else {
                        b.progress.isIndeterminate = snap.status == DownloadManager.STATUS_RUNNING
                        b.progress.progress = 0
                        b.tvPercent.text = "-"
                    }
                }

                b.btnPlay.setOnClickListener { onPlay(ui) }
                b.btnDelete.setOnClickListener { onDelete(ui) }
            }

            private fun statusText(status: Int, reason: Int): String {
                return when (status) {
                    DownloadManager.STATUS_PENDING -> "等待中"
                    DownloadManager.STATUS_PAUSED -> "已暂停($reason)"
                    DownloadManager.STATUS_RUNNING -> "下载中"
                    DownloadManager.STATUS_SUCCESSFUL -> "已完成"
                    DownloadManager.STATUS_FAILED -> "失败($reason)"
                    else -> "未知($status)"
                }
            }
        }
    }
}
