package com.haidianfirstteam.nostalgiaai.ui

import android.os.Bundle
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.databinding.ActivityMainBinding
import com.haidianfirstteam.nostalgiaai.ui.chat.ChatFragment
import com.haidianfirstteam.nostalgiaai.ui.drawer.ConversationAdapter
import com.haidianfirstteam.nostalgiaai.ui.drawer.DrawerViewModel
import com.haidianfirstteam.nostalgiaai.ui.settings.SettingsActivity
import com.haidianfirstteam.nostalgiaai.ui.toolbox.ToolboxActivity
import com.haidianfirstteam.nostalgiaai.util.RoomDebug
import com.haidianfirstteam.nostalgiaai.util.CrashGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val drawerVm: DrawerViewModel by viewModels()
    private lateinit var convAdapter: ConversationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.toolbar.setOnLongClickListener {
            // Quick debug panel
            val app = application as com.haidianfirstteam.nostalgiaai.NostalgiaApp
            CoroutineScope(Dispatchers.Main).launch {
                val text = withContext(Dispatchers.IO) { RoomDebug.check(this@MainActivity, app.db) }
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("调试信息")
                    .setMessage(text)
                    .setPositiveButton("确定", null)
                    .show()
            }
            true
        }

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupDrawerList()
        binding.btnNewChat.setOnClickListener { drawerVm.newConversation() }
        binding.btnSettings.setOnClickListener { startActivity(android.content.Intent(this, SettingsActivity::class.java)) }
        binding.btnToolbox.setOnClickListener { startActivity(android.content.Intent(this, ToolboxActivity::class.java)) }

        // If user chose "replay tutorial" from Settings, ensure main tutorial shows.
        if (intent?.getBooleanExtra("force_tutorial", false) == true) {
            try {
                com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialPrefs(this).resetAll()
            } catch (_: Throwable) {
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, ChatFragment.newInstance())
                .commit()
        }

        showLastCrashIfAny()

        drawerVm.openConversationId.observe(this) { id ->
            openConversation(id)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun showLastCrashIfAny() {
        val app = application as? com.haidianfirstteam.nostalgiaai.NostalgiaApp ?: return
        val crash = CrashGuard.consumeLastCrash(app) ?: return
        val ctx = this
        val tv = android.widget.TextView(ctx).apply {
            text = crash
            setTextIsSelectable(true)
            setPadding(48, 32, 48, 0)
        }
        val scroll = android.widget.ScrollView(ctx).apply { addView(tv) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("上次崩溃日志（可复制）")
            .setView(scroll)
            .setPositiveButton("复制") { _, _ ->
                try {
                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", crash))
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "已复制")
                } catch (_: Throwable) {
                    com.haidianfirstteam.nostalgiaai.util.ToastUtil.show(ctx, "复制失败")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun setupDrawerList() {
        convAdapter = ConversationAdapter(
            onClick = { drawerVm.openConversation(it.id) },
            onLongClick = { showConversationActions(it.id, it.title) }
        )
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = convAdapter

        drawerVm.conversations.observe(this) { list ->
            convAdapter.submit(list)
            binding.rvConversations.isVisible = true
        }
    }

    private fun showConversationActions(conversationId: Long, currentTitle: String) {
        val items = arrayOf("重命名", "删除")
        MaterialAlertDialogBuilder(this)
            .setTitle(currentTitle)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showRenameDialog(conversationId, currentTitle)
                    1 -> drawerVm.deleteConversation(conversationId)
                }
            }
            .show()
    }

    private fun showRenameDialog(conversationId: Long, currentTitle: String) {
        val input = EditText(this)
        input.setText(currentTitle)
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                drawerVm.renameConversation(conversationId, input.text?.toString() ?: "")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openConversation(conversationId: Long) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, ChatFragment.newInstance(conversationId = conversationId))
            .commit()
    }
}
