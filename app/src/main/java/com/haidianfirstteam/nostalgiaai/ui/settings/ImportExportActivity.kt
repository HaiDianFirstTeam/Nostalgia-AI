package com.haidianfirstteam.nostalgiaai.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.haidianfirstteam.nostalgiaai.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.export.ImportExportRepository
import androidx.core.view.children
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportExportActivity : AppCompatActivity() {

    private val REQ_EXPORT = 2001
    private val REQ_IMPORT = 2002

    private var pendingExportSections: Set<ImportExportRepository.Section> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "导入 / 导出"

        findViewById<android.widget.TextView>(R.id.simple_text).text = "选择导入/导出"
        findViewById<android.widget.TextView>(R.id.simple_text).setOnClickListener {
            showChoice()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ImportExportActivity::class.java)
    }

    private fun showChoice() {
        val options = arrayOf("导出 JSON", "导入 JSON")
        MaterialAlertDialogBuilder(this)
            .setTitle("导入/导出")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showExportDialog()
                    1 -> pickImportFile()
                }
            }
            .show()
    }

    private fun showExportDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        fun addCb(label: String, section: ImportExportRepository.Section): CheckBox {
            val cb = CheckBox(this)
            cb.text = label
            cb.tag = section
            cb.isChecked = true
            container.addView(cb)
            return cb
        }

        addCb("Providers", ImportExportRepository.Section.PROVIDERS)
        addCb("API Keys", ImportExportRepository.Section.API_KEYS)
        addCb("Models", ImportExportRepository.Section.MODELS)
        addCb("Model Groups", ImportExportRepository.Section.MODEL_GROUPS)
        addCb("Group Providers(顺序)", ImportExportRepository.Section.GROUP_ROUTES)
        addCb("Tavily", ImportExportRepository.Section.TAVILY)
        addCb("Settings", ImportExportRepository.Section.SETTINGS)
        addCb("History (Conversations+Messages)", ImportExportRepository.Section.CONVERSATIONS)

        MaterialAlertDialogBuilder(this)
            .setTitle("选择导出内容")
            .setView(container)
            .setPositiveButton("下一步") { _, _ ->
                val selected = container.children
                    .filterIsInstance<CheckBox>()
                    .filter { it.isChecked }
                    .map { it.tag as ImportExportRepository.Section }
                    .toSet()
                pendingExportSections = selected
                pickExportFile()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickExportFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "nostalgia-ai-export.json")
        }
        startActivityForResult(intent, REQ_EXPORT)
    }

    private fun pickImportFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQ_IMPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        val app = application as NostalgiaApp
        val repo = ImportExportRepository(this, app.db)

        when (requestCode) {
            REQ_EXPORT -> {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        withContext(Dispatchers.IO) {
                            repo.exportToUri(pendingExportSections, uri)
                        }
                        MaterialAlertDialogBuilder(this@ImportExportActivity)
                            .setMessage("导出完成")
                            .setPositiveButton("确定", null)
                            .show()
                    } catch (e: Exception) {
                        MaterialAlertDialogBuilder(this@ImportExportActivity)
                            .setMessage("导出失败：${e.message}")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }
            REQ_IMPORT -> {
                val options = arrayOf("追加（推荐）", "覆盖（未实现）")
                MaterialAlertDialogBuilder(this)
                    .setTitle("导入方式")
                    .setItems(options) { _, which ->
                        val overwrite = which == 1
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    repo.importFromUri(uri, overwrite)
                                }
                                MaterialAlertDialogBuilder(this@ImportExportActivity)
                                    .setMessage("导入完成（部分字段需后续完善映射）")
                                    .setPositiveButton("确定", null)
                                    .show()
                            } catch (e: Exception) {
                                MaterialAlertDialogBuilder(this@ImportExportActivity)
                                    .setMessage("导入失败：${e.message}")
                                    .setPositiveButton("确定", null)
                                    .show()
                            }
                        }
                    }
                    .show()
            }
        }
    }
}
