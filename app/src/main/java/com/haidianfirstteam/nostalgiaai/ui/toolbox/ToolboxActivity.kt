package com.haidianfirstteam.nostalgiaai.ui.toolbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.databinding.ActivityListBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineAdapter
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem
import com.haidianfirstteam.nostalgiaai.ui.music.MusicActivity
import com.haidianfirstteam.nostalgiaai.ui.music.MusicLocalImportActivity
import com.haidianfirstteam.nostalgiaai.ui.translate.TranslateActivity

class ToolboxActivity : BaseActivity() {

    private lateinit var binding: ActivityListBinding
    private lateinit var adapter: TwoLineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        title = getString(R.string.nav_toolbox)

        adapter = TwoLineAdapter(
            onClick = { item ->
                when (item.id) {
                    1L -> startActivity(MusicActivity.newIntent(this))
                    2L -> startActivity(TranslateActivity.newIntent(this))
                    3L -> startActivity(MusicLocalImportActivity.newIntent(this))
                }
            },
            onLongClick = { _ -> }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        adapter.submit(
            listOf(
                TwoLineItem(1L, "音乐解析", "搜索/播放/下载/歌单"),
                TwoLineItem(3L, "本地歌曲导入", "导入本地音频文件/文件夹，加入歌单或播放列表"),
                TwoLineItem(2L, "翻译助手", "附带提示词的快捷翻译（支持记忆模式）")
            )
        )
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ToolboxActivity::class.java)
    }
}
