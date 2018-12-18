package com.frostnerd.smokescreen.fragment

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.preference.PreferenceFragmentCompat
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.dialog.AppChoosalDialog
import com.frostnerd.smokescreen.service.DnsVpnService
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
        log("Fragment created")
        findPreference("theme").setOnPreferenceChangeListener { _, newValue ->
            val id = (newValue as? String)?.toInt() ?: newValue as Int
            val newTheme = Theme.findById(id)

            log("Updated theme to $newValue")
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
                defaultChosenUnselectablePackages = requireContext().getPreferences().defaultBypassPackages,
                infoText = getString(
                    R.string.dialog_excludedapps_infotext,
                    requireContext().getPreferences().defaultBypassPackages.size
                )
            ) { selected ->
                if (selected.size != requireContext().getPreferences().userBypassPackages.size) {
                    log("Updated the list of user bypass packages to $selected")
                    requireContext().getPreferences().userBypassPackages = selected
                    if (requireContext().isServiceRunning(DnsVpnService::class.java)) {
                        DnsVpnService.restartVpn(requireContext(), false)
                    }
                }
            }.createDialog()
            dialog.setTitle(R.string.title_excluded_apps)
            dialog.show()
            true
        }
        findPreference("send_logs").setOnPreferenceClickListener {
            log("Trying to send logs..")
            val zipFile = requireContext().zipAllLogFiles()
            if (zipFile != null) {
                val zipUri = FileProvider.getUriForFile(requireContext(), "com.frostnerd.smokescreen.LogZipProvider", zipFile)
                val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "support@frostnerd.com", null))
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " -- logs")
                emailIntent.putExtra(Intent.EXTRA_TEXT, "")
                emailIntent.putExtra(Intent.EXTRA_EMAIL, "support@frostnerd.com")
                for (receivingApps in requireContext().packageManager.queryIntentActivities(
                    emailIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )) {
                    requireContext().grantUriPermission(
                        receivingApps.activityInfo.packageName,
                        zipUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                emailIntent.putExtra(Intent.EXTRA_STREAM, zipUri)
                emailIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                log("Now choosing chooser for E-Mail intent")
                startActivity(Intent.createChooser(emailIntent, getString(R.string.title_send_logs)))
            } else log("Cannot send, zip file is null.")
            true
        }
    }

}