package com.haidianfirstteam.nostalgiaai.ui.settings

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
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
import com.haidianfirstteam.nostalgiaai.util.ToastUtil
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
        val options = arrayOf("导出到文件", "导出到剪贴板", "从文件导入", "从剪贴板导入")
        MaterialAlertDialogBuilder(this)
            .setTitle("导入/导出")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showExportDialog(toClipboard = false)
                    1 -> showExportDialog(toClipboard = true)
                    2 -> pickImportFile()
                    3 -> showImportFromClipboardDialog()
                }
            }
            .show()
    }

    private fun showExportDialog(toClipboard: Boolean) {
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
                if (toClipboard) {
                    exportToClipboard()
                } else {
                    pickExportFile()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportToClipboard() {
        val app = application as NostalgiaApp
        val repo = ImportExportRepository(this, app.db)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    repo.exportToJson(pendingExportSections)
                }
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("nostalgia-ai-export", json))
                MaterialAlertDialogBuilder(this@ImportExportActivity)
                    .setMessage("已导出到剪贴板")
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

    private fun pickExportFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "nostalgia-ai-export.json")
        }
        startActivityForResult(intent, REQ_EXPORT)
    }

    private fun pickImportFile() {
        // Android 4.x file pickers are often buggy with MIME filters and CATEGORY_OPENABLE.
        // Use a broad picker then validate by extension/content after selection.
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQ_IMPORT)
    }

    private fun showImportFromClipboardDialog() {
        val input = android.widget.EditText(this)
        input.hint = "粘贴导入的 JSON"
        MaterialAlertDialogBuilder(this)
            .setTitle("从剪贴板导入")
            .setView(input)
            .setPositiveButton("下一步") { _, _ ->
                val text = input.text?.toString() ?: ""
                if (text.trim().isEmpty()) return@setPositiveButton
                val options = arrayOf("追加（推荐）", "覆盖")
                MaterialAlertDialogBuilder(this)
                    .setTitle("导入方式")
                    .setItems(options) { _, which ->
                        val overwrite = which == 1
                        val app = application as NostalgiaApp
                        val repo = ImportExportRepository(this, app.db)
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                withContext(Dispatchers.IO) { repo.importFromJson(text, overwrite = overwrite) }
                                MaterialAlertDialogBuilder(this@ImportExportActivity)
                                    .setMessage("导入完成")
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
            .setNegativeButton("取消", null)
            .show()
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
                            .setMessage("导出完成\n位置: $uri")
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
                // Filter non-json files on legacy pickers
                val name = try {
                    com.haidianfirstteam.nostalgiaai.util.FileUtil.getPickedFile(this, uri).displayName
                } catch (_: Exception) {
                    null
                }
                if (name != null && !name.lowercase().endsWith(".json")) {
                    ToastUtil.show(this, "请选择 .json 文件")
                    return
                }
                val options = arrayOf("追加（推荐）", "覆盖")
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
                                    .setMessage("导入完成")
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
