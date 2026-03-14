package com.haidianfirstteam.nostalgiaai.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.haidianfirstteam.nostalgiaai.data.entities.AppSettingEntity
import kotlinx.coroutines.runBlocking

class SettingsRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TAVILY_BASE_URL = "tavily_base_url"

        // Stored as integer string in tenths. Range: 5..50 => 0.5x..5.0x
        const val KEY_FONT_SCALE_X10 = "font_scale_x10"

        const val KEY_STREAM_MODE = "stream_mode" // off | on | compat
        const val KEY_STREAM_COMPAT_INTERVAL_MS = "stream_compat_interval_ms" // e.g. 500
    }

    fun getFontScaleX10Blocking(): Int = runBlocking {
        val raw = db.appSettings().get(KEY_FONT_SCALE_X10)?.value
        raw?.toIntOrNull()?.coerceIn(5, 50) ?: 10
    }

    fun setFontScaleX10Blocking(x10: Int) = runBlocking {
        val v = x10.coerceIn(5, 50)
        db.appSettings().put(AppSettingEntity(KEY_FONT_SCALE_X10, v.toString()))
    }

    fun getFontScaleBlocking(): Float {
        // Avoid float drift by deriving from x10.
        return getFontScaleX10Blocking() / 10f
    }

    fun getThemeModeBlocking(): Int = runBlocking {
        val v = db.appSettings().get(KEY_THEME_MODE)?.value
        when (v) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    fun setThemeModeBlocking(mode: Int) = runBlocking {
        val v = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> "light"
            AppCompatDelegate.MODE_NIGHT_YES -> "dark"
            else -> "system"
        }
        db.appSettings().put(AppSettingEntity(KEY_THEME_MODE, v))
    }

    fun getTavilyBaseUrlBlocking(): String = runBlocking {
        db.appSettings().get(KEY_TAVILY_BASE_URL)?.value ?: "https://api.tavily.com"
    }

    fun setTavilyBaseUrlBlocking(url: String) = runBlocking {
        db.appSettings().put(AppSettingEntity(KEY_TAVILY_BASE_URL, url.trim()))
    }

    fun getStreamModeBlocking(): String = runBlocking {
        db.appSettings().get(KEY_STREAM_MODE)?.value ?: "on"
    }

    fun setStreamModeBlocking(mode: String) = runBlocking {
        db.appSettings().put(AppSettingEntity(KEY_STREAM_MODE, mode.trim()))
    }

    fun getCompatStreamIntervalMsBlocking(): Long = runBlocking {
        val raw = db.appSettings().get(KEY_STREAM_COMPAT_INTERVAL_MS)?.value
        raw?.toLongOrNull()?.coerceAtLeast(50L) ?: 500L
    }

    fun setCompatStreamIntervalMsBlocking(ms: Long) = runBlocking {
        db.appSettings().put(AppSettingEntity(KEY_STREAM_COMPAT_INTERVAL_MS, ms.coerceAtLeast(50L).toString()))
    }
}
