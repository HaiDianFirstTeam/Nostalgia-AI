package com.haidianfirstteam.nostalgiaai.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.R

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
            startActivity(ProvidersActivity.newIntent(requireContext()))
            true
        }

        findPreference<Preference>("model_groups")?.setOnPreferenceClickListener {
            startActivity(ModelGroupsActivity.newIntent(requireContext()))
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
    }
}
