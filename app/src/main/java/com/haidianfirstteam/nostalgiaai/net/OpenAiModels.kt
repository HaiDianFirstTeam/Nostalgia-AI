package com.haidianfirstteam.nostalgiaai.net

import com.google.gson.annotations.SerializedName

data class OpenAiChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<OpenAiMessage>,
    @SerializedName("temperature") val temperature: Double? = null,
    @SerializedName("stream") val stream: Boolean? = null
)

data class OpenAiMessage(
    @SerializedName("role") val role: String,
    // For multimodal, this should be array of parts; we keep flexible with Any.
    @SerializedName("content") val content: Any
)

data class OpenAiChatResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("choices") val choices: List<OpenAiChoice> = emptyList(),
    @SerializedName("error") val error: OpenAiError? = null
)

data class OpenAiChoice(
    @SerializedName("index") val index: Int,
    @SerializedName("message") val message: OpenAiMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class OpenAiError(
    @SerializedName("message") val message: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("code") val code: String? = null
)
