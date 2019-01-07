package com.frostnerd.smokescreen.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.frostnerd.general.isInt
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.activity.MainActivity
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.AppChoosalDialog
import com.frostnerd.smokescreen.dialog.QueryGeneratorDialog
import com.frostnerd.smokescreen.util.preferences.Theme

/*
 * Copyright (C) 2019 Daniel Wolf (Ch4t4r)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the developer at daniel.wolf@frostnerd.com.
 */
class SettingsFragment : PreferenceFragmentCompat() {
    private var werePreferencesAdded = false
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            requireContext().getPreferences().notifyPreferenceChangedFromExternal(key)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        log("Adding preferences from resources...")
        setPreferencesFromResource(R.xml.preferences, rootKey)
        log("Preferences added.")
        werePreferencesAdded = true
        createPreferenceListener()
    }

    override fun onPause() {
        super.onPause()
        log("Pausing fragment")
        removePreferenceListener()
    }

    override fun onResume() {
        super.onResume()
        log("Resuming fragment")
        if (werePreferencesAdded) createPreferenceListener()
    }

    override fun onDetach() {
        log("Fragment detached.")
        removePreferenceListener()
        super.onDetach()
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        log("Fragment attached.")
        if (werePreferencesAdded) createPreferenceListener()
    }

    private fun createPreferenceListener() {
        requireContext().getPreferences().sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun removePreferenceListener() {
        requireContext().getPreferences().sharedPreferences.unregisterOnSharedPreferenceChangeListener(
            preferenceListener
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("Fragment created")
        findPreference("theme").setOnPreferenceChangeListener { _, newValue ->
            val id = (newValue as? String)?.toInt() ?: newValue as Int
            val newTheme = Theme.findById(id)

            log("Updated theme to $newValue")
            if (newTheme != null) {
                removePreferenceListener()
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
            requireContext().showLogExportDialog()
            true
        }
        findPreference("delete_logs").setOnPreferenceClickListener {
            showLogDeletionDialog()
            true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hideIconPreference = findPreference("hide_notification_icon")
            hideIconPreference.isEnabled = false
            hideIconPreference.isVisible = false
        }
        if (!requireContext().getPreferences().isUsingKeweon()) {
            val terminateKeweonPreference = findPreference("null_terminate_keweon")
            terminateKeweonPreference.isVisible = false
            terminateKeweonPreference.isEnabled = false
        }
        processGeneralCategory()
        processCacheCategory()
        processLoggingCategory()
        processIPCategory()
        processNetworkCategory()
        processQueryCategory()
    }

    private fun processQueryCategory() {
        val queryLogging = findPreference("log_dns_queries")
        val exportQueries = findPreference("export_dns_queries")
        val generateQueries = findPreference("generate_queries")

        if(!BuildConfig.DEBUG) generateQueries.isVisible = false

        queryLogging.setOnPreferenceChangeListener { _, newValue ->
            requireContext().getPreferences().queryLoggingEnabled = newValue as Boolean
            (requireActivity() as MainActivity).reloadMenuItems()
            true
        }
        exportQueries.summary = getString(R.string.summary_export_queries, requireContext().getDatabase().dnsQueryDao().getCount())
        exportQueries.setOnPreferenceClickListener {
            requireContext().getDatabase().dnsQueryRepository().exportQueriesAsCsvAsync(requireContext()) {file ->
                val uri = FileProvider.getUriForFile(requireContext(), "com.frostnerd.smokescreen.LogZipProvider", file)
                val exportIntent = Intent(Intent.ACTION_SEND)
                exportIntent.putExtra(Intent.EXTRA_TEXT, "")
                exportIntent.type = "text/csv"
                exportIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " -- Logged Queries")
                for (receivingApps in requireContext().packageManager.queryIntentActivities(
                    exportIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )) {
                    requireContext().grantUriPermission(
                        receivingApps.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                exportIntent.putExtra(Intent.EXTRA_STREAM, uri)
                exportIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                startActivity(Intent.createChooser(exportIntent, getString(R.string.title_export_queries)))
            }
            true
        }
        generateQueries.setOnPreferenceClickListener {
            QueryGeneratorDialog(requireContext())
            true
        }
    }

    @SuppressLint("NewApi")
    private fun processGeneralCategory() {
        val startOnBoot = findPreference("start_on_boot") as CheckBoxPreference
        startOnBoot.setOnPreferenceChangeListener { preference, newValue ->
            if (newValue == false) true
            else {
                if (requireContext().isAppBatteryOptimized()) {
                    showBatteryOptimizationDialog {
                        startOnBoot.isChecked = true
                    }
                    false
                } else true
            }
        }
    }

    private fun processIPCategory() {
        val ipv6 = findPreference("ipv6_enabled") as CheckBoxPreference
        val ipv4 = findPreference("ipv4_enabled") as CheckBoxPreference
        val forceIpv6 = findPreference("force_ipv6") as CheckBoxPreference
        val forceIpv4 = findPreference("force_ipv4") as CheckBoxPreference
        val allowIpv6Traffic = findPreference("allow_ipv6_traffic") as CheckBoxPreference
        val allowIpv4Traffic = findPreference("allow_ipv4_traffic") as CheckBoxPreference

        val updateState = { ipv6Enabled: Boolean, ipv4Enabled: Boolean ->
            ipv4.isEnabled = ipv6Enabled
            ipv6.isEnabled = ipv4Enabled
            forceIpv6.isEnabled = ipv6Enabled && ipv6.isEnabled
            forceIpv4.isEnabled = ipv4Enabled && ipv4.isEnabled
            allowIpv6Traffic.isEnabled = !ipv6Enabled
            allowIpv4Traffic.isEnabled = !ipv4Enabled
            if(!ipv6.isChecked && ipv6Enabled) allowIpv6Traffic.isChecked = true
            if(!ipv4.isChecked && ipv4Enabled) allowIpv4Traffic.isChecked = true
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

    private fun processNetworkCategory() {

    }

    private fun processCacheCategory() {
        val cacheEnabled = findPreference("dnscache_enabled") as CheckBoxPreference
        val keepAcrossLaunches = findPreference("dnscache_keepacrosslaunches") as CheckBoxPreference
        val cacheMaxSize = findPreference("dnscache_maxsize") as EditTextPreference
        val useDefaultTime = findPreference("dnscache_use_default_time") as CheckBoxPreference
        val minCacheTime = findPreference("dnscache_minimum_time") as EditTextPreference
        val cacheTime = findPreference("dnscache_custom_time") as EditTextPreference

        val updateState = { isCacheEnabled: Boolean, isUsingDefaultTime: Boolean ->
            cacheMaxSize.isEnabled = isCacheEnabled
            useDefaultTime.isEnabled = isCacheEnabled
            cacheTime.isEnabled = isCacheEnabled && !isUsingDefaultTime
            keepAcrossLaunches.isEnabled = isCacheEnabled
            minCacheTime.isEnabled = isUsingDefaultTime && isCacheEnabled
        }
        updateState(cacheEnabled.isChecked, useDefaultTime.isChecked)
        cacheTime.summary = getString(
            R.string.summary_dnscache_customcachetime,
            requireContext().getPreferences().customDnsCacheTime
        )
        minCacheTime.summary = getString(
            R.string.summary_dnscache_minimum_cache_time,
            requireContext().getPreferences().minimumCacheTime
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
        minCacheTime.setOnPreferenceChangeListener { _, newValue ->
            if (newValue.toString().isInt()) {
                minCacheTime.summary = getString(R.string.summary_dnscache_minimum_cache_time, newValue.toString().toInt())
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

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showBatteryOptimizationDialog(enablePreference: () -> Unit) {
        AlertDialog.Builder(requireContext(), requireContext().getPreferences().theme.dialogStyle)
            .setTitle(R.string.dialog_batteryoptimization_title)
            .setMessage(R.string.dialog_batteryoptimization_message)
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.dialog_batteryoptimization_whitelist) { dialog, _ ->
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                dialog.dismiss()
            }
            .setNeutralButton(R.string.dialog_batteryoptimization_ignore) { dialog, _ ->
                enablePreference()
                dialog.dismiss()
            }.show()
    }

    private fun showExcludedAppsDialog() {
        val dialog = AppChoosalDialog(
            requireActivity(),
            requireContext().getPreferences().userBypassPackages,
            defaultChosenUnselectablePackages = requireContext().getPreferences().defaultBypassPackages,
            infoText = getString(
                R.string.dialog_excludedapps_infotext,
                requireContext().getPreferences().defaultBypassPackages.size
            ),
            blackList = requireContext().getPreferences().isBypassBlacklist
        ) { selected, isBlacklist ->
            requireContext().getPreferences().isBypassBlacklist = isBlacklist
            if (selected.size != requireContext().getPreferences().userBypassPackages.size) {
                log("Updated the list of user bypass packages to $selected")
                requireContext().getPreferences().userBypassPackages = selected
            }
        }.createDialog()
        dialog.setTitle(R.string.title_excluded_apps)
        dialog.show()
    }
}

fun Context.showLogExportDialog(onDismiss: (() -> Unit)? = null) {
    log("Trying to send logs..")
    val zipFile = this.zipAllLogFiles()
    if (zipFile != null) {
        val zipUri =
            FileProvider.getUriForFile(this, "com.frostnerd.smokescreen.LogZipProvider", zipFile)
        showLogExportDialog(zipUri, onDismiss)
    } else log("Cannot send, zip file is null.")
}

private fun Context.showLogExportDialog(zipUri: Uri, onDismiss: (() -> Unit)? = null) {
    val dialog = AlertDialog.Builder(this, this.getPreferences().theme.dialogStyle)
        .setTitle(R.string.title_send_logs)
        .setMessage(R.string.dialog_logexport_text)
        .setCancelable(true)
        .setPositiveButton(R.string.dialog_logexport_email) { dialog, _ ->
            log("User choose to send logs over E-Mail")
            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "support@frostnerd.com", null))
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " -- logs")
            emailIntent.putExtra(Intent.EXTRA_TEXT, "")
            emailIntent.putExtra(Intent.EXTRA_EMAIL, "support@frostnerd.com")
            for (receivingApps in this.packageManager.queryIntentActivities(
                emailIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )) {
                grantUriPermission(
                    receivingApps.activityInfo.packageName,
                    zipUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            emailIntent.putExtra(Intent.EXTRA_STREAM, zipUri)
            emailIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(Intent.createChooser(emailIntent, getString(R.string.title_send_logs)))
            log("Now choosing chooser for E-Mail intent")
            onDismiss?.invoke()
            dialog.dismiss()
        }
        .setNeutralButton(R.string.dialog_logexport_general) { dialog, _ ->
            log("User choose to send logs via general export")
            val generalIntent = Intent(Intent.ACTION_SEND)
            generalIntent.putExtra(Intent.EXTRA_TEXT, "")
            generalIntent.type = "application/zip"
            generalIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " -- logs")
            for (receivingApps in packageManager.queryIntentActivities(
                generalIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )) {
                grantUriPermission(
                    receivingApps.activityInfo.packageName,
                    zipUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            generalIntent.putExtra(Intent.EXTRA_STREAM, zipUri)
            generalIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            log("Now choosing chooser for general export")
            startActivity(Intent.createChooser(generalIntent, getString(R.string.title_send_logs)))
            onDismiss?.invoke()
            dialog.dismiss()
        }.create()
    dialog.setCanceledOnTouchOutside(false)
    dialog.show()
}