package com.haidianfirstteam.nostalgiaai.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.databinding.ActivityMusicBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi1Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicApi2Client
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicSourceType
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicStore
import com.haidianfirstteam.nostalgiaai.ui.music.player.MusicPlayerManager
import com.haidianfirstteam.nostalgiaai.ui.music.ui.CoverLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v0.4: Music toolbox entry.
 * NOTE: This is a placeholder shell; feature will be filled in subsequent commits.
 */
class MusicActivity : BaseActivity() {
    private lateinit var binding: ActivityMusicBinding

    private lateinit var store: MusicStore
    private val api1 = MusicApi1Client()
    private val api2 = MusicApi2Client()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val app = application as NostalgiaApp
        store = MusicStore(app.db)

        binding.bottomNav.setOnItemSelectedListener(onBottomNav)
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_music_home
            switchTo(MusicHomeFragment.newInstance())
        }

        // Player bar is wired in follow-up step.
        binding.btnPlayPause.setOnClickListener {
            MusicPlayerManager.togglePause()
        }
        binding.btnQueue.setOnClickListener {
            MusicQueueSheet.show(this)
        }

        MusicPlayerManager.state.observe(this) { st ->
            val t = st.track
            binding.tvNowPlaying.text = t?.name ?: "未播放"
            val icon = if (st.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            binding.btnPlayPause.setImageResource(icon)

            // Cover
            if (t?.coverId.isNullOrBlank()) {
                binding.imgCover.setImageDrawable(null)
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    val coverUrl = withContext(Dispatchers.IO) {
                        try {
                            if (t!!.source == "wyapi") {
                                t.coverId
                            } else {
                                api1.getCoverUrl(source = t.source.ifBlank { "netease" }, picId = t.coverId!!, size = 300)
                            }
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    CoverLoader.loadInto(this, coverUrl, binding.imgCover)
                }
            }
        }

        binding.playerBar.setOnClickListener {
            startActivity(MusicPlayerActivity.newIntent(this))
        }

        // Auto-next resolver
        MusicPlayerManager.autoPlayResolver = { idx, track ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val url = withContext(Dispatchers.IO) {
                        val s = store.getSettings()
                        if (s.source == MusicSourceType.API2_WYAPI || track.source == "wyapi") {
                            val level = s.quality.streamLevel ?: "standard"
                            api2.getPlayUrl(track.id, level).url
                        } else {
                            val br = s.quality.streamBr ?: 320
                            api1.getPlayUrl(source = track.source.ifBlank { "netease" }, trackId = track.id, br = br).url
                        }
                    }
                    MusicPlayerManager.playAt(applicationContext, idx, url)
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
    }

    override fun onDestroy() {
        if (MusicPlayerManager.autoPlayResolver != null) {
            MusicPlayerManager.autoPlayResolver = null
        }
        super.onDestroy()
    }

    private val onBottomNav = BottomNavigationView.OnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_music_home -> {
                switchTo(MusicHomeFragment.newInstance())
                true
            }
            R.id.nav_music_me -> {
                switchTo(MusicMeFragment.newInstance())
                true
            }
            else -> false
        }
    }

    private fun switchTo(f: androidx.fragment.app.Fragment) {
        supportFragmentManager.commit {
            replace(R.id.container, f)
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, MusicActivity::class.java)
    }
}
