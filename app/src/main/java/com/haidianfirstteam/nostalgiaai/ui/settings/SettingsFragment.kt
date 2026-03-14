package com.haidianfirstteam.nostalgiaai.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val app = requireActivity().application as NostalgiaApp

        val themePref = findPreference<ListPreference>("theme_mode")
        themePref?.setOnPreferenceChangeListener { _, newValue ->
            val mode = when (newValue as String) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            app.settingsRepository.setThemeModeBlocking(mode)
            AppCompatDelegate.setDefaultNightMode(mode)
            true
        }

        findPreference<Preference>("providers")?.setOnPreferenceClickListener {
            startActivity(ModelsActivity.newIntent(requireContext()))
            true
        }

        findPreference<Preference>("tavily")?.setOnPreferenceClickListener {
            startActivity(TavilyActivity.newIntent(requireContext()))
            true
        }

        findPreference<Preference>("import_export")?.setOnPreferenceClickListener {
            startActivity(ImportExportActivity.newIntent(requireContext()))
            true
        }

        findPreference<Preference>("about")?.setOnPreferenceClickListener {
            startActivity(AboutActivity.newIntent(requireContext()))
            true
        }

        // Font scale (0.5x..5.0x, step 0.1x) stored in app_settings as x10 int.
        val fontPref = findPreference<SeekBarPreference>(SettingsRepository.KEY_FONT_SCALE_X10)
        fun updateFontSummary(x10: Int) {
            val s = (x10.coerceIn(5, 50) / 10f)
            fontPref?.summary = String.format("%.1fx", s)
        }
        if (fontPref != null) {
            // Load initial value from DB.
            CoroutineScope(Dispatchers.Main).launch {
                val x10 = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    app.settingsRepository.getFontScaleX10Blocking()
                }
                fontPref.value = x10
                updateFontSummary(x10)
            }
            fontPref.setOnPreferenceChangeListener { _, newValue ->
                val x10 = (newValue as? Int) ?: 10
                updateFontSummary(x10)
                // Persist first, then recreate so new configuration is picked up.
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        app.settingsRepository.setFontScaleX10Blocking(x10)
                    }
                    // Apply immediately in this Settings screen; other screens will recreate onResume.
                    activity?.recreate()
                }
                true
            }
        }

        // Streaming output settings
        val streamPref = findPreference<ListPreference>(SettingsRepository.KEY_STREAM_MODE)
        streamPref?.setOnPreferenceChangeListener { _, newValue ->
            CoroutineScope(Dispatchers.IO).launch {
                app.db.appSettings().put(com.haidianfirstteam.nostalgiaai.data.entities.AppSettingEntity(SettingsRepository.KEY_STREAM_MODE, (newValue as String)))
            }
            true
        }
        val intervalPref = findPreference<EditTextPreference>(SettingsRepository.KEY_STREAM_COMPAT_INTERVAL_MS)
        intervalPref?.setOnPreferenceChangeListener { _, newValue ->
            val raw = (newValue as String).trim()
            val ms = raw.toLongOrNull()?.coerceAtLeast(50L) ?: 500L
            CoroutineScope(Dispatchers.IO).launch {
                app.db.appSettings().put(com.haidianfirstteam.nostalgiaai.data.entities.AppSettingEntity(SettingsRepository.KEY_STREAM_COMPAT_INTERVAL_MS, ms.toString()))
            }
            true
        }
    }
}
