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
import com.haidianfirstteam.nostalgiaai.ui.common.TwoLineItem
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView

class ProviderDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityListBinding
    private val vm: ProviderDetailViewModel by viewModels()
    private lateinit var adapter: SectionedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, -1L)
        vm.load(providerId)

        adapter = SectionedAdapter(
            onLongClick = { item -> showItemActions(item) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener { showAddKeyOrModelDialog() }

        vm.title.observe(this) { title = it }

        vm.uiSections.observe(this) { rows ->
            adapter.submit(rows)
            val empty = rows.none { it is ProviderDetailRow.Item }
            binding.empty.isVisible = empty
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
                    0 -> showEditDialog(item)
                    1 -> vm.deleteItem(item)
                    2 -> if (item.subtitle.startsWith("KEY:")) vm.toggleEnabled(item)
                }
            }
            .show()
    }

    private class SectionedAdapter(
        private val onLongClick: (TwoLineItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val rows = ArrayList<ProviderDetailRow>()

        fun submit(list: List<ProviderDetailRow>) {
            rows.clear()
            rows.addAll(list)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (rows[position]) {
                is ProviderDetailRow.Header -> 1
                is ProviderDetailRow.Item -> 2
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 1) {
                val tv = inflater.inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
                tv.setPadding(tv.paddingLeft, tv.paddingTop + 16, tv.paddingRight, tv.paddingBottom)
                HeaderVH(tv)
            } else {
                val b = com.haidianfirstteam.nostalgiaai.databinding.ItemTwoLineBinding.inflate(inflater, parent, false)
                ItemVH(b)
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is ProviderDetailRow.Header -> (holder as HeaderVH).bind(r)
                is ProviderDetailRow.Item -> (holder as ItemVH).bind(r.item)
            }
        }

        private class HeaderVH(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
            fun bind(row: ProviderDetailRow.Header) {
                tv.text = row.title
                tv.alpha = 0.9f
                tv.textSize = 16f
            }
        }

        private inner class ItemVH(private val b: com.haidianfirstteam.nostalgiaai.databinding.ItemTwoLineBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: TwoLineItem) {
                b.title.text = item.title
                b.subtitle.text = item.subtitle
                // Keep click no-op; actions via long-click (consistent with other lists)
                b.root.setOnLongClickListener {
                    onLongClick(item)
                    true
                }
            }
        }
    }

    private fun showEditDialog(item: TwoLineItem) {
        if (item.subtitle.startsWith("KEY:")) {
            showEditKeyDialog(item.id)
        } else if (item.subtitle.startsWith("MODEL:")) {
            showEditModelDialog(item.id)
        }
    }

    private fun showEditKeyDialog(keyId: Long) {
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) { vm.getApiKeyById(keyId) }
            if (existing == null) return@launch

            val nickEt = EditText(this@ProviderDetailActivity)
            nickEt.hint = "Key 昵称"
            nickEt.setText(existing.nickname)
            MaterialAlertDialogBuilder(this@ProviderDetailActivity)
                .setTitle("编辑 Key 昵称")
                .setView(nickEt)
                .setPositiveButton("下一步") { _, _ ->
                    val keyEt = EditText(this@ProviderDetailActivity)
                    keyEt.hint = "API Key"
                    keyEt.setText(existing.apiKey)
                    MaterialAlertDialogBuilder(this@ProviderDetailActivity)
                        .setTitle("编辑 API Key")
                        .setView(keyEt)
                        .setPositiveButton("确定") { _, _ ->
                            vm.editApiKey(keyId, nickEt.text?.toString() ?: "", keyEt.text?.toString() ?: "")
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun showEditModelDialog(modelId: Long) {
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) { vm.getModelById(modelId) }
            if (existing == null) return@launch

            val modelNameEt = EditText(this@ProviderDetailActivity)
            modelNameEt.hint = "modelName（如 gpt-4o-mini）"
            modelNameEt.setText(existing.modelName)
            MaterialAlertDialogBuilder(this@ProviderDetailActivity)
                .setTitle("编辑模型名称")
                .setView(modelNameEt)
                .setPositiveButton("下一步") { _, _ ->
                    val nickEt = EditText(this@ProviderDetailActivity)
                    nickEt.hint = "昵称（展示用）"
                    nickEt.setText(existing.nickname)
                    MaterialAlertDialogBuilder(this@ProviderDetailActivity)
                        .setTitle("编辑模型昵称")
                        .setView(nickEt)
                        .setPositiveButton("下一步") { _, _ ->
                            val options = arrayOf("纯文本", "多模态")
                            val checked = if (existing.multimodal) 1 else 0
                            var selected = checked
                            MaterialAlertDialogBuilder(this@ProviderDetailActivity)
                                .setTitle("是否多模态")
                                .setSingleChoiceItems(options, checked) { _, which -> selected = which }
                                .setPositiveButton("确定") { dialog, _ ->
                                    val multi = selected == 1
                                    vm.editModel(modelId, modelNameEt.text?.toString() ?: "", nickEt.text?.toString() ?: "", multi)
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    companion object {
        private const val EXTRA_PROVIDER_ID = "providerId"

        fun newIntent(context: Context, providerId: Long): Intent {
            return Intent(context, ProviderDetailActivity::class.java).putExtra(EXTRA_PROVIDER_ID, providerId)
        }
    }
}
