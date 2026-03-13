package com.haidianfirstteam.nostalgiaai.domain

/**
 * OpenAI 兼容的 message content parts（最常用的是 text + image_url）。
 * 这里用 Map 表示，避免引入额外序列化复杂度。
 */
object MultimodalParts {
    fun text(t: String): Map<String, Any> = mapOf(
        "type" to "text",
        "text" to t
    )

    fun imageDataUrl(dataUrl: String): Map<String, Any> = mapOf(
        "type" to "image_url",
        "image_url" to mapOf("url" to dataUrl)
    )

    // OpenAI compatible audio part for Chat Completions (model-dependent)
    fun inputAudio(base64: String, format: String?): Map<String, Any> {
        val audio = LinkedHashMap<String, Any>()
        audio["data"] = base64
        if (!format.isNullOrBlank()) audio["format"] = format
        return mapOf(
            "type" to "input_audio",
            "input_audio" to audio
        )
    }
}
