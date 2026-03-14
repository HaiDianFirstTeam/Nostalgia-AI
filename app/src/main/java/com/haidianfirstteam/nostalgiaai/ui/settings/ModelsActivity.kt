package com.haidianfirstteam.nostalgiaai.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.databinding.ActivityListBinding

/**
 * Models screen: merged view of model groups + providers.
 * Tree-like UI:
 * - Group header rows (not draggable)
 * - Provider rows under group (indented + dashed guide)
 * - Un-grouped providers (no indent)
 *
 * Long-press provider row: drag into/out of groups.
 * Menu: three-dots button on right.
 */
class ModelsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private val vm: ModelsViewModel by viewModels()
    private lateinit var adapter: ModelsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "模型"

        adapter = ModelsAdapter(
            onMenuClick = { row -> showRowMenu(row) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        val touchHelper = ItemTouchHelper(
            DragCallback(adapter) { providerId, drop ->
                vm.commitDrop(providerId, drop)
            }
        )
        touchHelper.attachToRecyclerView(binding.recycler)
        adapter.attachTouchHelper(touchHelper)

        binding.fab.setOnClickListener {
            showAddDialog()
        }

        vm.rows.observe(this) { rows ->
            adapter.submit(rows)
            binding.empty.isVisible = rows.isEmpty()
            binding.empty.text = "暂无 Provider/模型组，点击右下角添加"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showAddDialog() {
        val options = arrayOf("新增 Provider", "新增模型组")
        MaterialAlertDialogBuilder(this)
            .setTitle("新增")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddProviderDialog()
                    1 -> showAddGroupDialog()
                }
            }
            .show()
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

    private fun showAddGroupDialog() {
        val et = EditText(this)
        et.hint = "组名"
        MaterialAlertDialogBuilder(this)
            .setTitle("新增模型组")
            .setView(et)
            .setPositiveButton("确定") { _, _ -> vm.addGroup(et.text?.toString() ?: "") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRowMenu(row: ModelRow) {
        when (row) {
            is ModelRow.GroupHeader -> {
                val actions = arrayOf("重命名", "删除")
                MaterialAlertDialogBuilder(this)
                    .setTitle(row.title)
                    .setItems(actions) { _, which ->
                        when (which) {
                            0 -> {
                                val et = EditText(this)
                                et.setText(row.title)
                                MaterialAlertDialogBuilder(this)
                                    .setTitle("重命名")
                                    .setView(et)
                                    .setPositiveButton("确定") { _, _ -> vm.renameGroup(row.groupId, et.text?.toString() ?: "") }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                            1 -> vm.deleteGroup(row.groupId)
                        }
                    }
                    .show()
            }
            is ModelRow.ProviderRow -> {
                val actions = arrayOf("进入详情（Key/模型）", "选择组内模型", "重命名", "修改 BaseUrl", "移出组（变散修）", "删除")
                MaterialAlertDialogBuilder(this)
                    .setTitle(row.title)
                    .setItems(actions) { _, which ->
                        when (which) {
                            0 -> startActivity(ProviderDetailActivity.newIntent(this, row.providerId))
                            1 -> showPickModelForProviderDialog(row.providerId)
                            2 -> {
                                val et = EditText(this)
                                et.setText(row.title)
                                MaterialAlertDialogBuilder(this)
                                    .setTitle("重命名")
                                    .setView(et)
                                    .setPositiveButton("确定") { _, _ -> vm.renameProvider(row.providerId, et.text?.toString() ?: "") }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                            3 -> {
                                val et = EditText(this)
                                et.setText(row.subtitle)
                                MaterialAlertDialogBuilder(this)
                                    .setTitle("修改 BaseUrl")
                                    .setView(et)
                                    .setPositiveButton("确定") { _, _ -> vm.updateProviderBaseUrl(row.providerId, et.text?.toString() ?: "") }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                            4 -> vm.detachProvider(row.providerId)
                            5 -> vm.deleteProvider(row.providerId)
                        }
                    }
                    .show()
            }
            is ModelRow.UngroupedHeader -> {
                // no menu
            }
        }
    }

    private fun showPickModelForProviderDialog(providerId: Long) {
        val options = vm.modelOptionsForProvider(providerId)
        if (options.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage("该 Provider 下没有模型，或数据尚未加载完成。\n\n请先进入详情添加模型，或稍等再试。")
                .setPositiveButton("进入详情") { _, _ ->
                    startActivity(ProviderDetailActivity.newIntent(this, providerId))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val names = options.map { it.nickname + " (" + it.modelName + ")" + if (it.multimodal) " 多模态" else "" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("选择该 Provider 在组内使用的模型")
            .setItems(names) { _, which ->
                vm.setProviderModelInGroup(providerId, options[which].id)
            }
            .show()
    }

    private class ModelsAdapter(
        private val onMenuClick: (ModelRow) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), DragCallback.DragAdapter {

        private val rows = ArrayList<ModelRow>()
        private var touchHelper: ItemTouchHelper? = null

        private var highlightedGroupId: Long? = null
        private var highlightUngrouped: Boolean = false

        fun attachTouchHelper(helper: ItemTouchHelper) {
            touchHelper = helper
        }

        fun submit(list: List<ModelRow>) {
            rows.clear()
            rows.addAll(list)
            notifyDataSetChanged()
        }

        override fun snapshot(): List<ModelRow> = rows.toList()

        override fun setDropHighlight(targetGroupId: Long?) {
            val prevGroup = highlightedGroupId
            val prevUngrouped = highlightUngrouped

            highlightedGroupId = targetGroupId
            highlightUngrouped = targetGroupId == null

            if (prevGroup != null && prevGroup != targetGroupId) {
                val p = rows.indexOfFirst { it is ModelRow.GroupHeader && it.groupId == prevGroup }
                if (p >= 0) notifyItemChanged(p)
            }
            if (targetGroupId != null && targetGroupId != prevGroup) {
                val p = rows.indexOfFirst { it is ModelRow.GroupHeader && it.groupId == targetGroupId }
                if (p >= 0) notifyItemChanged(p)
            }
            if (prevUngrouped != highlightUngrouped) {
                val p = rows.indexOfFirst { it is ModelRow.UngroupedHeader }
                if (p >= 0) notifyItemChanged(p)
            }
        }

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is ModelRow.GroupHeader -> 1
            is ModelRow.UngroupedHeader -> 3
            is ModelRow.ProviderRow -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            return if (viewType == 1) {
                GroupVH(makeGroupHeaderView(ctx, showMenu = true))
            } else if (viewType == 3) {
                GroupVH(makeGroupHeaderView(ctx, showMenu = false))
            } else {
                ProviderVH(makeProviderRowView(ctx))
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            when (holder) {
                is GroupVH -> {
                    val highlighted = when (row) {
                        is ModelRow.GroupHeader -> row.groupId == highlightedGroupId
                        is ModelRow.UngroupedHeader -> highlightUngrouped
                        else -> false
                    }
                    holder.bind(row, onMenuClick, highlighted)
                }
                is ProviderVH -> {
                    val effectiveGroupId = effectiveGroupIdAt(position)
                    holder.bind(row as ModelRow.ProviderRow, effectiveGroupId != null, onMenuClick, touchHelper)
                }
            }
        }

        private fun effectiveGroupIdAt(position: Int): Long? {
            var i = position
            while (i >= 0) {
                when (val r = rows[i]) {
                    is ModelRow.GroupHeader -> return r.groupId
                    is ModelRow.UngroupedHeader -> return null
                    else -> {}
                }
                i--
            }
            return null
        }

        override fun isDraggable(position: Int): Boolean = rows.getOrNull(position) is ModelRow.ProviderRow

        override fun onItemMove(fromPosition: Int, toPosition: Int) {
            if (fromPosition == toPosition) return
            var to = toPosition
            // If hovering over a header, drop just after it.
            if (rows.getOrNull(to) is ModelRow.GroupHeader || rows.getOrNull(to) is ModelRow.UngroupedHeader) {
                to = (to + 1).coerceAtMost(rows.size)
            }
            val item = rows.removeAt(fromPosition)
            rows.add(to, item)
            notifyItemMoved(fromPosition, to)
        }

        override fun getRow(position: Int): ModelRow? = rows.getOrNull(position)

        private class GroupVH(private val root: View) : RecyclerView.ViewHolder(root) {
            private val title: TextView = root.findViewById(2001)
            private val menu: ImageButton? = root.findViewById(2002)

            fun bind(row: ModelRow, onMenu: (ModelRow) -> Unit, highlighted: Boolean) {
                when (row) {
                    is ModelRow.GroupHeader -> {
                        title.text = row.title
                        root.setOnClickListener {
                            // Open group detail
                            val ctx = root.context
                            ctx.startActivity(ModelGroupDetailActivity.newIntent(ctx, row.groupId))
                        }
                        menu?.isVisible = true
                        menu?.setOnClickListener { onMenu(row) }
                    }
                    is ModelRow.UngroupedHeader -> {
                        title.text = row.title
                        root.setOnClickListener(null)
                        menu?.isVisible = false
                    }
                    else -> {
                        // no-op
                    }
                }

                if (highlighted) {
                    root.setBackgroundColor(resolveHighlightColor(root))
                } else {
                    root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            }

            private fun resolveHighlightColor(v: View): Int {
                val tv = TypedValue()
                val ok = v.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                val base = if (ok) tv.data else 0xFF3D5AFE.toInt()
                return (0x1F shl 24) or (base and 0x00FFFFFF)
            }
        }

        private class ProviderVH(private val root: View) : RecyclerView.ViewHolder(root) {
            private val indent: View = root.findViewById(1001)
            private val dash: View = root.findViewById(1002)
            private val title: TextView = root.findViewById(1003)
            private val subtitle: TextView = root.findViewById(1004)
            private val menu: ImageButton = root.findViewById(1005)

            fun bind(row: ModelRow.ProviderRow, inGroup: Boolean, onMenu: (ModelRow) -> Unit, helper: ItemTouchHelper?) {
                title.text = row.title
                subtitle.text = row.subtitle
                menu.setOnClickListener { onMenu(row) }

                root.setOnClickListener {
                    val ctx = root.context
                    ctx.startActivity(ProviderDetailActivity.newIntent(ctx, row.providerId))
                }

                indent.isVisible = inGroup
                dash.isVisible = inGroup

                // Slightly shift grouped providers to the right to show hierarchy.
                val leftPad = if (inGroup) 8 else 0
                root.setPadding(leftPad, root.paddingTop, root.paddingRight, root.paddingBottom)

                // Drag on long press of whole row
                root.setOnLongClickListener {
                    helper?.startDrag(this)
                    true
                }
            }
        }

        private fun makeGroupHeaderView(ctx: Context, showMenu: Boolean): View {
            val container = LinearLayout(ctx)
            container.orientation = LinearLayout.HORIZONTAL
            container.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            container.setPadding(0, 22, 0, 12)

            val title = TextView(ctx)
            title.id = 2001
            title.textSize = 16f
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.weight = 1f
            title.layoutParams = lp
            container.addView(title)

            if (showMenu) {
                val menu = ImageButton(ctx)
                menu.id = 2002
                menu.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                menu.setImageResource(R.drawable.ic_more_vert_24)
                container.addView(menu)
            } else {
                val menu = ImageButton(ctx)
                menu.id = 2002
                menu.visibility = View.GONE
                container.addView(menu)
            }

            return container
        }

        private fun makeProviderRowView(ctx: Context): View {
            val container = LinearLayout(ctx)
            container.orientation = LinearLayout.HORIZONTAL
            container.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            container.setPadding(0, 18, 0, 18)

            // indent + dashed guide
            val indent = View(ctx)
            indent.id = 1001
            val indentLp = LinearLayout.LayoutParams(20, ViewGroup.LayoutParams.MATCH_PARENT)
            indent.layoutParams = indentLp
            container.addView(indent)

            val dash = View(ctx)
            dash.id = 1002
            dash.setBackgroundResource(R.drawable.bg_dashed_guide)
            val dashLp = LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT)
            dashLp.setMargins(4, 0, 10, 0)
            dash.layoutParams = dashLp
            container.addView(dash)

            val texts = LinearLayout(ctx)
            texts.orientation = LinearLayout.VERTICAL
            val textsLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            textsLp.weight = 1f
            texts.layoutParams = textsLp
            container.addView(texts)

            val title = TextView(ctx)
            title.id = 1003
            title.textSize = 15f
            texts.addView(title)

            val sub = TextView(ctx)
            sub.id = 1004
            sub.alpha = 0.85f
            texts.addView(sub)

            val menu = ImageButton(ctx)
            menu.id = 1005
            menu.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            menu.setImageResource(R.drawable.ic_more_vert_24)
            val menuLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            menu.layoutParams = menuLp
            container.addView(menu)

            return container
        }
    }

    private class DragCallback(
        private val adapter: DragAdapter,
        private val onDropCommit: (Long, DropInfo) -> Unit
    ) : ItemTouchHelper.Callback() {

        private val indicatorPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 4f
            color = 0x553D5AFE
        }

        interface DragAdapter {
            fun isDraggable(position: Int): Boolean
            fun onItemMove(fromPosition: Int, toPosition: Int)
            fun getRow(position: Int): ModelRow?
            fun snapshot(): List<ModelRow>
            fun setDropHighlight(targetGroupId: Long?)
        }

        private var draggingProviderId: Long? = null
        private var lastHoverPos: Int = -1

        // Insertion indicator-derived drop info (source of truth)
        private var indicatorHoverPos: Int = -1
        private var indicatorInsertAfter: Boolean = false

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val pos = viewHolder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return 0
            if (!adapter.isDraggable(pos)) return 0
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            if (!adapter.isDraggable(from)) return false
            adapter.onItemMove(from, to)
            lastHoverPos = to
            return true
        }

        private fun findEffectiveGroupIdAt(snapshot: List<ModelRow>, position: Int): Long? {
            var i = position
            while (i >= 0) {
                when (val r = snapshot[i]) {
                    is ModelRow.GroupHeader -> return r.groupId
                    is ModelRow.UngroupedHeader -> return null
                    else -> {}
                }
                i--
            }
            return null
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun isLongPressDragEnabled(): Boolean = false

        override fun onChildDraw(
            c: android.graphics.Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG) return
            if (!isCurrentlyActive) return

            // Draw an insertion line at current hover position.
            val centerY = viewHolder.itemView.top + dY + viewHolder.itemView.height / 2f
            val centerX = recyclerView.width / 2f
            val targetView = recyclerView.findChildViewUnder(centerX, centerY)
            val targetPos = if (targetView != null) recyclerView.getChildAdapterPosition(targetView) else RecyclerView.NO_POSITION
            if (targetPos == RecyclerView.NO_POSITION) return

            val snap = adapter.snapshot()
            val targetRow = adapter.getRow(targetPos)

            // Compute insert position based on indicator target.
            var hoverPos = targetPos
            var insertAfter = false
            if (targetView != null) {
                val mid = targetView.top + targetView.height / 2f
                insertAfter = centerY >= mid
            }
            if (targetRow is ModelRow.GroupHeader || targetRow is ModelRow.UngroupedHeader) {
                hoverPos = (targetPos + 1).coerceAtMost((snap.size - 1).coerceAtLeast(0))
                insertAfter = false
            }
            indicatorHoverPos = hoverPos
            indicatorInsertAfter = insertAfter

            // Update header highlight to match indicator drop section.
            val groupId = findEffectiveGroupIdAt(snap, hoverPos)
            adapter.setDropHighlight(groupId)
            val y = when (targetRow) {
                is ModelRow.GroupHeader, is ModelRow.UngroupedHeader -> {
                    // Insert below header
                    targetView!!.bottom.toFloat()
                }
                else -> {
                    val mid = targetView!!.top + targetView.height / 2f
                    if (centerY < mid) targetView.top.toFloat() else targetView.bottom.toFloat()
                }
            }

            // Try use theme primary color for indicator
            try {
                val tv = TypedValue()
                val ok = recyclerView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                if (ok) {
                    indicatorPaint.color = (0x88 shl 24) or (tv.data and 0x00FFFFFF)
                }
            } catch (_: Exception) {
                // ignore
            }

            val left = recyclerView.paddingLeft.toFloat() + 8f
            val right = (recyclerView.width - recyclerView.paddingRight).toFloat() - 8f
            c.drawLine(left, y, right, y, indicatorPaint)
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                val pos = viewHolder.bindingAdapterPosition
                val row = adapter.getRow(pos)
                draggingProviderId = (row as? ModelRow.ProviderRow)?.providerId
                lastHoverPos = pos

                // Drag visual feedback
                viewHolder.itemView.alpha = 0.88f
                viewHolder.itemView.scaleX = 1.02f
                viewHolder.itemView.scaleY = 1.02f
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1f
            viewHolder.itemView.scaleX = 1f
            viewHolder.itemView.scaleY = 1f

            val pid = draggingProviderId
            val finalPos = viewHolder.bindingAdapterPosition
            if (pid != null && finalPos != RecyclerView.NO_POSITION) {
                val snap = adapter.snapshot()
                // Prefer insertion-indicator drop info if available
                val hover = when {
                    indicatorHoverPos >= 0 -> indicatorHoverPos
                    lastHoverPos >= 0 -> lastHoverPos
                    else -> finalPos
                }
                val insertAfter = indicatorInsertAfter
                val drop = DropInfo(
                    hoverPos = hover.coerceIn(0, (snap.size - 1).coerceAtLeast(0)),
                    insertAfter = insertAfter,
                    snapshot = snap
                )
                onDropCommit(pid, drop)
            }
            adapter.setDropHighlight(null)
            draggingProviderId = null
            lastHoverPos = -1

            indicatorHoverPos = -1
            indicatorInsertAfter = false
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ModelsActivity::class.java)
    }
}

sealed class ModelRow {
    data class GroupHeader(val groupId: Long, val title: String) : ModelRow()
    data class UngroupedHeader(val title: String) : ModelRow()
    data class ProviderRow(
        val providerId: Long,
        val title: String,
        val subtitle: String,
        val groupId: Long? = null
    ) : ModelRow()
}

data class DropInfo(
    val hoverPos: Int,
    val insertAfter: Boolean,
    val snapshot: List<ModelRow>
)
