package com.haidianfirstteam.nostalgiaai.ui.tutorial

import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.ui.MainActivity
import com.haidianfirstteam.nostalgiaai.ui.music.MusicActivity
import com.haidianfirstteam.nostalgiaai.ui.music.MusicLocalImportActivity
import com.haidianfirstteam.nostalgiaai.ui.music.MusicLyricsActivity
import com.haidianfirstteam.nostalgiaai.ui.music.MusicPlayerActivity
import com.haidianfirstteam.nostalgiaai.ui.settings.*
import com.haidianfirstteam.nostalgiaai.ui.toolbox.ToolboxActivity
import com.haidianfirstteam.nostalgiaai.ui.translate.TranslateActivity
import com.haidianfirstteam.nostalgiaai.ui.translate.TranslateModelPickerActivity

/**
 * Per-screen tutorial steps.
 * Goal: spotlight every visible actionable control on that screen.
 */
object TutorialRegistry {
    fun stepsFor(activity: android.app.Activity): Pair<String, List<TutorialStep>>? {
        return when (activity) {
            is MainActivity -> "main" to listOf(
                // Drawer / navigation
                // Open drawer when describing drawer controls.
                TutorialStep(R.id.btnNewChat, "新对话", prepare = { a -> (a as? MainActivity)?.openDrawerForTutorial() }),
                TutorialStep(R.id.btnToolbox, "百宝箱"),
                TutorialStep(R.id.btnSettingsTop, "设置"),
                TutorialStep(R.id.rvConversations, "历史对话"),

                // Chat page controls (inside ChatFragment)
                // Close drawer before spotlighting chat.
                TutorialStep(R.id.btnTarget, "模型/组", prepare = { a -> (a as? MainActivity)?.closeDrawerForTutorial() }),
                TutorialStep(R.id.btnWebSearch, "联网"),
                TutorialStep(R.id.btnUpload, "上传"),
                TutorialStep(R.id.btnCamera, "相机"),
                TutorialStep(R.id.btnSend, "发送/停止"),

                // Message actions (if any message is visible)
                TutorialStep(text = "消息-复制", finder = TutorialFinders.recyclerChildViewById(R.id.rvMessages, 0, R.id.btnCopy)),
                TutorialStep(text = "消息-编辑重发", finder = TutorialFinders.recyclerChildViewById(R.id.rvMessages, 0, R.id.btnEditResend)),
                TutorialStep(text = "消息-删除", finder = TutorialFinders.recyclerChildViewById(R.id.rvMessages, 0, R.id.btnDelete)),
                TutorialStep(text = "消息-重试", finder = TutorialFinders.recyclerChildViewById(R.id.rvMessages, 0, R.id.btnRetry)),
                TutorialStep(text = "AI-编辑", finder = TutorialFinders.recyclerChildViewById(R.id.rvMessages, 0, R.id.btnEdit)),
                TutorialStep(text = "AI-展开", finder = TutorialFinders.recyclerChildViewById(R.id.rvMessages, 0, R.id.btnExpandChips)),
                TutorialStep(text = "分支-上一个", finder = TutorialFinders.recyclerChildViewById(R.id.rvMessages, 0, R.id.btnBranchPrev)),
                TutorialStep(text = "分支-下一个", finder = TutorialFinders.recyclerChildViewById(R.id.rvMessages, 0, R.id.btnBranchNext)),
            )

            is ToolboxActivity -> "toolbox" to listOf(
                TutorialStep(text = "音乐解析", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(text = "翻译助手", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 1)),
            )

            is MusicActivity -> "music" to listOf(
                TutorialStep(text = "主页", finder = TutorialFinders.bottomNavItem(R.id.bottomNav, R.id.nav_music_home)),
                TutorialStep(text = "我的", finder = TutorialFinders.bottomNavItem(R.id.bottomNav, R.id.nav_music_me)),

                // Home tab controls
                TutorialStep(R.id.btnSearch, "搜索按钮"),
                TutorialStep(R.id.btnClearHistory, "清空历史"),
                TutorialStep(text = "历史-删除", finder = TutorialFinders.recyclerChildViewById(R.id.rvHistory, 0, R.id.btnDelete)),
                TutorialStep(text = "结果-播放", finder = TutorialFinders.recyclerChildViewById(R.id.rvTracks, 0, R.id.btnPlay)),
                TutorialStep(text = "结果-下载", finder = TutorialFinders.recyclerChildViewById(R.id.rvTracks, 0, R.id.btnDownload)),
                TutorialStep(text = "结果-更多", finder = TutorialFinders.recyclerChildViewById(R.id.rvTracks, 0, R.id.btnMore)),

                // Player bar
                TutorialStep(R.id.btnPlayPause, "播放/暂停"),
                TutorialStep(R.id.btnQueue, "队列"),
            )

            is MusicPlayerActivity -> "music_player" to listOf(
                TutorialStep(R.id.coverContainer, "封面/歌词区"),
                TutorialStep(R.id.btnBackCover, "返回封面", prepare = { a ->
                    try {
                        val v = a.window.decorView.findViewById<android.view.View>(R.id.coverContainer)
                        // Enter lyrics mode so this button becomes visible.
                        v?.performClick()
                    } catch (_: Throwable) {}
                }),
                TutorialStep(R.id.btnSpeed, "倍速"),
                TutorialStep(R.id.seek, "进度条"),
                TutorialStep(R.id.btnMode, "模式"),
                TutorialStep(R.id.btnPrev, "上一首"),
                TutorialStep(R.id.btnPlayPause, "播放/暂停"),
                TutorialStep(R.id.btnNext, "下一首"),
                TutorialStep(R.id.btnQueue, "队列"),
            )

            is MusicLyricsActivity -> "music_lyrics" to listOf(
                TutorialStep(R.id.rvLyrics, "点击歌词跳转"),
            )

            is MusicLocalImportActivity -> "music_local" to listOf(
                TutorialStep(text = "长按进入多选", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "导入"),
                TutorialStep(text = "多选：删/歌单/队列"),
            )

            is TranslateActivity -> "translate" to listOf(
                TutorialStep(R.id.btnLangA, "语言A"),
                TutorialStep(R.id.btnSwap, "交换"),
                TutorialStep(R.id.btnLangB, "语言B"),
                TutorialStep(R.id.btnSettings, "设置"),
                TutorialStep(R.id.rvChat, "下拉历史"),
                TutorialStep(R.id.btnSend, "发送"),
                TutorialStep(text = "记忆：开=多轮"),
                TutorialStep(text = "记忆：带上下文"),
                TutorialStep(text = "记忆：右上角 +"),
                TutorialStep(text = "记忆：指代A/a"),
            )

            is TranslateModelPickerActivity -> "translate_model" to listOf(
                // Button-only: keep example item as actionable tap
                TutorialStep(text = "点一条选择", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )

            // Settings (Preference screen): spotlight every preference entry
            // No tutorial on main Settings page
            is SettingsActivity -> null

            // Generic list+fab screens: cover every visible control
            // Model/Tavily settings: show once only (shared keys)
            is ProvidersActivity -> "models_settings_once" to listOf(
                TutorialStep(text = "点一条编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "新增")
            )
            is ProviderDetailActivity -> "models_settings_once" to listOf(
                TutorialStep(text = "点一项编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )
            is ModelsActivity -> "models_settings_once" to listOf(
                TutorialStep(text = "点一条编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "新增")
            )
            is ModelGroupsActivity -> "models_settings_once" to listOf(
                TutorialStep(text = "点一条详情", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "新建")
            )
            is ModelGroupDetailActivity -> "models_settings_once" to listOf(
                TutorialStep(text = "点一项编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )
            is TavilyActivity -> "tavily_settings_once" to listOf(
                TutorialStep(text = "点一项编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )
            is ImportExportActivity -> "import_export" to listOf(
                TutorialStep(text = "点一项执行", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )
            is AboutActivity -> null

            // Music management screens
            is com.haidianfirstteam.nostalgiaai.ui.music.MusicSettingsActivity -> null
            is com.haidianfirstteam.nostalgiaai.ui.music.MusicPlaylistsActivity -> "music_playlists" to listOf(
                TutorialStep(text = "点一条进入", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "新建")
            )
            is com.haidianfirstteam.nostalgiaai.ui.music.MusicPlaylistDetailActivity -> "music_playlist_detail" to listOf(
                TutorialStep(text = "歌曲-播放", finder = TutorialFinders.recyclerChildViewById(R.id.recycler, 0, R.id.btnPlay)),
                TutorialStep(text = "歌曲-下载", finder = TutorialFinders.recyclerChildViewById(R.id.recycler, 0, R.id.btnDownload)),
                TutorialStep(text = "歌曲-更多", finder = TutorialFinders.recyclerChildViewById(R.id.recycler, 0, R.id.btnMore)),
                TutorialStep(R.id.fab, "操作")
            )
            is com.haidianfirstteam.nostalgiaai.ui.music.MusicPlayHistoryActivity -> "music_play_history" to listOf(
                TutorialStep(text = "历史-删除", finder = TutorialFinders.recyclerChildViewById(R.id.recycler, 0, R.id.btnDelete)),
                TutorialStep(R.id.fab, "清空")
            )

            else -> null
        }
    }
}
