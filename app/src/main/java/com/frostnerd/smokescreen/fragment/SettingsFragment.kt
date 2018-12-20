package com.frostnerd.smokescreen.fragment

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.frostnerd.general.isInt
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.dialog.AppChoosalDialog
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
    private var preferenceListener: ((SharedPreferences, String) -> Unit)? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onPause() {
        super.onPause()
        if (preferenceListener != null) requireContext().getPreferences().sharedPreferences.unregisterOnSharedPreferenceChangeListener(
            preferenceListener
        )
        preferenceListener = null
    }

    override fun onResume() {
        super.onResume()
        preferenceListener = { _, key ->
            requireContext().getPreferences().notifyPreferenceChangedFromExternal(key)
        }
        requireContext().getPreferences().sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)

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
            showExcludedAppsDialog()
            true
        }
        findPreference("send_logs").setOnPreferenceClickListener {
            log("Trying to send logs..")
            val zipFile = requireContext().zipAllLogFiles()
            if (zipFile != null) {
                val zipUri =
                    FileProvider.getUriForFile(requireContext(), "com.frostnerd.smokescreen.LogZipProvider", zipFile)
                showLogExportDialog(zipUri)
            } else log("Cannot send, zip file is null.")
            true
        }
        findPreference("delete_logs").setOnPreferenceClickListener {
            showLogDeletionDialog()
            true
        }
        processCacheCategory()
        processLoggingCategory()
        processNetworkCategory()
    }

    private fun processNetworkCategory() {
        val ipv6 = findPreference("ipv6_enabled") as CheckBoxPreference
        val ipv4 = findPreference("ipv4_enabled") as CheckBoxPreference
        val forceIpv6 = findPreference("force_ipv6") as CheckBoxPreference
        val forceIpv4 = findPreference("force_ipv4") as CheckBoxPreference

        val updateState = { ipv6Enabled: Boolean, ipv4Enabled: Boolean ->
            ipv4.isEnabled = ipv6Enabled
            ipv6.isEnabled = ipv4Enabled
            forceIpv6.isEnabled = ipv6Enabled && ipv6.isEnabled
            forceIpv4.isEnabled = ipv4Enabled && ipv4.isEnabled
        }
        updateState(ipv6.isChecked, ipv4.isChecked)
        ipv6.setOnPreferenceChangeListener { _, newValue ->
            updateState(newValue as Boolean, ipv4.isChecked)
            true
        }
        ipv4.setOnPreferenceChangeListener { _, newValue ->
            updateState(ipv6.isChecked, newValue as Boolean)
            true
        }
    }

    private fun processCacheCategory() {
        val cacheEnabled = findPreference("dnscache_enabled") as CheckBoxPreference
        val keepAcrossLaunches = findPreference("dnscache_keepacrosslaunches") as CheckBoxPreference
        val cacheMaxSize = findPreference("dnscache_maxsize") as EditTextPreference
        val useDefaultTime = findPreference("dnscache_use_default_time") as CheckBoxPreference
        val cacheTime = findPreference("dnscache_custom_time") as EditTextPreference


        val updateState = { isCacheEnabled: Boolean, isUsingDefaultTime: Boolean ->
            cacheMaxSize.isEnabled = isCacheEnabled
            useDefaultTime.isEnabled = isCacheEnabled
            cacheTime.isEnabled = isCacheEnabled && !isUsingDefaultTime
            keepAcrossLaunches.isEnabled = isCacheEnabled
        }
        updateState(cacheEnabled.isChecked, useDefaultTime.isChecked)
        cacheTime.summary = getString(
            R.string.summary_dnscache_customcachetime,
            requireContext().getPreferences().customDnsCacheTime
        )

        cacheMaxSize.setOnPreferenceChangeListener { _, newValue ->
            newValue.toString().isInt()
        }
        cacheEnabled.setOnPreferenceChangeListener { _, newValue ->
            updateState(newValue as Boolean, useDefaultTime.isChecked)
            true
        }
        useDefaultTime.setOnPreferenceChangeListener { _, newValue ->
            updateState(cacheEnabled.isChecked, newValue as Boolean)
            true
        }
        cacheTime.setOnPreferenceChangeListener { _, newValue ->
            if (newValue.toString().isInt()) {
                cacheTime.summary = getString(R.string.summary_dnscache_customcachetime, newValue.toString().toInt())
                true
            } else {
                false
            }
        }
    }

    private fun processLoggingCategory() {
        val loggingEnabled = findPreference("logging_enabled") as CheckBoxPreference
        val sendLogs = findPreference("send_logs")
        val deleteLogs = findPreference("delete_logs")
        loggingEnabled.isChecked = requireContext().getPreferences().loggingEnabled
        sendLogs.isEnabled = loggingEnabled.isChecked
        deleteLogs.isEnabled = loggingEnabled.isChecked
        loggingEnabled.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            if (!enabled) log("Logging disabled from settings.") // Log before disabling
            Logger.enabledGlobally = (enabled)
            if (!enabled) requireContext().closeLogger()
            if (enabled) log("Logging enabled from settings.") // Log after enabling
            sendLogs.isEnabled = enabled
            deleteLogs.isEnabled = enabled
            true
        }
    }

    private fun showLogDeletionDialog() {
        AlertDialog.Builder(requireContext(), requireContext().getPreferences().theme.dialogStyle)
            .setTitle(R.string.title_delete_all_logs)
            .setMessage(R.string.dialog_deletelogs_text)
            .setPositiveButton(R.string.all_yes) { dialog, _ ->
                requireContext().deleteAllLogs()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.all_no) { dialog, _ ->
                dialog.dismiss()
            }.setCancelable(true).show()
    }

    private fun showExcludedAppsDialog() {
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
            }
        }.createDialog()
        dialog.setTitle(R.string.title_excluded_apps)
        dialog.show()
    }

    private fun showLogExportDialog(zipUri: Uri) {
        AlertDialog.Builder(requireContext(), requireContext().getPreferences().theme.dialogStyle)
            .setTitle(R.string.title_send_logs)
            .setMessage(R.string.dialog_logexport_text)
            .setCancelable(true)
            .setPositiveButton(R.string.dialog_logexport_email) { dialog, _ ->
                log("User choose to send logs over E-Mail")
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
                startActivity(Intent.createChooser(emailIntent, getString(R.string.title_send_logs)))
                log("Now choosing chooser for E-Mail intent")
                dialog.dismiss()
            }
            .setNeutralButton(R.string.dialog_logexport_general) { dialog, _ ->
                log("User choose to send logs via general export")
                val generalIntent = Intent(Intent.ACTION_SEND)
                generalIntent.putExtra(Intent.EXTRA_TEXT, "")
                generalIntent.type = "application/zip"
                generalIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " -- logs")
                for (receivingApps in requireContext().packageManager.queryIntentActivities(
                    generalIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )) {
                    requireContext().grantUriPermission(
                        receivingApps.activityInfo.packageName,
                        zipUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                generalIntent.putExtra(Intent.EXTRA_STREAM, zipUri)
                generalIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                log("Now choosing chooser for general export")
                startActivity(Intent.createChooser(generalIntent, getString(R.string.title_send_logs)))
                dialog.dismiss()
            }.show()
    }

}