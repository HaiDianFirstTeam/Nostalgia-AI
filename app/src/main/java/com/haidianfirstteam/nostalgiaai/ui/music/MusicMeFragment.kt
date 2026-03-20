package com.haidianfirstteam.nostalgiaai.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

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
        return root
    }

    companion object {
        fun newInstance(): MusicMeFragment = MusicMeFragment()
    }
}
