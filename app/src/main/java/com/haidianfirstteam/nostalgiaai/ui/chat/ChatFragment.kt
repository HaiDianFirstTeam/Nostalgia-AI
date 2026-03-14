package com.haidianfirstteam.nostalgiaai.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.databinding.FragmentChatBinding
import com.haidianfirstteam.nostalgiaai.ui.drawer.DrawerViewModel
import com.haidianfirstteam.nostalgiaai.util.FileUtil
import com.haidianfirstteam.nostalgiaai.domain.AttachmentProcessor
import androidx.core.content.FileProvider
import java.io.File
import com.haidianfirstteam.nostalgiaai.util.ToastUtil
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.appcompat.widget.SwitchCompat

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val drawerVm: DrawerViewModel by activityViewModels()
    private val chatVm: ChatViewModel by activityViewModels()
    private val settingsVm: ChatSettingsViewModel by activityViewModels()

    private lateinit var adapter: MessageAdapter
    private var stickToBottom: Boolean = true

    private val REQ_PICK_FILE = 1001
    private val REQ_TAKE_PHOTO = 1002
    private val REQ_CAMERA_PERMISSION = 1003

    private var pendingCameraUri: Uri? = null
    private var pendingTakePhotoAfterPermission: Boolean = false

    private val attachments = ArrayList<FileUtil.PickedFile>()

    private var webSearchEnabled: Boolean = false
    private var webSearchCount: Int = 5

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val markwon = MarkwonFactory.create(requireContext())
        adapter = MessageAdapter(
            markwon = markwon,
            onEditResendUser = { msg, newText ->
                chatVm.editAndResendUser(msg.id, newText)
            },
            onRetry = { msg ->
                chatVm.retryFromMessage(msg.id)
            },
            onEditAssistant = { msg, newText ->
                chatVm.editMessage(msg.id, newText)
            }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter

        binding.btnSend.setOnClickListener {
            // If in-flight: act as STOP
            if (chatVm.requestState.value?.inFlight == true) {
                chatVm.cancelRunningRequest()
                return@setOnClickListener
            }
            val text = binding.etInput.text?.toString() ?: ""
            // Clear input only when we are sure a request will be sent

            if (text.trim().isEmpty() && attachments.isEmpty()) {
                ToastUtil.show(requireContext(), "请输入内容或选择文件")
                return@setOnClickListener
            }
            val sel = settingsVm.selected.value
            if (sel?.target == null) {
                ToastUtil.show(requireContext(), "请先选择模型/组")
                return@setOnClickListener
            }
            val webEnabled = webSearchEnabled
            val webCount = webSearchCount
            val pickedAttachments = ArrayList(attachments)
            attachments.clear()
            renderAttachmentSummary()

            binding.etInput.setText("")

            when (val t = sel.target) {
                is ChatTarget.Group -> chatVm.sendUserMessage(
                    text = text,
                    targetType = "group",
                    targetGroupId = t.groupId,
                    webSearchEnabled = webEnabled,
                    webSearchCount = webCount,
                    pickedAttachments = pickedAttachments
                )
                is ChatTarget.DirectModel -> chatVm.sendUserMessage(
                    text = text,
                    targetType = "direct",
                    targetProviderId = t.providerId,
                    targetModelId = t.modelId,
                    webSearchEnabled = webEnabled,
                    webSearchCount = webCount,
                    pickedAttachments = pickedAttachments
                )
            }
        }

        // Targets spinner (groups + direct models)
        settingsVm.targets.observe(viewLifecycleOwner) { list ->
            // Update button label
            val sel = settingsVm.selected.value
            binding.btnTarget.text = sel?.title ?: getString(com.haidianfirstteam.nostalgiaai.R.string.empty_no_target)
            if (list.isEmpty()) {
                ToastUtil.show(requireContext(), getString(com.haidianfirstteam.nostalgiaai.R.string.empty_no_target))
            }
        }
        binding.btnTarget.setOnClickListener { showTargetPickerDialog() }

        binding.btnWebSearch.setOnClickListener { showWebSearchDialog() }
        renderWebSearchButton()

        settingsVm.selected.observe(viewLifecycleOwner) { sel ->
            // Camera button only for multimodal target
            binding.btnCamera.visibility = if (sel?.multimodalPossible == true) View.VISIBLE else View.GONE
            binding.btnTarget.text = sel?.title ?: getString(com.haidianfirstteam.nostalgiaai.R.string.empty_no_target)
        }
        settingsVm.refresh()

        binding.btnUpload.setOnClickListener {
            pickFile()
        }

        binding.btnCamera.setOnClickListener {
            takePhoto()
        }

        // Camera button visibility is controlled by settingsVm.selected observer.

        val argConv = arguments?.takeIf { it.containsKey("conversationId") }?.getLong("conversationId")
        if (argConv != null) {
            chatVm.loadConversation(argConv)
        } else {
            // If no conversation yet, ensure one exists
            drawerVm.newConversation()
        }

        chatVm.messages.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.tvEmpty.isVisible = list.isEmpty()
            if (list.isNotEmpty() && stickToBottom) {
                binding.rvMessages.scrollToPosition(list.size - 1)
            }
        }

        binding.rvMessages.itemAnimator = null
        binding.rvMessages.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
                val last = lm.findLastVisibleItemPosition()
                val total = lm.itemCount
                stickToBottom = total <= 0 || last >= total - 2
            }
        })

        chatVm.requestState.observe(viewLifecycleOwner) { st ->
            binding.requestStatus.visibility = if (st.inFlight) View.VISIBLE else View.GONE
            binding.tvStatus.text = st.statusText
            // While in-flight, btnSend acts as STOP, so it must remain clickable.
            binding.btnSend.isEnabled = true
            binding.btnUpload.isEnabled = !st.inFlight
            binding.btnCamera.isEnabled = !st.inFlight

            if (st.inFlight) {
                binding.btnSend.contentDescription = getString(com.haidianfirstteam.nostalgiaai.R.string.action_stop)
                binding.btnSend.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            } else {
                binding.btnSend.contentDescription = getString(com.haidianfirstteam.nostalgiaai.R.string.action_send)
                binding.btnSend.setImageResource(com.haidianfirstteam.nostalgiaai.R.drawable.ic_send_24)
            }
        }

        drawerVm.openConversationId.observe(viewLifecycleOwner) { id ->
            // Before switching, drop the previous empty conversation.
            val prev = chatVm.conversationId.value
            // NOTE: openConversationId may re-emit the SAME id (e.g. after rotation / re-create).
            // Never delete the conversation we are opening.
            if (prev != null && prev != id) {
                drawerVm.deleteIfEmpty(prev)
            }
            chatVm.loadConversation(id)
        }
    }

    override fun onStop() {
        super.onStop()
        // Do NOT delete empty conversations onStop: file picker / camera will trigger onStop.
        // Only cleanup when switching conversations (handled elsewhere) or when the Activity is finishing.
        val act = activity
        if (act != null && act.isFinishing) {
            val id = chatVm.conversationId.value
            if (id != null) {
                drawerVm.deleteIfEmpty(id)
            }
        }
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "text/markdown",
                "image/*",
                "audio/*",
                "video/*"
            ))
        }
        startActivityForResult(intent, REQ_PICK_FILE)
    }

    private fun takePhoto() {
        // Only allow when camera button visible
        if (binding.btnCamera.visibility != View.VISIBLE) return

        // Runtime permission on Android 6.0+
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingTakePhotoAfterPermission = true
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION)
                return
            }
        }

        val photoFile = File(requireContext().cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            requireContext(),
            requireContext().packageName + ".fileprovider",
            photoFile
        )
        pendingCameraUri = uri

        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivityForResult(intent, REQ_TAKE_PHOTO)
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("无法打开相机：${e.message}")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA_PERMISSION) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (ok && pendingTakePhotoAfterPermission) {
                pendingTakePhotoAfterPermission = false
                takePhoto()
            } else {
                pendingTakePhotoAfterPermission = false
                ToastUtil.show(requireContext(), getString(com.haidianfirstteam.nostalgiaai.R.string.toast_camera_denied))
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_FILE && data != null) {
            val uris = ArrayList<Uri>()
            data.data?.let { uris.add(it) }
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    val u = clip.getItemAt(i).uri
                    if (u != null) uris.add(u)
                }
            }

            if (uris.isEmpty()) return

            uris.forEach { uri ->
                try {
                    // Persist permission when possible
                    val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    requireContext().contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {
                    // ignore
                }
                val picked = FileUtil.getPickedFile(requireContext(), uri)
                attachments.add(picked)
            }
            renderAttachmentSummary()

            // Show delete UI for attachments
            showAttachmentManageDialog()
        }

        if (requestCode == REQ_TAKE_PHOTO) {
            val uri = pendingCameraUri
            if (uri != null) {
                val picked = FileUtil.getPickedFile(requireContext(), uri)
                attachments.add(picked)
                renderAttachmentSummary()

                val prepared = AttachmentProcessor.prepare(requireContext(), picked)
                if (!prepared.error.isNullOrBlank()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("文件提示")
                        .setMessage(prepared.error)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
            pendingCameraUri = null
        }
    }

    private fun renderAttachmentSummary() {
        if (attachments.isEmpty()) {
            binding.tvAttachments.text = ""
            binding.tvAttachments.setOnClickListener(null)
            return
        }
        val sb = StringBuilder()
        sb.append("已选择 ").append(attachments.size).append(" 个文件（点击管理/删除）：")
        attachments.take(3).forEach {
            sb.append("\n• ").append(it.displayName)
        }
        if (attachments.size > 3) sb.append("\n...")
        binding.tvAttachments.text = sb.toString()
        binding.tvAttachments.setOnClickListener { showAttachmentManageDialog() }
    }

    private fun showAttachmentManageDialog() {
        if (attachments.isEmpty()) return
        val names = attachments.map { it.displayName }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("已选择文件（点击删除）")
            .setItems(names) { _, which ->
                if (which >= 0 && which < attachments.size) {
                    attachments.removeAt(which)
                    renderAttachmentSummary()
                    // re-open if still has items for quick batch delete
                    if (attachments.isNotEmpty()) {
                        showAttachmentManageDialog()
                    }
                }
            }
            .setPositiveButton("清空") { _, _ ->
                attachments.clear()
                renderAttachmentSummary()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun renderWebSearchButton() {
        val text = if (webSearchEnabled) {
            "联网(${webSearchCount})"
        } else {
            "联网(关)"
        }
        binding.btnWebSearch.text = text
    }

    private fun showWebSearchDialog() {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val switch = SwitchCompat(ctx).apply {
            text = "开启联网搜索"
            isChecked = webSearchEnabled
        }
        container.addView(switch)

        val countEt = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(webSearchCount.toString())
            hint = "条数（>=1）"
        }
        container.addView(countEt)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("联网搜索")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                webSearchEnabled = switch.isChecked
                val c = countEt.text?.toString()?.toIntOrNull() ?: webSearchCount
                webSearchCount = if (c < 1) 1 else c
                renderWebSearchButton()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTargetPickerDialog() {
        val list = settingsVm.targets.value ?: emptyList()
        if (list.isEmpty()) {
            ToastUtil.show(requireContext(), getString(com.haidianfirstteam.nostalgiaai.R.string.empty_no_target))
            return
        }
        val titles = list.map { it.title }.toTypedArray()
        val current = settingsVm.selected.value
        val checked = if (current == null) {
            0
        } else {
            list.indexOfFirst { it.target == current.target }.takeIf { it >= 0 } ?: 0
        }
        var selected = checked
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择模型/组")
            .setSingleChoiceItems(titles, checked) { _, which -> selected = which }
            .setPositiveButton("确定") { _, _ ->
                settingsVm.selectByIndex(selected)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(conversationId: Long? = null, startNewChat: Boolean = false): ChatFragment {
            val f = ChatFragment()
            f.arguments = Bundle().apply {
                putBoolean("startNewChat", startNewChat)
                if (conversationId != null) putLong("conversationId", conversationId)
            }
            return f
        }
    }
}
