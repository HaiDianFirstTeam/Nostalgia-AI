package com.haidianfirstteam.nostalgiaai.ui.chat

data class MessageUi(
    val id: Long,
    val role: String,
    val content: String,
    val createdAt: Long
)
