package com.haidianfirstteam.nostalgiaai.ui.music

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.haidianfirstteam.nostalgiaai.ui.music.api.MusicTrack

object MusicDownloader {
    fun enqueue(context: Context, track: MusicTrack, url: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val title = sanitize(track.name)
        val artist = sanitize(track.artists.joinToString("-"))
        val fileName = ("${title}-${artist}").take(60).ifBlank { "music" } + ".mp3"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.name)
            .setDescription(artist)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)

        try {
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        } catch (_: Throwable) {
            // ignore
        }
        dm.enqueue(req)
    }

    private fun sanitize(s: String): String {
        return s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
