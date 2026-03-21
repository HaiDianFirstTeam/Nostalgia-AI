package com.haidianfirstteam.nostalgiaai.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityMusicLyricsBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.lyrics.LrcParser
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import com.haidianfirstteam.nostalgiaai.ui.music.ui.CoverLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicLyricsActivity : BaseActivity() {
    private lateinit var binding: ActivityMusicLyricsBinding
    private val api1 = MusicApi1Client()
    private lateinit var store: MusicStore
    private lateinit var adapter: LyricsAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var userScrolling: Boolean = false
    private var lastTrackId: String? = null
    private var isCenterPaddingApplied: Boolean = false

    private val tick = object : Runnable {
        override fun run() {
            try {
                val st = MusicPlayerManager.state.value
                val t = st?.track
                if (t != null && t.id != lastTrackId) {
                    lastTrackId = t.id
                    loadLyrics()
                }
                val pos = MusicPlayerManager.getPositionMs()
                val idx = adapter.setActiveTimeMs(pos)
                if (!userScrolling && idx >= 0) {
                    lockActiveLineToCenter(idx)
                }
            } catch (_: Throwable) {
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicLyricsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = "歌词"

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        adapter = LyricsAdapter { pos, line ->
            // Seek to the clicked lyric timestamp.
            try {
                userScrolling = false
                MusicPlayerManager.seekToMs(line.timeMs.toInt())
                adapter.setActiveTimeMs(line.timeMs.toInt())
                lockActiveLineToCenter(pos)
            } catch (_: Throwable) {
            }
        }
        val lm = LinearLayoutManager(this)
        binding.rvLyrics.layoutManager = lm
        binding.rvLyrics.adapter = adapter

        // Apply generous paddings so the first/last lines can also be centered.
        binding.rvLyrics.post { applyCenterLockPaddingIfNeeded() }

        binding.rvLyrics.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                userScrolling = newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
            }
        })

        loadLyrics()
    }

    private fun applyCenterLockPaddingIfNeeded() {
        if (isCenterPaddingApplied) return
        val rv = binding.rvLyrics
        if (rv.height <= 0) return
        val left = rv.paddingLeft
        val right = rv.paddingRight

        // Target anchor: between header bottom and screen bottom (lyrics viewport).
        val desiredCenter = ((binding.header.height + rv.height) / 2).coerceAtLeast(dp(120))
        // Estimate half item height (bilingual rows are taller).
        val estimatedHalfItem = dp(34)
        val pad = (desiredCenter - estimatedHalfItem).coerceAtLeast(binding.header.height + dp(12))
        rv.setPadding(left, pad, right, pad)
        isCenterPaddingApplied = true
    }

    /**
     * Keep the active line strictly pinned to the center anchor.
     * Uses a delta-based correction so variable row heights still center correctly.
     */
    private fun lockActiveLineToCenter(position: Int) {
        applyCenterLockPaddingIfNeeded()

        val rv = binding.rvLyrics
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val v = lm.findViewByPosition(position)
        if (v == null) {
            // If the target view isn't laid out yet, jump close first.
            lm.scrollToPositionWithOffset(position, dp(8))
            rv.post { lockActiveLineToCenter(position) }
            return
        }
        val desiredCenter = (binding.header.height + rv.height) / 2
        val viewCenter = (v.top + v.bottom) / 2
        val dy = viewCenter - desiredCenter
        if (kotlin.math.abs(dy) > dp(1)) {
            if (kotlin.math.abs(dy) <= dp(4)) {
                rv.scrollBy(0, dy)
            } else {
                rv.smoothScrollBy(0, dy)
            }
        }
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    private fun loadLyrics() {
        val track = MusicPlayerManager.state.value?.track
        if (track == null) {
            adapter.submit(emptyList())
            return
        }
        lastTrackId = track.id

        binding.tvTitle.text = track.name
        binding.tvArtist.text = track.artists.joinToString("/")

        // Background cover
        if (!track.coverId.isNullOrBlank()) {
            lifecycleScope.launch {
                val coverUrl = withContext(Dispatchers.IO) {
                    try {
                        api1.getCoverUrl(track.source.ifBlank { "netease" }, track.coverId!!, 500)
                    } catch (_: Throwable) {
                        null
                    }
                }
                CoverLoader.loadInto(this, coverUrl, binding.imgBg, circular = false)
            }
        } else {
            binding.imgBg.setImageDrawable(null)
        }
        lifecycleScope.launch {
            binding.progress.visibility = android.view.View.VISIBLE
            try {
                val pair = withContext(Dispatchers.IO) {
                    // API1 uses lyric_id == track_id typically.
                    api1.getLyric(source = track.source.ifBlank { "netease" }, lyricId = track.id)
                }
                val lines = LrcParser.parseBilingual(pair.first, pair.second)
                adapter.submit(lines)
            } catch (t: Throwable) {
                MaterialAlertDialogBuilder(this@MusicLyricsActivity)
                    .setTitle("获取歌词失败")
                    .setMessage(t.message ?: "未知错误")
                    .setPositiveButton("确定", null)
                    .show()
            } finally {
                binding.progress.visibility = android.view.View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, MusicLyricsActivity::class.java)
    }
}
