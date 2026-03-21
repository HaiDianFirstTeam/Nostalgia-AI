package com.haidianfirstteam.nostalgiaai.ui.tutorial

import android.content.Context

class TutorialPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("tutorial_prefs", Context.MODE_PRIVATE)

    fun isAllDisabled(): Boolean = sp.getBoolean(KEY_DISABLE_ALL, false)

    fun disableAll() {
        sp.edit().putBoolean(KEY_DISABLE_ALL, true).apply()
    }

    fun resetAll() {
        sp.edit()
            .remove(KEY_DISABLE_ALL)
            .remove(KEY_SHOWN_SET)
            .apply()
    }

    fun shouldShow(screenKey: String): Boolean {
        if (isAllDisabled()) return false
        val shown = sp.getStringSet(KEY_SHOWN_SET, emptySet()) ?: emptySet()
        return !shown.contains(screenKey)
    }

    fun markShown(screenKey: String) {
        val old = sp.getStringSet(KEY_SHOWN_SET, emptySet()) ?: emptySet()
        val next = HashSet(old)
        next.add(screenKey)
        sp.edit().putStringSet(KEY_SHOWN_SET, next).apply()
    }

    companion object {
        private const val KEY_DISABLE_ALL = "disable_all"
        private const val KEY_SHOWN_SET = "shown_set"
    }
}
