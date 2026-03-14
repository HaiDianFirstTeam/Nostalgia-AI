package com.haidianfirstteam.nostalgiaai.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.activity.viewModels
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.databinding.ActivityListBinding
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineAdapter
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem

class ModelGroupDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityListBinding
    private val vm: ModelGroupDetailViewModel by viewModels()
    private lateinit var adapter: TwoLineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
        vm.load(groupId)

        adapter = TwoLineAdapter(
            onClick = { item -> showItemActions(item) },
            onLongClick = { item -> showItemActions(item) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener { showAddProviderDialog() }

        binding.toolbar.setOnLongClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("组操作")
                .setItems(arrayOf("重置该组优先级状态")) { _, _ ->
                    vm.resetPriorityState()
                }
                .show()
            true
        }

        vm.title.observe(this) { title = it }
        vm.items.observe(this) { list ->
            adapter.submit(list)
            binding.empty.isVisible = list.isEmpty()
            binding.empty.text = "暂无 provider，点击右下角添加。\n\n提示：点击条目可上移/下移/删除。"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showAddProviderDialog() {
        // Step 1: choose provider
        val providers = vm.providerOptions()
        if (providers.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage("请先在 Provider 页面添加至少一个 Provider 和模型")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        val providerNames = providers.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("选择 Provider")
            .setItems(providerNames) { _, which ->
                val p = providers[which]
                // Step 2: choose model under provider
                val models = vm.modelOptionsForProvider(p.id)
                if (models.isEmpty()) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage("该 Provider 下没有模型，请先添加模型")
                        .setPositiveButton("确定", null)
                        .show()
                    return@setItems
                }
                val modelNames = models.map { it.nickname + " (" + it.modelName + ")" + if (it.multimodal) " 多模态" else "" }.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle("选择模型")
                    .setItems(modelNames) { _, mWhich ->
                        val m = models[mWhich]
                        vm.addProvider(p.id, m.id)
                    }
                    .show()
            }
            .show()
    }

    private fun showItemActions(item: TwoLineItem) {
        val actions = arrayOf("上移", "下移", "删除")
        MaterialAlertDialogBuilder(this)
            .setTitle(item.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> vm.moveUp(item.id)
                    1 -> vm.moveDown(item.id)
                    2 -> vm.deleteEntry(item.id)
                }
            }
            .show()
    }

    companion object {
        private const val EXTRA_GROUP_ID = "groupId"

        fun newIntent(context: Context, groupId: Long): Intent {
            return Intent(context, ModelGroupDetailActivity::class.java).putExtra(EXTRA_GROUP_ID, groupId)
        }
    }
}
