package com.haidianfirstteam.nostalgiaai.ui.translate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityTranslateBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslateActivity : BaseActivity() {
    private lateinit var binding: ActivityTranslateBinding
    private lateinit var store: TranslateStore
    private lateinit var engine: TranslateEngine

    private var settings: TranslateSettings = TranslateSettings()
    private var currentInput: String = ""
    private var currentOutput: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTranslateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val app = application as NostalgiaApp
        store = TranslateStore(app.db)
        engine = TranslateEngine(app.db)

        binding.btnLangA.setOnClickListener { pickLanguage(isA = true) }
        binding.btnLangB.setOnClickListener { pickLanguage(isA = false) }
        binding.btnSwap.setOnClickListener {
            val a = settings.langA
            settings = settings.copy(langA = settings.langB, langB = a)
            persistSettings()
            renderHeader()
        }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }

        binding.toolbar.setOnLongClickListener {
            startActivity(TranslateModelPickerActivity.newIntent(this))
            true
        }

        binding.btnSend.setOnClickListener { send() }

        // Pull-down gesture: when at top and user drags down, show history.
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        val chatAdapter = TranslateChatAdapter()
        binding.rvChat.adapter = chatAdapter
        chatAdapter.submit(currentInput, currentOutput)
        binding.rvChat.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            private var pull = 0
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(-1) && dy < 0) {
                    pull += -dy
                    if (pull > 120) {
                        pull = 0
                        showHistorySheet()
                    }
                } else if (dy > 0) {
                    pull = 0
                }
            }
        })

        lifecycleScope.launch {
            settings = withContext(Dispatchers.IO) { store.getSettings() }
            renderHeader()
        }
    }

    override fun onResume() {
        super.onResume()
        // In case model was changed in picker.
        lifecycleScope.launch {
            settings = withContext(Dispatchers.IO) { store.getSettings() }
            renderHeader()
        }
    }

    private fun renderHeader() {
        binding.btnLangA.text = if (settings.langA == "auto") "自动检测" else settings.langA
        binding.btnLangB.text = settings.langB
        binding.tvMode.text = when (settings.mode) {
            TranslateMode.WORD -> "单词/词组"
            TranslateMode.SENTENCE -> "句子"
            TranslateMode.ARTICLE -> "文章"
        }

        binding.tvModel.text = when (settings.routeType) {
            "group" -> "模型：组(${settings.routeGroupId ?: "?"})"
            "direct" -> "模型：直连(${settings.routeModelId ?: "?"})"
            else -> "模型：自动"
        }
    }

    private fun persistSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
            store.setSettings(settings)
        }
    }

    private fun pickLanguage(isA: Boolean) {
        val langs = arrayOf("auto", "Chinese", "English", "Japanese", "Korean", "French", "German", "Spanish", "Russian")
        val labels = arrayOf("自动检测", "中文", "英语", "日语", "韩语", "法语", "德语", "西班牙语", "俄语")
        val cur = if (isA) settings.langA else settings.langB
        val checked = langs.indexOf(cur).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(if (isA) "选择语言A" else "选择语言B")
            .setSingleChoiceItems(labels, checked) { d, which ->
                settings = if (isA) settings.copy(langA = langs[which]) else settings.copy(langB = langs[which])
                persistSettings()
                renderHeader()
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            "模式：单词/词组",
            "模式：句子",
            "模式：文章",
            if (settings.memoryEnabled) "记忆：开" else "记忆：关",
            "身份设定",
            "选择模型"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("翻译设置")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> settings = settings.copy(mode = TranslateMode.WORD)
                    1 -> settings = settings.copy(mode = TranslateMode.SENTENCE)
                    2 -> settings = settings.copy(mode = TranslateMode.ARTICLE)
                    3 -> settings = settings.copy(memoryEnabled = !settings.memoryEnabled)
                    4 -> showIdentityDialog()
                    5 -> startActivity(TranslateModelPickerActivity.newIntent(this))
                }
                persistSettings()
                renderHeader()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showIdentityDialog() {
        val input = android.widget.EditText(this)
        input.setText(settings.identity)
        input.hint = "例如：学生，中国国内9年级，托福72分..."
        MaterialAlertDialogBuilder(this)
            .setTitle("身份设定")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                settings = settings.copy(identity = input.text?.toString().orEmpty())
                persistSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun send() {
        val text = binding.etInput.text?.toString().orEmpty().trim()
        if (text.isBlank()) return
        binding.etInput.setText("")

        // Default: hide previous conversation (only show current pair)
        currentInput = text
        currentOutput = ""
        (binding.rvChat.adapter as? TranslateChatAdapter)?.submit(currentInput, currentOutput)

        lifecycleScope.launch {
            binding.progress.visibility = View.VISIBLE
            try {
                val history = withContext(Dispatchers.IO) { store.listHistory() }
                val output = withContext(Dispatchers.IO) {
                    engine.translate(settings, text, history)
                }
                currentOutput = output
                (binding.rvChat.adapter as? TranslateChatAdapter)?.submit(currentInput, currentOutput)

                withContext(Dispatchers.IO) {
                    store.addHistory(
                        TranslateHistoryItem(
                            id = System.currentTimeMillis(),
                            createdAt = System.currentTimeMillis(),
                            input = text,
                            output = output,
                            langA = settings.langA,
                            langB = settings.langB,
                            mode = settings.mode
                        )
                    )
                }
            } catch (t: Throwable) {
                MaterialAlertDialogBuilder(this@TranslateActivity)
                    .setTitle("翻译失败")
                    .setMessage(t.message ?: "未知错误")
                    .setPositiveButton("确定", null)
                    .show()
            } finally {
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun showHistorySheet() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(com.haidianfirstteam.nostalgiaai.R.layout.sheet_translate_history, null, false)
        dialog.setContentView(view)

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(com.haidianfirstteam.nostalgiaai.R.id.rvTranslateHistory)
        val btnClear = view.findViewById<android.view.View>(com.haidianfirstteam.nostalgiaai.R.id.btnClearTranslateHistory)

        val adapter = TranslateHistoryAdapter(
            onClick = { item ->
                currentInput = item.input
                currentOutput = item.output
                (binding.rvChat.adapter as? TranslateChatAdapter)?.submit(currentInput, currentOutput)
                dialog.dismiss()
            },
            onDelete = { item ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.deleteHistory(item.id) }
                    val list = withContext(Dispatchers.IO) { store.listHistory() }
                    adapter.submit(list)
                }
            }
        )
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = adapter

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { store.listHistory() }
            adapter.submit(list)
        }

        btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("清空历史")
                .setMessage("确定清空全部翻译历史吗？")
                .setPositiveButton("清空") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.clearHistory() }
                        adapter.submit(emptyList())
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialog.show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, TranslateActivity::class.java)
    }
}
