package com.haidianfirstteam.nostalgiaai.ui.chat

sealed class ChatTarget {
    data class Group(val groupId: Long) : ChatTarget()
    data class DirectModel(val providerId: Long, val modelId: Long) : ChatTarget()
}
