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

class ModelGroupsActivity : BaseActivity() {

    private lateinit var binding: ActivityListBinding
    private val vm: ModelGroupsViewModel by viewModels()
    private lateinit var adapter: TwoLineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "模型组"

        adapter = TwoLineAdapter(
            onClick = { item -> startActivity(ModelGroupDetailActivity.newIntent(this, item.id)) },
            onLongClick = { item -> showActions(item) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener { showAddDialog() }

        vm.groups.observe(this) { list ->
            val ui = list.map { TwoLineItem(it.id, it.name, "点击配置 provider 顺序 / model") }
            adapter.submit(ui)
            binding.empty.isVisible = ui.isEmpty()
            binding.empty.text = "暂无模型组，点击右下角添加"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showAddDialog() {
        val et = EditText(this)
        et.hint = "组名"
        MaterialAlertDialogBuilder(this)
            .setTitle("新增模型组")
            .setView(et)
            .setPositiveButton("确定") { _, _ -> vm.addGroup(et.text?.toString() ?: "") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showActions(item: TwoLineItem) {
        val actions = arrayOf("重命名", "删除")
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
                    1 -> vm.delete(item.id)
                }
            }
            .show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ModelGroupsActivity::class.java)
    }
}
