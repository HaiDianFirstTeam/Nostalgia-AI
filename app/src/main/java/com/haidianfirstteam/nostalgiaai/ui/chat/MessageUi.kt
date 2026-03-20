package com.haidianfirstteam.nostalgiaai.ui.chat

data class MessageUi(
    val id: Long,
    val role: String,
    val content: String,
    val createdAt: Long,
    val webLinks: List<WebLinkUi> = emptyList(),

    // Branch/variant navigation for this message node.
    // When a message has siblings under the same parent, user can switch which variant is active.
    val branchEnabled: Boolean = false,
    val branchIndex: Int = 1,
    val branchTotal: Int = 1
)

data class WebLinkUi(
    val title: String,
    val url: String
)
