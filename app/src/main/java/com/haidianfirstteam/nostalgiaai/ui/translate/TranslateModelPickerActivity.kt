package com.haidianfirstteam.nostalgiaai.ui.translate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.databinding.ActivityListBinding
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import com.haidianfirstteam.nostalgiaai.ui.chat.ChatSettingsViewModel
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineAdapter
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslateModelPickerActivity : BaseActivity() {
    private lateinit var binding: ActivityListBinding
    private lateinit var adapter: TwoLineAdapter
    private lateinit var store: TranslateStore
    private lateinit var vm: ChatSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = "选择翻译模型"

        val app = application as NostalgiaApp
        store = TranslateStore(app.db)
        vm = ChatSettingsViewModel(app)

        adapter = TwoLineAdapter(
            onClick = { item ->
                // item.id encodes index in target list
                val idx = item.id.toInt()
                val targets = vm.targets.value ?: return@TwoLineAdapter
                val t = targets.getOrNull(idx) ?: return@TwoLineAdapter
                lifecycleScope.launch {
                    val cur = withContext(Dispatchers.IO) { store.getSettings() }
                    val next = when (val target = t.target) {
                        is com.haidianfirstteam.nostalgiaai.ui.chat.ChatTarget.Group -> {
                            cur.copy(routeType = "group", routeGroupId = target.groupId, routeProviderId = null, routeModelId = null)
                        }
                        is com.haidianfirstteam.nostalgiaai.ui.chat.ChatTarget.DirectModel -> {
                            cur.copy(routeType = "direct", routeGroupId = null, routeProviderId = target.providerId, routeModelId = target.modelId)
                        }
                    }
                    withContext(Dispatchers.IO) { store.setSettings(next) }
                    finish()
                }
            },
            onLongClick = { _ -> }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fab.visibility = android.view.View.GONE

        vm.targets.observe(this) { list ->
            adapter.submit(
                list.mapIndexed { index, t ->
                    TwoLineItem(index.toLong(), t.title, if (t.multimodalPossible) "" else "")
                }
            )
            binding.empty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.empty.text = "暂无可用模型，请先在设置里添加"
        }
        vm.refresh()

        MaterialAlertDialogBuilder(this)
            .setTitle("说明")
            .setMessage("翻译助手将使用你选择的‘组/模型’进行翻译。")
            .setPositiveButton("知道了", null)
            .show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, TranslateModelPickerActivity::class.java)
    }
}
