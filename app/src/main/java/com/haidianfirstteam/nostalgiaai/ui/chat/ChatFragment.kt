package com.haidianfirstteam.nostalgiaai.ui.chat

import android.os.Build
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
import android.widget.ArrayAdapter
import androidx.core.content.FileProvider
import java.io.File
import com.haidianfirstteam.nostalgiaai.util.ToastUtil
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val drawerVm: DrawerViewModel by activityViewModels()
    private val chatVm: ChatViewModel by activityViewModels()
    private val settingsVm: ChatSettingsViewModel by activityViewModels()

    private lateinit var adapter: MessageAdapter

    private val REQ_PICK_FILE = 1001
    private val REQ_TAKE_PHOTO = 1002
    private val REQ_CAMERA_PERMISSION = 1003

    private var pendingCameraUri: Uri? = null
    private var pendingTakePhotoAfterPermission: Boolean = false

    private val attachments = ArrayList<FileUtil.PickedFile>()

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
            val text = binding.etInput.text?.toString() ?: ""
            binding.etInput.setText("")

            if (text.trim().isEmpty() && attachments.isEmpty()) {
                ToastUtil.show(requireContext(), "请输入内容或选择文件")
                return@setOnClickListener
            }
            val sel = settingsVm.selected.value
            val webEnabled = binding.switchWebSearch.isChecked
            val webCount = (binding.etWebCount.text?.toString() ?: "5").toIntOrNull() ?: 5
            val pickedAttachments = ArrayList(attachments)
            attachments.clear()
            renderAttachmentSummary()

            when (val t = sel?.target) {
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
                null -> chatVm.sendUserMessage(text, pickedAttachments = pickedAttachments)
            }
        }

        // Targets spinner (groups + direct models)
        settingsVm.targets.observe(viewLifecycleOwner) { list ->
            val titles = list.map { it.title }
            val spAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, titles)
            spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spModelGroup.adapter = spAdapter
            // Keep current selection when possible
            if (titles.isEmpty()) {
                ToastUtil.show(requireContext(), getString(com.haidianfirstteam.nostalgiaai.R.string.empty_no_target))
            }
        }
        binding.spModelGroup.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                settingsVm.selectByIndex(position)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        settingsVm.selected.observe(viewLifecycleOwner) { sel ->
            // Camera button only for multimodal target
            binding.btnCamera.visibility = if (sel?.multimodalPossible == true) View.VISIBLE else View.GONE
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
            if (list.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(list.size - 1)
            }
        }

        chatVm.requestState.observe(viewLifecycleOwner) { st ->
            binding.requestStatus.visibility = if (st.inFlight) View.VISIBLE else View.GONE
            binding.tvStatus.text = st.statusText
            binding.btnSend.isEnabled = !st.inFlight
            binding.btnUpload.isEnabled = !st.inFlight
            binding.btnCamera.isEnabled = !st.inFlight
        }

        drawerVm.openConversationId.observe(viewLifecycleOwner) { id ->
            chatVm.loadConversation(id)
        }
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
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
            val uri: Uri = data.data ?: return
            try {
                // Persist permission when possible
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
                // ignore
            }
            val picked = FileUtil.getPickedFile(requireContext(), uri)
            attachments.add(picked)
            renderAttachmentSummary()

            // For text models later: extract text now so user sees result
            val prepared = AttachmentProcessor.prepare(requireContext(), picked)
            if (prepared.kind == AttachmentProcessor.Kind.TEXT && !prepared.extractedText.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("已解析文本")
                    .setMessage(prepared.extractedText.take(1500))
                    .setPositiveButton("确定", null)
                    .show()
            } else if (!prepared.error.isNullOrBlank()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("文件提示")
                    .setMessage(prepared.error)
                    .setPositiveButton("确定", null)
                    .show()
            }
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
            return
        }
        val sb = StringBuilder()
        sb.append("已选择 ").append(attachments.size).append(" 个文件：")
        attachments.take(3).forEach {
            sb.append("\n• ").append(it.displayName)
        }
        if (attachments.size > 3) sb.append("\n...")
        binding.tvAttachments.text = sb.toString()
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
