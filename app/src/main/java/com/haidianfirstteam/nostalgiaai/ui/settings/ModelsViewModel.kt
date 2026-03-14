package com.haidianfirstteam.nostalgiaai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.data.entities.GroupProviderEntity
import com.haidianfirstteam.nostalgiaai.data.entities.ModelEntity
import com.haidianfirstteam.nostalgiaai.data.entities.ModelGroupEntity
import com.haidianfirstteam.nostalgiaai.data.entities.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NostalgiaApp).db

    private val _rows = MediatorLiveData<List<ModelRow>>()
    val rows: LiveData<List<ModelRow>> = _rows

    private val providersLD = db.providers().observeAll()
    private val groupsLD = db.modelGroups().observeAll()
    private val groupProvidersLD = db.groupProviders().observeAll()
    private val modelsLD = db.models().observeAll()

    init {
        fun refresh() {
            viewModelScope.launch {
                val rows = withContext(Dispatchers.IO) { buildRows() }
                _rows.value = rows
            }
        }
        _rows.addSource(providersLD) { refresh() }
        _rows.addSource(groupsLD) { refresh() }
        _rows.addSource(groupProvidersLD) { refresh() }
        _rows.addSource(modelsLD) { refresh() }
        refresh()
    }

    private suspend fun buildRows(): List<ModelRow> {
        val providers = db.providers().listAll()
        val groups = db.modelGroups().listAll()
        val groupProviders = db.groupProviders().listAll()
        val models = db.models().listAll()

        val modelById = models.associateBy { it.id }
        val providerById = providers.associateBy { it.id }

        val usedProviderIds = groupProviders.map { it.providerId }.toSet()
        val ungroupedProviders = providers.filter { !usedProviderIds.contains(it.id) }

        val out = ArrayList<ModelRow>()

        // Groups first
        groups.sortedBy { it.id }.forEach { g ->
            out.add(ModelRow.GroupHeader(g.id, g.name))
            val entries = groupProviders.filter { it.groupId == g.id }.sortedBy { it.orderIndex }
            entries.forEach { e ->
                val p = providerById[e.providerId]
                val m = modelById[e.modelId]
                val title = p?.name ?: "Provider"
                val subtitle = buildProviderSubtitle(p, m)
                out.add(ModelRow.ProviderRow(providerId = e.providerId, title = title, subtitle = subtitle, groupId = g.id))
            }
        }

        // Ungrouped providers (always show header as a drop target)
        out.add(ModelRow.UngroupedHeader("散修"))
        ungroupedProviders.forEach { p ->
            out.add(ModelRow.ProviderRow(providerId = p.id, title = p.name, subtitle = p.baseUrl, groupId = null))
        }

        return out
    }

    private fun buildProviderSubtitle(p: ProviderEntity?, m: ModelEntity?): String {
        val base = p?.baseUrl ?: ""
        val modelStr = if (m != null) {
            "model=${m.nickname} (${m.modelName})" + (if (m.multimodal) " 多模态" else "")
        } else {
            ""
        }
        return listOf(base, modelStr).filter { it.isNotBlank() }.joinToString("  ")
    }

    fun addProvider(name: String, baseUrl: String) {
        val n = name.trim()
        val b = baseUrl.trim()
        if (n.isEmpty() || b.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.providers().insert(ProviderEntity(name = n, baseUrl = b))
            }
        }
    }

    fun addGroup(name: String) {
        val t = name.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.modelGroups().insert(ModelGroupEntity(name = t)) }
        }
    }

    fun renameGroup(groupId: Long, name: String) {
        val t = name.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val g = db.modelGroups().getById(groupId) ?: return@withContext
                db.modelGroups().update(g.copy(name = t))
            }
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.modelGroups().deleteById(groupId) }
        }
    }

    fun deleteProvider(providerId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.providers().deleteById(providerId) }
        }
    }

    fun modelOptionsForProvider(providerId: Long): List<ModelEntity> {
        // Use current observed cache if available; fallback to sync read in background.
        val cached = modelsLD.value
        if (cached != null) return cached.filter { it.providerId == providerId }
        return emptyList()
    }

    fun setProviderModelInGroup(providerId: Long, modelId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Provider might exist in at most one group (current app semantics).
                val entry = db.groupProviders().listAll().firstOrNull { it.providerId == providerId } ?: return@withContext
                db.groupProviders().update(entry.copy(modelId = modelId))
            }
        }
    }

    fun renameProvider(providerId: Long, name: String) {
        val t = name.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val p = db.providers().getById(providerId) ?: return@withContext
                db.providers().update(p.copy(name = t))
            }
        }
    }

    fun updateProviderBaseUrl(providerId: Long, baseUrl: String) {
        val t = baseUrl.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val p = db.providers().getById(providerId) ?: return@withContext
                db.providers().update(p.copy(baseUrl = t))
            }
        }
    }

    fun detachProvider(providerId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val entries = db.groupProviders().listAll().filter { it.providerId == providerId }
                entries.forEach { db.groupProviders().deleteById(it.id) }
            }
        }
    }

    /**
     * Called after drag. Best-effort mapping:
     * - If moved into the section under a group header, attach to that group with next orderIndex.
     * - If moved below all groups, detach (become ungrouped).
     */
    fun commitDrop(providerId: Long, toPos: Int, snapshot: List<ModelRow>) {
        // Compute effective group assignment from the tree snapshot.
        // (Don't trust ProviderRow.groupId during drag; it may be stale.)
        val effectiveGroupByProvider = HashMap<Long, Long?>()
        val orderByGroup = HashMap<Long, ArrayList<Long>>()

        var currentGroupId: Long? = null
        snapshot.forEach { r ->
            when (r) {
                is ModelRow.GroupHeader -> currentGroupId = r.groupId
                is ModelRow.UngroupedHeader -> currentGroupId = null
                is ModelRow.ProviderRow -> {
                    effectiveGroupByProvider[r.providerId] = currentGroupId
                    if (currentGroupId != null) {
                        val list = orderByGroup.getOrPut(currentGroupId!!) { ArrayList() }
                        if (!list.contains(r.providerId)) list.add(r.providerId)
                    }
                }
            }
        }

        // Target group is the effective group at the provider's current position.
        val targetGroupId: Long? = effectiveGroupByProvider[providerId]

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (targetGroupId == null) {
                    // detach: remove any groupProviders entries for this provider
                    val entries = db.groupProviders().listAll().filter { it.providerId == providerId }
                    entries.forEach { db.groupProviders().deleteById(it.id) }
                    // Reindex the previous group if any (best-effort)
                    return@withContext
                }

                // Ensure entry exists in target group
                val allEntries = db.groupProviders().listAll().toMutableList()
                val existing = allEntries.firstOrNull { it.providerId == providerId }
                val models = db.models().listAll().filter { it.providerId == providerId }
                if (models.isEmpty()) return@withContext

                val entryId = if (existing == null) {
                    db.groupProviders().insert(
                        GroupProviderEntity(
                            groupId = targetGroupId,
                            providerId = providerId,
                            modelId = models[0].id,
                            orderIndex = 0,
                            enabled = true
                        )
                    )
                } else {
                    // If moving between groups, update groupId now
                    if (existing.groupId != targetGroupId) {
                        db.groupProviders().update(existing.copy(groupId = targetGroupId))
                    }
                    existing.id
                }

                // Reorder within target group according to snapshot order
                val desired = orderByGroup[targetGroupId] ?: ArrayList()
                if (!desired.contains(providerId)) desired.add(providerId)

                val groupEntries = db.groupProviders().listByGroup(targetGroupId)
                val byProvider = groupEntries.associateBy { it.providerId }

                desired.forEachIndexed { idx, pid ->
                    val e = byProvider[pid] ?: return@forEachIndexed
                    if (e.orderIndex != idx) db.groupProviders().update(e.copy(orderIndex = idx))
                }
            }
        }
    }
}
