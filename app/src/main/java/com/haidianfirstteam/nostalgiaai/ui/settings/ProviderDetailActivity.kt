package com.haidianfirstteam.nostalgiaai.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.databinding.ActivityListBinding
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineAdapter
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem

class ProviderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private val vm: ProviderDetailViewModel by viewModels()
    private lateinit var adapter: TwoLineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, -1L)
        vm.load(providerId)

        adapter = TwoLineAdapter(
            onClick = { /* no-op */ },
            onLongClick = { item -> showItemActions(item) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener { showAddKeyOrModelDialog() }

        vm.title.observe(this) { title = it }

        vm.items.observe(this) { list ->
            adapter.submit(list)
            binding.empty.isVisible = list.isEmpty()
            binding.empty.text = "暂无 Key/模型，点击右下角添加"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showAddKeyOrModelDialog() {
        val options = arrayOf("新增 API Key", "新增模型")
        MaterialAlertDialogBuilder(this)
            .setTitle("新增")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddKeyDialog()
                    1 -> showAddModelDialog()
                }
            }
            .show()
    }

    private fun showAddKeyDialog() {
        val nick = EditText(this)
        nick.hint = "Key 昵称"
        MaterialAlertDialogBuilder(this)
            .setTitle("API Key 昵称")
            .setView(nick)
            .setPositiveButton("下一步") { _, _ ->
                val keyEt = EditText(this)
                keyEt.hint = "API Key"
                MaterialAlertDialogBuilder(this)
                    .setTitle("API Key")
                    .setView(keyEt)
                    .setPositiveButton("确定") { _, _ ->
                        vm.addApiKey(nick.text?.toString() ?: "", keyEt.text?.toString() ?: "")
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddModelDialog() {
        val modelNameEt = EditText(this)
        modelNameEt.hint = "modelName（如 gpt-4o-mini）"
        MaterialAlertDialogBuilder(this)
            .setTitle("模型名称")
            .setView(modelNameEt)
            .setPositiveButton("下一步") { _, _ ->
                val nickEt = EditText(this)
                nickEt.hint = "昵称（展示用）"
                MaterialAlertDialogBuilder(this)
                    .setTitle("模型昵称")
                    .setView(nickEt)
                    .setPositiveButton("下一步") { _, _ ->
                        val options = arrayOf("纯文本", "多模态")
                        MaterialAlertDialogBuilder(this)
                            .setTitle("是否多模态")
                            .setItems(options) { _, which ->
                                val multi = which == 1
                                vm.addModel(modelNameEt.text?.toString() ?: "", nickEt.text?.toString() ?: "", multi)
                            }
                            .show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showItemActions(item: TwoLineItem) {
        // We encode item.subtitle to include a prefix for type.
        val actions = if (item.subtitle.startsWith("KEY:")) {
            arrayOf("编辑", "删除", "启用/禁用")
        } else {
            arrayOf("编辑", "删除")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(item.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> vm.editItem(item)
                    1 -> vm.deleteItem(item)
                    2 -> if (item.subtitle.startsWith("KEY:")) vm.toggleEnabled(item)
                }
            }
            .show()
    }

    companion object {
        private const val EXTRA_PROVIDER_ID = "providerId"

        fun newIntent(context: Context, providerId: Long): Intent {
            return Intent(context, ProviderDetailActivity::class.java).putExtra(EXTRA_PROVIDER_ID, providerId)
        }
    }
}
