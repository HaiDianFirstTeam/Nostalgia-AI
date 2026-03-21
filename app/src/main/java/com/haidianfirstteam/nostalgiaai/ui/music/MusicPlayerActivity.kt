package com.haidianfirstteam.nostalgiaai.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityMusicPlayerBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi2Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicSourceType
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicPlayMode
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.lyrics.LrcParser
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import com.haidianfirstteam.nostalgiaai.ui.music.ui.CoverLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlayerActivity : BaseActivity() {

    private lateinit var binding: ActivityMusicPlayerBinding
    private lateinit var store: MusicStore
    private val api1 = MusicApi1Client()
    private val api2 = MusicApi2Client()

    private val handler = Handler(Looper.getMainLooper())
    private var userSeeking: Boolean = false

    // Inline lyrics mode (within player screen)
    private lateinit var inlineLyricsAdapter: LyricsAdapter
    private var inlineLyricsEnabled: Boolean = false
    private var inlineUserScrolling: Boolean = false
    private var inlineLastTrackId: String? = null
    private var inlinePaddingApplied: Boolean = false
    private val tick = object : Runnable {
        override fun run() {
            try {
                val dur = MusicPlayerManager.getDurationMs()
                val pos = MusicPlayerManager.getPositionMs()
                if (dur > 0) {
                    binding.seek.max = dur
                    if (!userSeeking) {
                        binding.seek.progress = pos.coerceIn(0, dur)
                    }
                    binding.tvTime.text = "${fmt(pos)} / ${fmt(dur)}"
                } else {
                    binding.seek.max = 0
                    if (!userSeeking) binding.seek.progress = 0
                    binding.tvTime.text = "00:00 / 00:00"
                }

                // Inline lyrics sync
                if (inlineLyricsEnabled) {
                    val st = MusicPlayerManager.state.value
                    val t = st?.track
                    if (t != null && t.id != inlineLastTrackId) {
                        inlineLastTrackId = t.id
                        loadInlineLyrics()
                    }
                    val idx = inlineLyricsAdapter.setActiveTimeMs(pos)
                    if (!inlineUserScrolling && idx >= 0) {
                        lockInlineActiveLineToCenter(idx)
                    }
                }
            } catch (_: Throwable) {
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        binding.btnPlayPause.setOnClickListener { MusicPlayerManager.togglePause() }
        binding.btnQueue.setOnClickListener { MusicQueueSheet.show(this) }
        binding.btnLyrics.setOnClickListener { startActivity(MusicLyricsActivity.newIntent(this)) }

        // Inline lyrics: tapping cover toggles cover <-> lyrics scroll.
        setupInlineLyricsUi()

        binding.btnPrev.setOnClickListener { MusicPlayerManager.skipPrev() }
        binding.btnNext.setOnClickListener { MusicPlayerManager.skipNext() }

        binding.btnMode.setOnClickListener { showModeDialog() }

        binding.seek.isEnabled = true
        binding.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    userSeeking = true
                    val dur = MusicPlayerManager.getDurationMs()
                    binding.tvTime.text = "${fmt(progress)} / ${fmt(dur)}"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                val p = seekBar?.progress ?: 0
                MusicPlayerManager.seekToMs(p)
            }
        })

        MusicPlayerManager.state.observe(this) { st ->
            val t = st.track
            binding.tvTitle.text = t?.name ?: "未播放"
            binding.tvArtist.text = t?.artists?.joinToString("/") ?: ""
            val icon = if (st.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            binding.btnPlayPause.setImageResource(icon)

            // Reflect mode in title (simple, avoids new UI component)
            val modeTxt = when (MusicPlayerManager.getMode()) {
                MusicPlayMode.SHUFFLE -> "随机"
                MusicPlayMode.LOOP_ONE -> "单曲循环"
                else -> "顺序"
            }
            binding.btnMode.contentDescription = "播放模式：$modeTxt"

            if (t?.coverId.isNullOrBlank()) {
                binding.imgCover.setImageDrawable(null)
            } else {
                lifecycleScope.launch {
                    val coverUrl = withContext(Dispatchers.IO) {
                        try {
                            if (t!!.source == "wyapi") t.coverId else api1.getCoverUrl(t.source.ifBlank { "netease" }, t.coverId!!, 500)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    CoverLoader.loadInto(this, coverUrl, binding.imgCover, circular = false)
                }
            }

            // When track changes while inline lyrics is open, refresh.
            if (inlineLyricsEnabled) {
                inlineLastTrackId = t?.id
                loadInlineLyrics()
            }
        }
    }

    private fun setupInlineLyricsUi() {
        inlineLyricsAdapter = LyricsAdapter { pos, line ->
            try {
                inlineUserScrolling = false
                MusicPlayerManager.seekToMs(line.timeMs.toInt())
                inlineLyricsAdapter.setActiveTimeMs(line.timeMs.toInt())
                lockInlineActiveLineToCenter(pos)
            } catch (_: Throwable) {
            }
        }

        binding.rvInlineLyrics.layoutManager = LinearLayoutManager(this)
        binding.rvInlineLyrics.adapter = inlineLyricsAdapter
        binding.rvInlineLyrics.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                inlineUserScrolling = newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
            }
        })

        // Apply padding after layout so first/last lines can center.
        binding.rvInlineLyrics.post { applyInlineCenterPaddingIfNeeded() }

        binding.coverContainer.setOnClickListener {
            setInlineLyricsEnabled(!inlineLyricsEnabled)
        }

        // Also allow toggling back by tapping on lyrics itself.
        binding.rvInlineLyrics.setOnClickListener {
            setInlineLyricsEnabled(false)
        }
    }

    private fun setInlineLyricsEnabled(enabled: Boolean) {
        inlineLyricsEnabled = enabled
        binding.rvInlineLyrics.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.vInlineFadeTop.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.vInlineFadeBottom.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.imgCover.visibility = if (enabled) android.view.View.INVISIBLE else android.view.View.VISIBLE
        if (enabled) {
            loadInlineLyrics()
        }
    }

    private fun loadInlineLyrics() {
        val track = MusicPlayerManager.state.value?.track
        if (track == null) {
            inlineLyricsAdapter.submit(emptyList())
            return
        }
        inlineLastTrackId = track.id
        lifecycleScope.launch {
            try {
                val pair = withContext(Dispatchers.IO) {
                    val settings = store.getSettings()
                    if (settings.source == MusicSourceType.API2_WYAPI || track.source == "wyapi") {
                        api2.getLyricPair(track.id)
                    } else {
                        api1.getLyric(source = track.source.ifBlank { "netease" }, lyricId = track.id)
                    }
                }
                val lines = LrcParser.parseBilingual(pair.first, pair.second)
                inlineLyricsAdapter.submit(lines)
                // Center current line immediately.
                val idx = inlineLyricsAdapter.setActiveTimeMs(MusicPlayerManager.getPositionMs())
                if (idx >= 0) {
                    lockInlineActiveLineToCenter(idx)
                }
            } catch (_: Throwable) {
                // Keep silent; player screen shouldn't be blocked by lyrics errors.
                inlineLyricsAdapter.submit(emptyList())
            }
        }
    }

    private fun applyInlineCenterPaddingIfNeeded() {
        if (inlinePaddingApplied) return
        val rv = binding.rvInlineLyrics
        if (rv.height <= 0) return
        val left = rv.paddingLeft
        val right = rv.paddingRight

        val desiredCenter = (rv.height / 2).coerceAtLeast(dp(60))
        val estimatedHalfItem = dp(22)
        val pad = (desiredCenter - estimatedHalfItem).coerceAtLeast(dp(12))
        rv.setPadding(left, pad, right, pad)
        inlinePaddingApplied = true
    }

    private fun lockInlineActiveLineToCenter(position: Int) {
        applyInlineCenterPaddingIfNeeded()
        val rv = binding.rvInlineLyrics
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val v = lm.findViewByPosition(position)
        if (v == null) {
            lm.scrollToPositionWithOffset(position, dp(8))
            rv.post { lockInlineActiveLineToCenter(position) }
            return
        }
        val desiredCenter = rv.height / 2
        val viewCenter = (v.top + v.bottom) / 2
        val dy = viewCenter - desiredCenter
        if (kotlin.math.abs(dy) > dp(1)) {
            if (kotlin.math.abs(dy) <= dp(4)) rv.scrollBy(0, dy) else rv.smoothScrollBy(0, dy)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showModeDialog() {
        val items = arrayOf("顺序", "随机", "单曲循环")
        MaterialAlertDialogBuilder(this)
            .setTitle("播放模式")
            .setItems(items) { _, which ->
                val mode = when (which) {
                    1 -> MusicPlayMode.SHUFFLE
                    2 -> MusicPlayMode.LOOP_ONE
                    else -> MusicPlayMode.ORDER
                }
                MusicPlayerManager.setMode(mode)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun fmt(ms: Int): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return String.format("%02d:%02d", m, s)
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, MusicPlayerActivity::class.java)
    }
}
