package com.haidianfirstteam.nostalgiaai.ui.music.player

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    private var mp: MediaPlayer? = null
    private var queue: MutableList<MusicTrack> = mutableListOf()
    private var index: Int = -1
    private var mode: MusicPlayMode = MusicPlayMode.ORDER

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
        val p = mp ?: return false
        return if (p.isPlaying) {
            p.pause()
            post(playing = false)
            false
        } else {
            p.start()
            post(playing = true)
            true
        }
    }

    fun getDurationMs(): Int {
        return try {
            mp?.duration ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    fun getPositionMs(): Int {
        return try {
            mp?.currentPosition ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    fun seekToMs(positionMs: Int) {
        try {
            val d = getDurationMs()
            val p = positionMs.coerceIn(0, d.coerceAtLeast(0))
            mp?.seekTo(p)
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        try {
            mp?.setOnCompletionListener(null)
            mp?.release()
        } catch (_: Throwable) {
        }
        mp = null
        queue.clear()
        index = -1
        post(track = null, playing = false)
    }

    private fun start(context: Context, track: MusicTrack, url: String) {
        try {
            mp?.setOnCompletionListener(null)
            mp?.release()
        } catch (_: Throwable) {
        }
        mp = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOnPreparedListener {
                it.start()
                post(track = track, playing = true)
            }
            setOnCompletionListener {
                onComplete(context)
            }
            setOnErrorListener { _, _, _ ->
                post(track = track, playing = false)
                true
            }
            setDataSource(url)
            prepareAsync()
        }
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
        val isPlaying = playing ?: (mp?.isPlaying == true)
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
