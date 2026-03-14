package com.haidianfirstteam.nostalgiaai.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

data class ChatTargetUi(
    val title: String,
    val target: ChatTarget,
    val multimodalPossible: Boolean
)

class ChatSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NostalgiaApp).db

    private val _targets = MutableLiveData<List<ChatTargetUi>>(emptyList())
    val targets: LiveData<List<ChatTargetUi>> = _targets

    private val _selected = MutableLiveData<ChatTargetUi?>()
    val selected: LiveData<ChatTargetUi?> = _selected

    // Observe DB changes so targets update in real time (no need to start a new conversation).
    private val providersLD = db.providers().observeAll()
    private val modelsLD = db.models().observeAll()
    private val groupsLD = db.modelGroups().observeAll()
    private val groupProvidersLD = db.groupProviders().observeAll()

    private val dbChanges = MediatorLiveData<Unit>().apply {
        fun ping() { value = Unit }
        addSource(providersLD) { ping() }
        addSource(modelsLD) { ping() }
        addSource(groupsLD) { ping() }
        addSource(groupProvidersLD) { ping() }
    }

    init {
        dbChanges.observeForever {
            // Rebuild targets on any change.
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { buildTargets() }
            _targets.value = list

            // Preserve selection when possible.
            val cur = _selected.value
            if (cur == null) {
                if (list.isNotEmpty()) _selected.value = list[0]
            } else {
                val idx = list.indexOfFirst { it.target == cur.target }
                _selected.value = if (idx >= 0) list[idx] else list.firstOrNull()
            }
        }
    }

    // When groups/providers/models are edited, caller should call refresh().

    fun selectByIndex(index: Int) {
        val list = _targets.value ?: emptyList()
        if (index in list.indices) {
            _selected.value = list[index]
        }
    }

    private suspend fun buildTargets(): List<ChatTargetUi> {
        val providers: List<ProviderEntity> = db.providers().listAll()
        val models: List<ModelEntity> = db.models().listAll()
        val groups: List<ModelGroupEntity> = db.modelGroups().listAll()
        val groupProviders: List<GroupProviderEntity> = db.groupProviders().listAll()

        val modelsByProvider = models.groupBy { it.providerId }
        val providerById = providers.associateBy { it.id }
        val modelById = models.associateBy { it.id }

        val groupHasMultimodal = HashMap<Long, Boolean>()
        for (g in groups) {
            // Determine multimodal possible for group:
            // prefer group_providers mapping (provider+model)
            val gp = groupProviders.filter { it.groupId == g.id && it.enabled }
            val anyMulti = gp.asSequence().mapNotNull { modelById[it.modelId] }.any { it.multimodal }
            groupHasMultimodal[g.id] = anyMulti
        }

        val out = ArrayList<ChatTargetUi>()

        // Groups first
        groups.sortedBy { it.name }.forEach { g ->
            out.add(
                ChatTargetUi(
                    title = "组: ${g.name}",
                    target = ChatTarget.Group(g.id),
                    multimodalPossible = groupHasMultimodal[g.id] == true
                )
            )
        }

        // Then direct models
        providers.sortedBy { it.name }.forEach { p ->
            val ms = modelsByProvider[p.id].orEmpty().sortedBy { it.nickname }
            ms.forEach { m ->
                val suffix = if (m.multimodal) "（多模态）" else ""
                out.add(
                    ChatTargetUi(
                        title = "模型: ${m.nickname} @${p.name} $suffix",
                        target = ChatTarget.DirectModel(providerId = p.id, modelId = m.id),
                        multimodalPossible = m.multimodal
                    )
                )
            }
        }

        return out
    }

    override fun onCleared() {
        super.onCleared()
        // Avoid leaking observer.
        dbChanges.removeSource(providersLD)
        dbChanges.removeSource(modelsLD)
        dbChanges.removeSource(groupsLD)
        dbChanges.removeSource(groupProvidersLD)
    }
}
