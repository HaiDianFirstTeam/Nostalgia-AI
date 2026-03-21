package com.haidianfirstteam.nostalgiaai.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialController
import com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialStep
import com.haidianfirstteam.nostalgiaai.ui.music.MusicLocalImportActivity

class MusicMeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = inflater.inflate(com.haidianfirstteam.nostalgiaai.R.layout.fragment_music_me, container, false)

        root.findViewById<android.view.View>(com.haidianfirstteam.nostalgiaai.R.id.btnMusicHistory).setOnClickListener {
            startActivity(MusicPlayHistoryActivity.newIntent(ctx))
        }
        root.findViewById<android.view.View>(com.haidianfirstteam.nostalgiaai.R.id.btnMusicPlaylists).setOnClickListener {
            startActivity(MusicPlaylistsActivity.newIntent(ctx))
        }
        root.findViewById<android.view.View>(com.haidianfirstteam.nostalgiaai.R.id.btnMusicSettings).setOnClickListener {
            startActivity(MusicSettingsActivity.newIntent(ctx))
        }

        root.findViewById<android.view.View>(com.haidianfirstteam.nostalgiaai.R.id.btnMusicLocalImport).setOnClickListener {
            startActivity(MusicLocalImportActivity.newIntent(ctx))
        }

        // Spotlight tutorial for "My" tab (first time only)
        TutorialController.maybeShow(
            requireActivity(),
            "music_me",
            listOf(
                TutorialStep(com.haidianfirstteam.nostalgiaai.R.id.btnMusicHistory, "历史"),
                TutorialStep(com.haidianfirstteam.nostalgiaai.R.id.btnMusicPlaylists, "歌单"),
                TutorialStep(com.haidianfirstteam.nostalgiaai.R.id.btnMusicSettings, "音质"),
                TutorialStep(com.haidianfirstteam.nostalgiaai.R.id.btnMusicLocalImport, "本地/下载管理"),
            )
        )
        return root
    }

    companion object {
        fun newInstance(): MusicMeFragment = MusicMeFragment()
    }
}
