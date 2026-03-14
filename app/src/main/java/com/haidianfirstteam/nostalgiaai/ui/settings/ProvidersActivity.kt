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

class ProvidersActivity : BaseActivity() {

    private lateinit var binding: ActivityListBinding
    private val vm: ProvidersViewModel by viewModels()
    private lateinit var adapter: TwoLineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "模型来源"

        adapter = TwoLineAdapter(
            onClick = { item ->
                startActivity(ProviderDetailActivity.newIntent(this, item.id))
            },
            onLongClick = { item -> showProviderActions(item) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener { showAddProviderDialog() }

        vm.providers.observe(this) { list ->
            val ui = list.map { TwoLineItem(it.id, it.name, it.baseUrl) }
            adapter.submit(ui)
            binding.empty.isVisible = ui.isEmpty()
            binding.empty.text = "暂无 Provider，点击右下角添加"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showAddProviderDialog() {
        val nameEt = EditText(this)
        nameEt.hint = "Provider 名称（如 OpenAI/自建）"
        MaterialAlertDialogBuilder(this)
            .setTitle("新增 Provider")
            .setView(nameEt)
            .setPositiveButton("下一步") { _, _ ->
                val baseEt = EditText(this)
                baseEt.hint = "BaseUrl（如 https://api.openai.com）"
                MaterialAlertDialogBuilder(this)
                    .setTitle("BaseUrl")
                    .setView(baseEt)
                    .setPositiveButton("确定") { _, _ ->
                        vm.addProvider(nameEt.text?.toString() ?: "", baseEt.text?.toString() ?: "")
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showProviderActions(item: TwoLineItem) {
        val actions = arrayOf("重命名", "修改 BaseUrl", "删除")
        MaterialAlertDialogBuilder(this)
            .setTitle(item.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        val et = EditText(this)
                        et.setText(item.title)
                        MaterialAlertDialogBuilder(this)
                            .setTitle("重命名")
                            .setView(et)
                            .setPositiveButton("确定") { _, _ -> vm.rename(item.id, et.text?.toString() ?: "") }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    1 -> {
                        val et = EditText(this)
                        et.setText(item.subtitle)
                        MaterialAlertDialogBuilder(this)
                            .setTitle("修改 BaseUrl")
                            .setView(et)
                            .setPositiveButton("确定") { _, _ -> vm.updateBaseUrl(item.id, et.text?.toString() ?: "") }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    2 -> vm.delete(item.id)
                }
            }
            .show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ProvidersActivity::class.java)
    }
}
