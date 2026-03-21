package com.haidianfirstteam.nostalgiaai.ui.translate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.app.Dialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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

    private lateinit var singleAdapter: TranslateChatAdapter
    private lateinit var convAdapter: TranslateConversationAdapter
    private var activeConversationId: Long? = null

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

        // Pull-down gesture: a fast downward swipe at the top opens history drawer.
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        singleAdapter = TranslateChatAdapter()
        convAdapter = TranslateConversationAdapter()
        binding.rvChat.adapter = singleAdapter
        singleAdapter.submit(currentInput, currentOutput)
        installPullDownToHistoryGesture()

        lifecycleScope.launch {
            settings = withContext(Dispatchers.IO) { store.getSettings() }
            renderHeader()
            applyMemoryModeUi()
        }
    }

    override fun onResume() {
        super.onResume()
        // In case model was changed in picker.
        lifecycleScope.launch {
            settings = withContext(Dispatchers.IO) { store.getSettings() }
            renderHeader()
            applyMemoryModeUi()
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

        // Toolbar menu: new conversation (only for memory mode)
        try {
            if (binding.toolbar.menu.size() == 0) {
                binding.toolbar.inflateMenu(com.haidianfirstteam.nostalgiaai.R.menu.menu_translate)
                binding.toolbar.setOnMenuItemClickListener { item ->
                    if (item.itemId == com.haidianfirstteam.nostalgiaai.R.id.action_new_conversation) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { store.newConversation() }
                            applyMemoryModeUi()
                        }
                        true
                    } else false
                }
            }
            binding.toolbar.menu.findItem(com.haidianfirstteam.nostalgiaai.R.id.action_new_conversation)?.isVisible = settings.memoryEnabled
        } catch (_: Throwable) {
        }
    }

    private suspend fun loadActiveConversationIfNeeded() {
        if (!settings.memoryEnabled) return
        val id = withContext(Dispatchers.IO) { store.ensureActiveConversationId() }
        activeConversationId = id
        var turns = withContext(Dispatchers.IO) { store.getConversation(id)?.turns ?: emptyList() }

        // If user turns on memory mode after doing a single translation, keep that as the first turn.
        if (turns.isEmpty() && currentInput.isNotBlank() && currentOutput.isNotBlank()) {
            val now = System.currentTimeMillis()
            val first = TranslateHistoryItem(
                id = now,
                createdAt = now,
                input = currentInput,
                output = currentOutput,
                langA = settings.langA,
                langB = settings.langB,
                mode = settings.mode
            )
            withContext(Dispatchers.IO) { store.appendTurnToConversation(id, first) }
            turns = listOf(first)
        }

        convAdapter.submit(turns)
        if (turns.isNotEmpty()) binding.rvChat.scrollToPosition(turns.size - 1)
    }

    private fun applyMemoryModeUi() {
        lifecycleScope.launch {
            if (settings.memoryEnabled) {
                binding.rvChat.adapter = convAdapter
                loadActiveConversationIfNeeded()
            } else {
                binding.rvChat.adapter = singleAdapter
                singleAdapter.submit(currentInput, currentOutput)
            }
            // Ensure menu visibility is correct.
            renderHeader()
        }
    }

    private fun persistSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
            store.setSettings(settings)
        }
    }

    private fun pickLanguage(isA: Boolean) {
        val langs = arrayOf(
            "auto",
            "Chinese",
            "English",
            "Japanese",
            "Korean",
            "French",
            "German",
            "Spanish",
            "Russian",
            "__custom__"
        )
        val labels = arrayOf("自动检测", "中文", "英语", "日语", "韩语", "法语", "德语", "西班牙语", "俄语", "自定义...")
        val cur = if (isA) settings.langA else settings.langB
        val checked = langs.indexOf(cur).takeIf { it >= 0 } ?: 0
        MaterialAlertDialogBuilder(this)
            .setTitle(if (isA) "选择语言A" else "选择语言B")
            .setSingleChoiceItems(labels, checked) { d, which ->
                if (langs[which] == "__custom__") {
                    d.dismiss()
                    showCustomLanguageDialog(isA)
                } else {
                    settings = if (isA) settings.copy(langA = langs[which]) else settings.copy(langB = langs[which])
                    persistSettings()
                    renderHeader()
                    d.dismiss()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCustomLanguageDialog(isA: Boolean) {
        val input = android.widget.EditText(this)
        input.hint = "例如：粤语 / 文言文 / 简体中文 / English-UK"
        input.setText(if (isA) settings.langA else settings.langB)
        MaterialAlertDialogBuilder(this)
            .setTitle(if (isA) "自定义语言A" else "自定义语言B")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val v = input.text?.toString().orEmpty().trim()
                if (v.isNotBlank()) {
                    settings = if (isA) settings.copy(langA = v) else settings.copy(langB = v)
                    persistSettings()
                    renderHeader()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            (if (settings.mode == TranslateMode.WORD) "✓ " else "") + "模式：单词/词组",
            (if (settings.mode == TranslateMode.SENTENCE) "✓ " else "") + "模式：句子",
            (if (settings.mode == TranslateMode.ARTICLE) "✓ " else "") + "模式：文章",
            (if (settings.memoryEnabled) "✓ " else "") + (if (settings.memoryEnabled) "记忆：开" else "记忆：关"),
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
                applyMemoryModeUi()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun installPullDownToHistoryGesture() {
        val rv = binding.rvChat
        var vt: VelocityTracker? = null
        var downY = 0f
        var armed = false
        var triggered = false

        rv.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    vt?.recycle()
                    vt = VelocityTracker.obtain()
                    vt?.addMovement(ev)
                    downY = ev.y
                    armed = !rv.canScrollVertically(-1)
                    triggered = false
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(ev)
                    if (armed && !triggered) {
                        val dy = ev.y - downY
                        if (dy > dp(80)) {
                            vt?.computeCurrentVelocity(1000)
                            val vy = vt?.yVelocity ?: 0f
                            // "用力"：要求一定的下拉距离 + 速度
                            if (vy > 1200f) {
                                triggered = true
                                showHistorySheet()
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.recycle()
                    vt = null
                }
            }
            false
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

        if (!settings.memoryEnabled) {
            // No memory: only show current pair.
            currentInput = text
            currentOutput = ""
            (binding.rvChat.adapter as? TranslateChatAdapter)?.submit(currentInput, currentOutput)
        }

        lifecycleScope.launch {
            binding.progress.visibility = View.VISIBLE
            try {
                if (settings.memoryEnabled) {
                    val convId = withContext(Dispatchers.IO) { store.ensureActiveConversationId() }
                    activeConversationId = convId

                    val pendingId = System.currentTimeMillis()
                    val pending = TranslateHistoryItem(
                        id = pendingId,
                        createdAt = pendingId,
                        input = text,
                        output = "",
                        langA = settings.langA,
                        langB = settings.langB,
                        mode = settings.mode
                    )
                    convAdapter.append(pending)
                    if (convAdapter.snapshot().size > 1) {
                        binding.rvChat.scrollToPosition(convAdapter.snapshot().size - 1)
                    }

                    val ctx = withContext(Dispatchers.IO) {
                        store.getConversation(convId)?.turns ?: emptyList()
                    }
                    val output = withContext(Dispatchers.IO) {
                        engine.translate(settings, text, ctx)
                    }
                    convAdapter.updateOutput(pendingId, output)

                    val finalItem = pending.copy(output = output)
                    withContext(Dispatchers.IO) {
                        // Save to conversation (for context) + global history (for card wall).
                        store.appendTurnToConversation(convId, finalItem)
                        store.addHistory(finalItem.copy(id = finalItem.id))
                    }
                } else {
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
        val dialog = Dialog(this, com.haidianfirstteam.nostalgiaai.R.style.TopSheetDialog)
        val view = LayoutInflater.from(this).inflate(com.haidianfirstteam.nostalgiaai.R.layout.sheet_translate_history, null, false)
        dialog.setContentView(view)

        dialog.window?.let { w ->
            w.setGravity(Gravity.TOP)
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = view.findViewById<android.widget.TextView>(com.haidianfirstteam.nostalgiaai.R.id.tvTranslateHistoryTitle)
        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(com.haidianfirstteam.nostalgiaai.R.id.rvTranslateHistory)
        val btnClear = view.findViewById<android.view.View>(com.haidianfirstteam.nostalgiaai.R.id.btnClearTranslateHistory)

        if (settings.memoryEnabled) {
            tvTitle.text = "对话"
            lateinit var convListAdapter: TranslateConversationHistoryAdapter
            convListAdapter = TranslateConversationHistoryAdapter(
                onClick = { card ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.setActiveConversationId(card.id) }
                        applyMemoryModeUi()
                        dialog.dismiss()
                    }
                },
                onDelete = { card ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.deleteConversation(card.id) }
                        val list = withContext(Dispatchers.IO) { store.listConversations() }
                        convListAdapter.submit(list.map { it.toCard() })
                    }
                }
            )
            rv.layoutManager = GridLayoutManager(this, 2)
            rv.adapter = convListAdapter

            lifecycleScope.launch {
                val list = withContext(Dispatchers.IO) { store.listConversations() }
                convListAdapter.submit(list.map { it.toCard() })
            }

            btnClear.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("清空对话")
                    .setMessage("确定清空全部翻译对话吗？")
                    .setPositiveButton("清空") { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { store.clearConversations() }
                            withContext(Dispatchers.IO) { store.newConversation() }
                            applyMemoryModeUi()
                            dialog.dismiss()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        } else {
            tvTitle.text = "历史记录"
            // Must be declared before lambdas capture it (avoid forward reference in initializer).
            lateinit var historyAdapter: TranslateHistoryAdapter
            historyAdapter = TranslateHistoryAdapter(
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
                        historyAdapter.submit(list)
                    }
                }
            )
            rv.layoutManager = GridLayoutManager(this, 2)
            rv.adapter = historyAdapter

            lifecycleScope.launch {
                val list = withContext(Dispatchers.IO) { store.listHistory() }
                historyAdapter.submit(list)
            }

            btnClear.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("清空历史")
                    .setMessage("确定清空全部翻译历史吗？")
                    .setPositiveButton("清空") { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { store.clearHistory() }
                            historyAdapter.submit(emptyList())
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        dialog.show()
    }

    private fun TranslateConversation.toCard(): TranslateConversationCard {
        val first = turns.firstOrNull()?.input?.trim().orEmpty()
        val title = if (first.isBlank()) "(空)" else {
            val t = first.replace("\n", " ")
            val parts = t.split(Regex("\\s+")).filter { it.isNotBlank() }
            val head = parts.take(3).joinToString(" ").take(24)
            if (head.length < t.length) head + "..." else head
        }
        return TranslateConversationCard(id = id, updatedAt = updatedAt, title = title)
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, TranslateActivity::class.java)
    }
}
