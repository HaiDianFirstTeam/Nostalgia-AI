package com.haidianfirstteam.nostalgiaai.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityMusicPlayerBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi2Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicSourceType
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicPlayMode
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
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
        }
    }

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
