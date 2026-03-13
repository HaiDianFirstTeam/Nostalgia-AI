package com.haidianfirstteam.nostalgiaai.ui

import android.os.Bundle
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
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
import com.haidianfirstteam.nostalgiaai.util.RoomDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

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

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, ChatFragment.newInstance())
                .commit()
        }

        drawerVm.openConversationId.observe(this) { id ->
            openConversation(id)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
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
