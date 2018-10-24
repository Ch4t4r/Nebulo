package com.frostnerd.smokescreen.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import com.frostnerd.general.IntentUtil
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.util.Preferences

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
            val newTheme = Preferences.Theme.findById(id)

            if(newTheme != null) {
                Preferences.getInstance(requireContext()).setTheme(newTheme)
                IntentUtil.restartActivity(requireActivity())
                true
            } else {
                false
            }
        }
    }

}