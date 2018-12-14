package com.frostnerd.smokescreen.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.dialog.AppChoosalDialog
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.restart
import com.frostnerd.smokescreen.util.preferences.AppSettings
import com.frostnerd.smokescreen.util.preferences.Theme

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findPreference("theme").setOnPreferenceChangeListener { _, newValue ->
            val id = (newValue as? String)?.toInt() ?: newValue as Int
            val newTheme = Theme.findById(id)

            if (newTheme != null) {
                requireContext().getPreferences().theme = newTheme
                requireActivity().restart()
                true
            } else {
                false
            }
        }
        findPreference("app_exclusion_list").setOnPreferenceClickListener {
            val dialog = AppChoosalDialog(
                requireActivity(),
                requireContext().getPreferences().userBypassPackages,
                requireContext().getPreferences().defaultBypassPackages,
                getString(R.string.dialog_excludedapps_infotext, requireContext().getPreferences().defaultBypassPackages.size)
            ) { selected ->
                requireContext().getPreferences().userBypassPackages = selected
            }.createDialog()
            dialog.setTitle("Test")
            dialog.show()
            true
        }
    }

}