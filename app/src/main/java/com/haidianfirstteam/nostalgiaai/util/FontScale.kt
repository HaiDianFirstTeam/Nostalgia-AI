package com.haidianfirstteam.nostalgiaai.util

import android.content.Context
import android.content.res.Configuration

object FontScale {
    /**
     * Wrap context with a custom fontScale for this app only.
     *
     * API 19+ supports createConfigurationContext().
     */
    fun wrap(base: Context, scale: Float): Context {
        val s = scale.coerceIn(0.5f, 5.0f)
        val current = base.resources?.configuration?.fontScale ?: 1.0f
        if (kotlin.math.abs(current - s) < 0.0001f) return base

        val config = Configuration(base.resources.configuration)
        config.fontScale = s
        return base.createConfigurationContext(config)
    }
}
