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
                TutorialStep(R.id.toolbar, "顶部栏"),
                TutorialStep(R.id.btnNewChat, "新对话"),
                TutorialStep(R.id.btnSettings, "设置"),
                TutorialStep(R.id.btnToolbox, "百宝箱"),
                TutorialStep(R.id.rvConversations, "历史对话"),

                // Chat page controls (inside ChatFragment)
                TutorialStep(R.id.btnTarget, "模型/组"),
                TutorialStep(R.id.btnWebSearch, "联网"),
                TutorialStep(R.id.rvMessages, "消息列表"),
                TutorialStep(R.id.btnUpload, "上传"),
                TutorialStep(R.id.btnCamera, "相机"),
                TutorialStep(R.id.etInput, "输入"),
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
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "功能列表"),
                TutorialStep(text = "音乐解析", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(text = "本地导入", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 1)),
                TutorialStep(text = "翻译助手", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 2)),
            )

            is MusicActivity -> "music" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(text = "主页", finder = TutorialFinders.bottomNavItem(R.id.bottomNav, R.id.nav_music_home)),
                TutorialStep(text = "我的", finder = TutorialFinders.bottomNavItem(R.id.bottomNav, R.id.nav_music_me)),

                // Home tab controls
                TutorialStep(R.id.etSearch, "搜索", prepare = { a ->
                    try {
                        val v = a.window.decorView.findViewById<android.widget.EditText>(R.id.etSearch)
                        v?.requestFocus()
                    } catch (_: Throwable) {}
                }),
                TutorialStep(R.id.btnSearch, "搜索按钮"),
                TutorialStep(R.id.historyPanel, "搜索历史"),
                TutorialStep(R.id.btnClearHistory, "清空历史"),
                TutorialStep(R.id.rvHistory, "历史列表"),
                TutorialStep(text = "历史-删除", finder = TutorialFinders.recyclerChildViewById(R.id.rvHistory, 0, R.id.btnDelete)),
                TutorialStep(R.id.rvTracks, "结果列表"),
                TutorialStep(text = "结果-播放", finder = TutorialFinders.recyclerChildViewById(R.id.rvTracks, 0, R.id.btnPlay)),
                TutorialStep(text = "结果-下载", finder = TutorialFinders.recyclerChildViewById(R.id.rvTracks, 0, R.id.btnDownload)),
                TutorialStep(text = "结果-更多", finder = TutorialFinders.recyclerChildViewById(R.id.rvTracks, 0, R.id.btnMore)),

                // Player bar
                TutorialStep(R.id.playerBar, "播放条"),
                TutorialStep(R.id.imgCover, "封面"),
                TutorialStep(R.id.tvNowPlaying, "正在播放"),
                TutorialStep(R.id.btnPlayPause, "播放/暂停"),
                TutorialStep(R.id.btnQueue, "队列"),
            )

            is MusicPlayerActivity -> "music_player" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
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
                TutorialStep(R.id.btnLyrics, "全屏歌词"),
            )

            is MusicLyricsActivity -> "music_lyrics" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.rvLyrics, "点击歌词可跳转"),
            )

            is MusicLocalImportActivity -> "music_local" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "本地列表"),
                TutorialStep(text = "长按进入多选", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "导入"),
                TutorialStep(text = "多选后可删除/加入歌单/加入队列"),
            )

            is TranslateActivity -> "translate" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.btnLangA, "语言A"),
                TutorialStep(R.id.btnSwap, "交换"),
                TutorialStep(R.id.btnLangB, "语言B"),
                TutorialStep(R.id.btnSettings, "设置"),
                TutorialStep(R.id.rvChat, "下拉出历史"),
                TutorialStep(R.id.etInput, "输入"),
                TutorialStep(R.id.btnSend, "发送"),
                TutorialStep(text = "记忆：保留上下文/指代"),
            )

            is TranslateModelPickerActivity -> "translate_model" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "模型列表"),
                TutorialStep(text = "点一条选择", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )

            // Settings (Preference screen): spotlight every preference entry
            is SettingsActivity -> "settings" to listOf(
                TutorialStep(text = "深浅色", finder = TutorialFinders.preferenceByTitle("深浅色模式")),
                TutorialStep(text = "字体缩放", finder = TutorialFinders.preferenceByTitle("字体缩放")),
                TutorialStep(text = "模型", finder = TutorialFinders.preferenceByTitle("模型")),
                TutorialStep(text = "Tavily", finder = TutorialFinders.preferenceByTitle("Tavily 联网搜索")),
                TutorialStep(text = "流式输出", finder = TutorialFinders.preferenceByTitle("流式输出")),
                TutorialStep(text = "兼容刷新间隔", finder = TutorialFinders.preferenceByTitle("兼容流式刷新间隔(ms)")),
                TutorialStep(text = "导入/导出", finder = TutorialFinders.preferenceByTitle("导入 / 导出（JSON）")),
                TutorialStep(text = "重新体验教程", finder = TutorialFinders.preferenceByTitle("重新体验教程")),
                TutorialStep(text = "关于", finder = TutorialFinders.preferenceByTitle("关于")),
            )

            // Generic list+fab screens: cover every visible control
            is ProvidersActivity -> "providers" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "列表"),
                TutorialStep(text = "点一条编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "新增")
            )
            is ProviderDetailActivity -> "provider_detail" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "配置项"),
                TutorialStep(text = "点一项编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )
            is ModelsActivity -> "models" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "模型列表"),
                TutorialStep(text = "点一条编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "新增")
            )
            is ModelGroupsActivity -> "model_groups" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "模型组列表"),
                TutorialStep(text = "点一条详情", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "新建")
            )
            is ModelGroupDetailActivity -> "model_group_detail" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "组详情"),
                TutorialStep(text = "点一项编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )
            is TavilyActivity -> "tavily" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "配置列表"),
                TutorialStep(text = "点一项编辑", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )
            is ImportExportActivity -> "import_export" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "操作列表"),
                TutorialStep(text = "点一项执行", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
            )
            is AboutActivity -> "about" to listOf(
                TutorialStep(R.id.toolbar, "返回：回到设置。"),
            )

            // Music management screens
            is com.haidianfirstteam.nostalgiaai.ui.music.MusicSettingsActivity -> "music_settings" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "音质设置")
            )
            is com.haidianfirstteam.nostalgiaai.ui.music.MusicPlaylistsActivity -> "music_playlists" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "歌单列表"),
                TutorialStep(text = "点一条进入", finder = TutorialFinders.recyclerChildAt(R.id.recycler, 0)),
                TutorialStep(R.id.fab, "新建")
            )
            is com.haidianfirstteam.nostalgiaai.ui.music.MusicPlaylistDetailActivity -> "music_playlist_detail" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "歌曲列表"),
                TutorialStep(text = "歌曲-播放", finder = TutorialFinders.recyclerChildViewById(R.id.recycler, 0, R.id.btnPlay)),
                TutorialStep(text = "歌曲-下载", finder = TutorialFinders.recyclerChildViewById(R.id.recycler, 0, R.id.btnDownload)),
                TutorialStep(text = "歌曲-更多", finder = TutorialFinders.recyclerChildViewById(R.id.recycler, 0, R.id.btnMore)),
                TutorialStep(R.id.fab, "操作")
            )
            is com.haidianfirstteam.nostalgiaai.ui.music.MusicPlayHistoryActivity -> "music_play_history" to listOf(
                TutorialStep(R.id.toolbar, "返回"),
                TutorialStep(R.id.recycler, "历史列表"),
                TutorialStep(text = "历史-删除", finder = TutorialFinders.recyclerChildViewById(R.id.recycler, 0, R.id.btnDelete)),
                TutorialStep(R.id.fab, "清空")
            )

            else -> null
        }
    }
}
