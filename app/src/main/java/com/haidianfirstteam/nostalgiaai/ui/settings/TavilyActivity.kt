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

class TavilyActivity : BaseActivity() {

    private lateinit var binding: ActivityListBinding
    private val vm: TavilyViewModel by viewModels()
    private lateinit var adapter: TwoLineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Tavily"

        adapter = TwoLineAdapter(
            onClick = { /* no-op */ },
            onLongClick = { item -> showKeyActions(item) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener { showAddKeyDialog() }

        binding.empty.isVisible = false
        vm.state.observe(this) { state ->
            binding.empty.isVisible = state.keys.isEmpty()
            binding.empty.text = "BaseUrl: ${state.baseUrl}\n\n暂无 Key，点击右下角添加"
            adapter.submit(state.keys)
        }

        vm.refresh()

        binding.toolbar.setOnLongClickListener {
            showBaseUrlDialog(vm.state.value?.baseUrl ?: "")
            true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showBaseUrlDialog(current: String) {
        val et = EditText(this)
        et.setText(current)
        MaterialAlertDialogBuilder(this)
            .setTitle("Tavily BaseUrl")
            .setView(et)
            .setPositiveButton("确定") { _, _ -> vm.setBaseUrl(et.text?.toString() ?: "") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddKeyDialog() {
        val nick = EditText(this)
        nick.hint = "Key 昵称"
        MaterialAlertDialogBuilder(this)
            .setTitle("新增 Tavily Key")
            .setView(nick)
            .setPositiveButton("下一步") { _, _ ->
                val keyEt = EditText(this)
                keyEt.hint = "API Key"
                MaterialAlertDialogBuilder(this)
                    .setTitle("API Key")
                    .setView(keyEt)
                    .setPositiveButton("确定") { _, _ -> vm.addKey(nick.text?.toString() ?: "", keyEt.text?.toString() ?: "") }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showKeyActions(item: TwoLineItem) {
        val actions = arrayOf("删除")
        MaterialAlertDialogBuilder(this)
            .setTitle(item.title)
            .setItems(actions) { _, _ -> vm.deleteKey(item.id) }
            .show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, TavilyActivity::class.java)
    }
}
