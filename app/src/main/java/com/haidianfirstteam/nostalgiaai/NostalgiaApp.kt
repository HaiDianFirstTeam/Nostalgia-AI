package com.haidianfirstteam.nostalgiaai

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDex
import com.haidianfirstteam.nostalgiaai.data.AppDatabase
import com.haidianfirstteam.nostalgiaai.data.SettingsRepository

class NostalgiaApp : Application() {

    lateinit var db: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Required for API < 21 when multiDexEnabled.
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        com.haidianfirstteam.nostalgiaai.util.CrashGuard.install(this)
        db = AppDatabase.get(this)
        settingsRepository = SettingsRepository(this, db)
        applyThemeFromSettings()
    }

    private fun applyThemeFromSettings() {
        val mode = settingsRepository.getThemeModeBlocking()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
