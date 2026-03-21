package com.haidianfirstteam.nostalgiaai.ui.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.databinding.ActivityListBinding
import com.haidianfirstteam.nostalgiaai.databinding.ItemLocalMusicBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack
import com.haidianfirstteam.nostalgiaai.ui.music.data.LocalMusicItem
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import com.haidianfirstteam.nostalgiaai.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicLocalImportActivity : BaseActivity() {

    private lateinit var binding: ActivityListBinding
    private lateinit var store: MusicStore
    private lateinit var adapter: LocalMusicAdapter

    private var actionMode: ActionMode? = null
    private val selected = LinkedHashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        title = "本地歌曲导入"

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        adapter = LocalMusicAdapter(
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

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_input_add))
        binding.fab.setOnClickListener { showAddDialog() }

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { store.listLocalMusic() }
            adapter.submit(list)
            binding.empty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.empty.text = "暂无本地歌曲，点右下角 + 导入"
        }
    }

    private fun showAddDialog() {
        val items = ArrayList<String>()
        items.add("选择音频文件")
        if (Build.VERSION.SDK_INT >= 21) items.add("选择文件夹(导入)")
        MaterialAlertDialogBuilder(this)
            .setTitle("导入")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) pickFiles()
                else pickFolder()
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
            refresh()
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
            refresh()
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
            if (c != null && c.moveToFirst()) {
                c.getString(0)
            } else null
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
                adapter.notifyDataSetChanged()
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean = false

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
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                selected.clear()
                adapter.notifyDataSetChanged()
            }
        })
    }

    private fun toggleSelect(uri: String) {
        if (selected.contains(uri)) selected.remove(uri) else selected.add(uri)
        updateSelectionTitle()
        adapter.notifyDataSetChanged()
        if (selected.isEmpty()) {
            actionMode?.finish()
        }
    }

    private fun updateSelectionTitle() {
        actionMode?.title = "已选 ${selected.size}"
    }

    private fun confirmDeleteSelected() {
        val ctx = this
        val uris = selected.toSet()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("删除")
            .setMessage("确定删除已选 ${uris.size} 首导入记录吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.deleteLocalMusic(uris) }
                    refresh()
                    actionMode?.finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addSelectedToQueue() {
        val uris = selected.toList()
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { store.listLocalMusic() }
            val map = list.associateBy { it.uri }
            val tracks = uris.mapNotNull { map[it]?.toTrack() }
            MusicPlayerManager.addToQueue(tracks)
            ToastUtil.show(this@MusicLocalImportActivity, "已加入播放列表")
        }
    }

    private fun addSelectedToPlaylist() {
        val ctx = this
        val uris = selected.toList()
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val pls = withContext(Dispatchers.IO) { store.listPlaylists() }
            val names = ArrayList<String>()
            names.add("新建歌单...")
            names.addAll(pls.map { it.name })
            MaterialAlertDialogBuilder(ctx)
                .setTitle("加入歌单")
                .setItems(names.toTypedArray()) { _, which ->
                    if (which == 0) {
                        val input = android.widget.EditText(ctx)
                        input.hint = "歌单名称"
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("新建歌单")
                            .setView(input)
                            .setPositiveButton("创建") { _, _ ->
                                lifecycleScope.launch {
                                    val pl = withContext(Dispatchers.IO) { store.createPlaylist(input.text?.toString().orEmpty()) }
                                    addUrisToPlaylist(pl.id, uris)
                                    actionMode?.finish()
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    } else {
                        val pid = pls[which - 1].id
                        addUrisToPlaylist(pid, uris)
                        actionMode?.finish()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun addUrisToPlaylist(playlistId: Long, uris: List<String>) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { store.listLocalMusic() }
            val map = list.associateBy { it.uri }
            withContext(Dispatchers.IO) {
                for (u in uris) {
                    val it = map[u] ?: continue
                    store.addTrackToPlaylist(playlistId, it.toTrack())
                }
            }
            ToastUtil.show(this@MusicLocalImportActivity, "已加入歌单")
        }
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
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<LocalMusicAdapter.VH>() {

        private val items = ArrayList<LocalMusicItem>()

        fun submit(list: List<LocalMusicItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val b = ItemLocalMusicBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        inner class VH(private val b: ItemLocalMusicBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
            fun bind(item: LocalMusicItem) {
                b.tvTitle.text = item.name
                b.tvSub.text = item.uri
                val selMode = selectionMode()
                b.cb.visibility = if (selMode) android.view.View.VISIBLE else android.view.View.GONE
                b.cb.isChecked = isSelected(item.uri)
                b.root.setOnClickListener { onClick(item) }
                b.root.setOnLongClickListener {
                    onLongClick(item)
                    true
                }
            }
        }
    }
}
