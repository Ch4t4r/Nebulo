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
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.util.preferences.Theme
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            val openWithChrome = { url:String ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.setPackage("com.android.chrome")
                startActivity(intent)
        }
            val websites = setOf("bild.de", "washingtonpost.com", "cnn.com", "bbc.com", "nytimes.com", "huffingtonpost.com",
                "reuters.com", "abcnews.go.com", "timesofindia.indiatimes.com", "theguardian.com", "bloomberg.com",
                "Oneindia.com", "News18.com", "Hindustantimes.com", "Firstpost.com", "Indianexpress.com", "Manoramaonline.com",
                "spiegel.de", "focus.de", "n-tv.de", "welt.de", "faz.net", "stern.de", "t3n.de", "facebook.com", "twitter.com",
                "baidu.com", "yahoo.com", "instagram.com", "vk.com", "wikipedia.org", "qq.com", "taobao.com", "tmail.com",
                "google.co.in", "google.com", "google.de", "reddit.com", "sohu.com", "live.com", "jd.com", "yandex.ru",
                "weibo.com", "sina.com.cn", "google.co.jp", "360.cn", "login.tmail.com", "blogspot.com", "netflix.com",
                "google.com.hk", "linkedin.com", "google.com.br", "google.co.uk", "yahoo.co.jp", "csdn.net", "pages.tmail.com",
                "twitch.tv", "google.ru", "google.fr", "alipay.com", "office.com", "ebay.com", "microsoft.com", "bing.com",
                "microsoftonline.com", "aliexpress.com", "msn.com", "naver.com", "ebay-kleinanzeigen.de", "paypal.com",
                "t-online.de", "chip.de", "heise.de", "golem.de", "otto.de", "postbank.de", "whatsapp.com", "mobile.de",
                "wetter.com", "wetter.de", "tumblr.com", "booking.com", "idealo.de", "bahn.de", "amazon.com", "amazon.de",
                "ebay.de", "google.ch", "20min.ch", "blinck.ch", "srf.ch", "ricardo.ch", "bluewin.ch", "sbb.ch",
                "postfinance.ch", "digitec.ch", "admin.ch", "gmx.de", "imdb.net", "gmx.at", "gmx.de", "tribunnews.com",
                "stackoverflow.com", "apple.com", "wordpress.com", "imgur.com", "wikia.com", "amazon.co.uk", "pinterest.com",
                "adobe.com", "amazon.in", "dropbox.com", "quora.com", "google.es", "google.cn", "amazonaws.com",
                "salesforce.com", "chase.com", "spotify.com", "telegram.org", "steampowered.com", "skype.com", "sky.de",
                "sky.com", "teamspeak.com", "maps.google.com", "9gag.com", "vw.de", "discord.gg", "nytimes.com",
                "stackexchange.com", "craigslist.com", "soundcloud.com", "vimeo.com", "panda.tv", "ask.com",
                "steamcommunity.com", "softonic.com", "dailymotion.com", "ebay.co.uk", "godaddy.com", "discordapp.com",
                "vice.com", "walmart.com", "alibaba.com", "amazon.es", "cnet.com", "google.pl", "yelp.com", "duckduckgo.com",
                "blogger.com", "wellsfargo.com", "deviantart.com", "wikihow.com", "dailymail.co.uk", "shutterstock.com",
                "gamepedia.com", "amazon.ca", "udemy.com", "ikea.de", "ikea.com", "speedtest.com", "medium.com", "hulu.com",
                "tripadvisor.com", "archive.org", "forbes.com", "airbnb.com", "genius.com", "americanexpress.com", "google.com.ua",
                "businessinsider.com", "bitcoin.com", "bitcoin.de", "glassdor.com", "fiverr.com", "crunchyroll.com",
                "sourceforge.net", "samsung.com", "fedex.com", "target.com", "google.gr", "dell.com", "lenovo.com",
                "playstation.com", "siteadvisor.com", "hola.com", "oracle.com", "cnbc.com", "news.google.de", "upwork.com",
                "icloud.com", "wp.pl", "nike.com", "web.de", "sohu.com", "weibo.com", "csdn.net", "mail.ru", "t.co", "naver.com",
                "github.com", "msn.de", "googleusercontent.com", "lovoo.com","tinder.com","lovoo.de","tinder.de","gmail.com",
                "viber.com", "hp.com", "snapchat.com", "minecraft.net", "minecraft.de", "minecraft.com", "mojang.com",
                "bitmoji.com", "messenger.com", "cleanmasterofficial.com", "king.com", "itunes.apple.com", "line.me",
                "flipboard.com", "translate.google.com", "uber.com", "pandora.com", "wish.com", "tiktok.com",
                "fortnite.com", "epicgames.com", "geoguessr.com", "asoftmurmur.com", "camelcamelcamel.com",
                "hackertyper.net", "xkcd.com", "flickr.com", "bit.ly", "w3.org", "europa.eu", "wp.com", "statcounter.com",
                "jimdo.com", "weebly.com", "mozilla.org", "myspace.com", "stumpleupon.com", "gravatar.com",
                "digg.com", "wixsite.com", "wix.com", "e-recht24.de", "slideshare.net", "telegraph.co.uk", "amzn.to",
                "livejournal.com", "bing.com", "time.com", "immobilienscout24.de", "check24.de", "computerbild.de",
                "dhl.de", "chefkoch.de", "booking.com", "mediamarkt.de", "idealo.de", "zdf.de", "gutefrage.net",
                "pr0gramm.com", "statista.com", "germanglobe.com", "alexa.com", "tribunnews.com",
                "Bukalapak.com", "Detik.com", "Google.co.id", "Tokopedia.com", "kompas.com", "Liputan6.com", "okezone.com",
                "Sindonews.com", "grid.id", "Kumparan.com", "Merdeka.com", "Blibli.com", "Kapanlagi.com", "Uzone.id",
                "Alodokter.com", "cnnindonesia.com", "viva.co.id", "viva.com", "brilio.net", "vidio.com", "Tempo.co",
                "suara.com", "bola.net", "shopee.co.id", "wowkeren.com", "popads.net", "Academia.edu", "imdb.com",
                "Instructure.com", "Etsy.com", "Bankofamerica.com", "force.com", "zillow.com", "bestbuy.com",
                "Mercadolivre.com.br", "globo.com", "bet365.com", "fbcdn.net"
                ).toList().shuffled()
            GlobalScope.launch {
                for (website in websites) {
                    openWithChrome("http://$website")
                    delay(30000)
                    DnsVpnService.restartVpn(requireContext(), false)
                    delay(2000)
                }
            }
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