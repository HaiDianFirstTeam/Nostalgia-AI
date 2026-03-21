package com.haidianfirstteam.nostalgiaai.ui.music.player

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.haidianfirstteam.nostalgiaai.net.HttpClients
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack
import com.haidianfirstteam.nostalgiaai.ui.music.data.MusicPlayMode
import kotlin.random.Random

data class NowPlaying(
    val track: MusicTrack?,
    val playing: Boolean,
    val queueSize: Int,
    val index: Int,
)

object MusicPlayerManager {

    private var player: ExoPlayer? = null
    private var queue: MutableList<MusicTrack> = mutableListOf()
    private var index: Int = -1
    private var mode: MusicPlayMode = MusicPlayMode.ORDER

    // Playback speed (applied on supported API levels)
    @Volatile
    private var speed: Float = 1.0f

    fun getSpeed(): Float = speed

    fun setSpeed(v: Float) {
        val s = v.coerceIn(0.1f, 5.0f)
        speed = s
        val p = player
        if (p != null) {
            try {
                p.playbackParameters = PlaybackParameters(s)
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Provide a resolver for auto-next playback. Called when the current track completes and
     * we decide the next index. Resolver should fetch URL then call playAt(..., url).
     */
    @Volatile
    var autoPlayResolver: ((nextIndex: Int, track: MusicTrack) -> Unit)? = null

    private val _state = MutableLiveData(NowPlaying(track = null, playing = false, queueSize = 0, index = -1))
    val state: LiveData<NowPlaying> = _state

    fun setMode(m: MusicPlayMode) {
        mode = m
    }

    fun getMode(): MusicPlayMode = mode

    fun setQueue(list: List<MusicTrack>, startIndex: Int = 0) {
        queue = list.toMutableList()
        index = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        post()
    }

    fun getQueue(): List<MusicTrack> = queue.toList()

    fun addToQueue(tracks: List<MusicTrack>) {
        if (tracks.isEmpty()) return
        val wasEmpty = queue.isEmpty()
        queue.addAll(tracks)
        if (wasEmpty) {
            // Keep index invalid until a play URL is resolved.
            index = 0
        } else {
            if (index !in queue.indices) index = 0
        }
        post()
    }

    fun getIndex(): Int = index

    fun moveQueueItem(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices) return
        val item = queue.removeAt(from)
        queue.add(to, item)
        if (index == from) index = to
        post()
    }

    fun removeQueueItem(pos: Int) {
        if (pos !in queue.indices) return
        queue.removeAt(pos)
        if (queue.isEmpty()) {
            stop()
            return
        }
        if (index >= queue.size) index = queue.size - 1
        post()
    }

    fun playSingle(context: Context, track: MusicTrack, url: String) {
        setQueue(listOf(track), 0)
        playAt(context, 0, url)
    }

    fun playAt(context: Context, newIndex: Int, url: String) {
        if (newIndex !in queue.indices) return
        index = newIndex
        start(context, queue[index], url)
    }

    fun skipNext() {
        if (queue.isEmpty() || index !in queue.indices) return
        val next = when (mode) {
            MusicPlayMode.SHUFFLE -> if (queue.size == 1) index else Random.nextInt(queue.size)
            MusicPlayMode.LOOP_ONE -> index
            MusicPlayMode.ORDER -> (index + 1).coerceAtMost(queue.size - 1)
        }
        index = next
        post(track = currentTrack(), playing = false)
        val t = currentTrack() ?: return
        autoPlayResolver?.invoke(index, t)
    }

    fun skipPrev() {
        if (queue.isEmpty() || index !in queue.indices) return
        val prev = when (mode) {
            MusicPlayMode.SHUFFLE -> if (queue.size == 1) index else Random.nextInt(queue.size)
            MusicPlayMode.LOOP_ONE -> index
            MusicPlayMode.ORDER -> (index - 1).coerceAtLeast(0)
        }
        index = prev
        post(track = currentTrack(), playing = false)
        val t = currentTrack() ?: return
        autoPlayResolver?.invoke(index, t)
    }

    fun togglePause(): Boolean {
        val p = player ?: return false
        return if (p.isPlaying) {
            p.pause()
            post(playing = false)
            false
        } else {
            p.play()
            post(playing = true)
            true
        }
    }

    fun getDurationMs(): Int {
        return try {
            val d = player?.duration ?: 0L
            if (d <= 0 || d == com.google.android.exoplayer2.C.TIME_UNSET) 0
            else d.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } catch (_: Throwable) {
            0
        }
    }

    fun getPositionMs(): Int {
        return try {
            val p = player?.currentPosition ?: 0L
            if (p <= 0) 0 else p.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } catch (_: Throwable) {
            0
        }
    }

    fun seekToMs(positionMs: Int) {
        try {
            val d = getDurationMs()
            val p = positionMs.coerceIn(0, d.coerceAtLeast(0))
            player?.seekTo(p.toLong())
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        try {
            player?.release()
        } catch (_: Throwable) {
        }
        player = null
        queue.clear()
        index = -1
        post(track = null, playing = false)
    }

    private fun start(context: Context, track: MusicTrack, url: String) {
        try {
            player?.release()
        } catch (_: Throwable) {
        }

        val appCtx = context.applicationContext
        // IMPORTANT: Use OkHttp data source to ensure Conscrypt-enabled TLS works on API 19.
        val httpFactory = OkHttpDataSource.Factory(HttpClients.music())
            .setUserAgent("Nostalgia-AI")
        val dataSourceFactory = DefaultDataSource.Factory(appCtx, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val p = ExoPlayer.Builder(appCtx)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        p.setHandleAudioBecomingNoisy(true)

        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> post(track = track, playing = p.isPlaying)
                    Player.STATE_ENDED -> {
                        post(track = track, playing = false)
                        onComplete(appCtx)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                post(track = track, playing = isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                post(track = track, playing = false)
            }
        })

        p.setMediaItem(com.google.android.exoplayer2.MediaItem.fromUri(url))
        p.prepare()
        try {
            p.playbackParameters = PlaybackParameters(speed)
        } catch (_: Throwable) {
        }
        p.playWhenReady = true

        player = p
        post(track = track, playing = false)
    }

    private fun onComplete(context: Context) {
        if (queue.isEmpty() || index !in queue.indices) {
            post(track = null, playing = false)
            return
        }
        when (mode) {
            MusicPlayMode.LOOP_ONE -> {
                post(playing = false)
                val t = queue[index]
                autoPlayResolver?.invoke(index, t)
            }
            MusicPlayMode.SHUFFLE -> {
                if (queue.size == 1) {
                    post(playing = false)
                } else {
                    val next = Random.nextInt(queue.size)
                    index = next
                    post(track = queue[index], playing = false)
                    autoPlayResolver?.invoke(index, queue[index])
                }
            }
            MusicPlayMode.ORDER -> {
                val next = index + 1
                if (next >= queue.size) {
                    post(playing = false)
                } else {
                    index = next
                    post(track = queue[index], playing = false)
                    autoPlayResolver?.invoke(index, queue[index])
                }
            }
        }
    }

    private fun post(track: MusicTrack? = currentTrack(), playing: Boolean? = null) {
        val isPlaying = playing ?: (player?.isPlaying == true)
        _state.postValue(
            NowPlaying(
                track = track,
                playing = isPlaying,
                queueSize = queue.size,
                index = index,
            )
        )
    }

    private fun currentTrack(): MusicTrack? {
        return if (index in queue.indices) queue[index] else null
    }
}
