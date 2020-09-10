package com.frostnerd.smokescreen.activity

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.*
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.frostnerd.dnstunnelproxy.KnownDnsServers
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.lifecyclemanagement.launchWithLifecycle
import com.frostnerd.navigationdraweractivity.NavigationDrawerActivity
import com.frostnerd.navigationdraweractivity.StyleOptions
import com.frostnerd.navigationdraweractivity.items.*
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.BatteryOptimizationInfoDialog
import com.frostnerd.smokescreen.dialog.ChangelogDialog
import com.frostnerd.smokescreen.dialog.ServerChoosalDialog
import com.frostnerd.smokescreen.fragment.*
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.util.DeepActionState
import com.frostnerd.smokescreen.util.LanguageContextWrapper
import com.frostnerd.smokescreen.util.preferences.VpnServiceState
import com.frostnerd.smokescreen.util.speedtest.DnsSpeedTest
import kotlinx.android.synthetic.main.menu_cardview.view.*
import kotlin.random.Random

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
class MainActivity : NavigationDrawerActivity() {
    companion object {
        const val BROADCAST_RELOAD_MENU = "main.reloadMenu"
        private const val PIN_TIMEOUT = 2*60*1000
    }
    override val drawerOverActionBar: Boolean = true
    private var textColor: Int = 0
    private var backgroundColor: Int = 0
    private var inputElementColor: Int = 0
    private var cardNetworkCallback:ConnectivityManager.NetworkCallback? = null
    private val networkManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private var pinLastPassed:Long? = null
    private var navigatedInternal = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageContextWrapper.attachFromSettings(this, newBase))
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        navigatedInternal = intent?.component?.packageName?.equals(packageName) ?: false
        super.startActivity(intent, options)
    }

    override fun onResume() {
        super.onResume()
        if(!navigatedInternal && getPreferences().enablePin) {
            pinLastPassed = intent?.getLongExtra("pin_validated_at", 0)
            if(pinLastPassed == null || System.currentTimeMillis() >= pinLastPassed!! + PIN_TIMEOUT) {
                startActivity(PinActivity.openAppIntent(this, intent?.extras))
                finish()
            }
        }
        navigatedInternal = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.layoutStyle)
        super.onCreate(savedInstanceState)
        AbstractHttpsDNSHandle // Loads the known servers.
        AbstractTLSDnsHandle
        KnownDnsServers
        setCardView { viewParent, suggestedHeight ->
            val view = layoutInflater.inflate(R.layout.menu_cardview, viewParent, false)
            val update = {
                launchWithLifecycle {
                    val server = getPreferences().dnsServerConfig
                    val primaryAddress = server.servers.first().address.addressCreator.resolveOrGetResultOrNull(
                        retryIfError = true,
                        runResolveNow = true
                    )?.firstOrNull()?.hostAddress ?: "-"
                    val secondaryAddress = (server.servers.lastOrNull()?.address?.addressCreator?.resolveOrGetResultOrNull(
                        retryIfError = true,
                        runResolveNow = true
                    )?.lastOrNull()?.hostAddress ?: "-").let {
                        if(it == primaryAddress) "-" else it
                    }

                    runOnUiThread {
                        view.serverName.text = server.name
                        view.dns1.text = primaryAddress
                        view.dns2.text = secondaryAddress
                    }
                }
            }
            update()
            getPreferences().listenForChanges(
                "dns_server_config",
                getPreferences().preferenceChangeListener {
                    update()
                }.unregisterOn(lifecycle)
            )
            cardNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    update()
                }
            }
            view.infoButton.setOnClickListener {
                showInfoTextDialogWithClose(this,
                        getString(R.string.dialog_latency_sidebar_title),
                        getString(R.string.dialog_latency_sidebar_message))
            }
            view.refresh.setOnClickListener {
                update()
            }
            networkManager.registerNetworkCallback(NetworkRequest.Builder().apply {
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            }.build(), cardNetworkCallback!!)
            view
        }
        supportActionBar?.elevation = 0f
        ChangelogDialog.showNewVersionChangelog(this)
        getPreferences().totalAppLaunches += 1
        if(getPreferences().totalAppLaunches >= 7 &&
            !getPreferences().askedForGroupJoin &&
            Random.nextInt(0,100) < 15 &&
                isPackageInstalled(this, "org.telegram.messenger")) {
            getPreferences().askedForGroupJoin = true
            showInfoTextDialog(this, getString(R.string.dialog_join_group_title), getString(R.string.dialog_join_group_message),
                getString(R.string.dialog_join_group_positive) to { dialog, _ ->
                    dialog.dismiss()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://join?invite=I54nRleveRGP8IPmcIdySg"))
                    startActivity(intent)
                }, getString(R.string.dialog_crashreporting_negative) to { dialog, _ ->
                    dialog.dismiss()
                }, null)
        } else if(getPreferences().totalAppLaunches > 6 && !getPreferences().hasAskedRateApp
            && Random.nextInt(0, 100) <= 15 && isPackageInstalled(this, "com.android.vending")
            && getPreferences().lastCrashTimeStamp?.let { System.currentTimeMillis() - it >= 12*60*60*1000 } != false
        ) {
            showInfoTextDialog(this, getString(R.string.dialog_raterequest_title),
                getString(R.string.dialog_raterequest_message),
                getString(R.string.dialog_join_group_positive) to { dialog, _ ->
                    dialog.dismiss()
                    rateApp()
                }, getString(R.string.dialog_crashreporting_negative) to { dialog, _ ->
                    dialog.dismiss()
                }, null)
            getPreferences().hasAskedRateApp = true
        }
        if(resources.getBoolean(R.bool.add_default_hostsources)) {
            val versionToStartFrom = getPreferences().hostSourcesVersion.let {
                when {
                    it != 0 -> it + 1 // Previous app version already had source versioning
                    getDatabase().hostSourceDao().getCount() == 0L -> 1 // Previous app version didn't have versioning and no sources
                    else -> 2 // Previous app version didn't have versioning, but had sources
                }
            }
            log("Updating host sources from $versionToStartFrom ")
            DnsRuleFragment.getDefaultHostSources(versionToStartFrom).apply {
                if(isNotEmpty()) {
                    log("Inserting ${this.size} hostsources")
                    getDatabase().hostSourceRepository().insertAllAsync(this)
                }
            }
            DnsRuleFragment.getUpdatedHostSources(versionToStartFrom).apply {
                if(isNotEmpty()) {
                    log("Updating ${this.size} hostsources")
                    getDatabase().hostSourceRepository().updateAllURLsAsync(this)
                }
            }
            getPreferences().hostSourcesVersion = DnsRuleFragment.latestSourcesVersion
        }
        handleDeepAction()
        if(!isServiceRunning(DnsVpnService::class.java) &&
            getPreferences().vpnServiceState == VpnServiceState.STARTED &&
            !getPreferences().ignoreServiceKilled &&
                getPreferences().vpnLaunchLastVersion == BuildConfig.VERSION_CODE) {
            getPreferences().vpnServiceState = VpnServiceState.STOPPED
            BatteryOptimizationInfoDialog(this).show()
        }
        registerLocalReceiver(listOf(BROADCAST_RELOAD_MENU), true) {
            reloadMenuItems()
        }

        @Suppress("ConstantConditionIf")
        if(!BuildConfig.DEBUG && BuildConfig.SENTRY_DSN == "dummy") {
            Toast.makeText(this, "Warning, Sentry DSN is not valid", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cardNetworkCallback?.let { networkManager.unregisterNetworkCallback(it) }
        cardNetworkCallback = null
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDeepAction(intent ?: this.intent)
    }

    private fun handleDeepAction(intent:Intent? = null) {
        if(intent?.hasExtra("deep_action") == true) {
            whenDrawerIsReady {
                when(val deepAction = intent.getSerializableExtra("deep_action")) {
                    DeepActionState.DNS_RULES -> {
                        clickItem(drawerItems.find {
                            it is ClickableDrawerItem && it.title == getString(R.string.button_main_dnsrules)
                        }!!)
                    }
                    DeepActionState.BATTERY_OPTIMIZATION_DIALOG -> {
                        BatteryOptimizationInfoDialog(this).show()
                    }
                    DeepActionState.DNSSERVERMODE_SETTINGS -> {
                        drawerItems.find {
                            it is ClickableDrawerItem && it.title == getString(R.string.menu_settings)
                        }?.apply {
                            clickItem(this, Bundle().apply { putSerializable("deep_action", deepAction) })
                        }
                    }
                }
            }
        }
    }

    override fun createDrawerItems(): MutableList<DrawerItem> {
        return createMenu {
            fragmentItem(getString(R.string.menu_main),
                iconLeft = getDrawable(R.drawable.ic_menu_dnsoverhttps),
                fragmentCreator = singleInstanceFragment { MainFragment() }
            )
            fragmentItem(getString(R.string.menu_settings),
                iconLeft = getDrawable(R.drawable.ic_menu_settings),
                fragmentCreator = singleInstanceFragment { args ->
                    SettingsOverviewFragment().also {
                    it.arguments = args
                } })
            if (getPreferences().queryLoggingEnabled) {
                divider()
                fragmentItem(getString(R.string.menu_querylogging),
                    iconLeft = getDrawable(R.drawable.ic_eye),
                    fragmentCreator = {
                        QueryLogFragment()
                    })

            }
            divider()
            clickableItem(getString(R.string.menu_create_shortcut),
                iconLeft = getDrawable(R.drawable.ic_external_link),
                onLongClick = null,
                onSimpleClick = { _, _, _ ->
                    ServerChoosalDialog(this@MainActivity, onEntrySelected = {
                        ShortcutActivity.createShortcut(this@MainActivity, it)
                    }).show()
                    false
                })
            fragmentItem(getString(R.string.button_main_dnsrules),
                iconLeft = getDrawable(R.drawable.ic_view_list),
                fragmentCreator = {
                    DnsRuleFragment()
                })
            divider()
            if (isPackageInstalled(this@MainActivity, "com.android.vending")) {
                clickableItem(getString(R.string.menu_rate),
                    iconLeft = getDrawable(R.drawable.ic_star),
                    onLongClick = null,
                    onSimpleClick = { _, _, _ ->
                        AlertDialog.Builder(this@MainActivity, getPreferences().theme.dialogStyle)
                            .setMessage(R.string.dialog_rate_confirmation)
                            .setPositiveButton(R.string.all_yes) { _, _ ->
                                rateApp()
                            }
                            .setNegativeButton(R.string.all_no) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                        false
                    })
            }
            if (isPackageInstalled(this@MainActivity, "org.fdroid.fdroid")) {
                clickableItem(getString(R.string.menu_show_on_fdroid),
                    iconLeft = getDrawable(R.drawable.ic_adb),
                    onLongClick = null,
                    onSimpleClick = { _, _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).setPackage("org.fdroid.fdroid"))
                        false
                    }
                )
            }
            fragmentItem(getString(R.string.menu_about),
                iconLeft = getDrawable(R.drawable.ic_info),
                fragmentCreator = singleInstanceFragment { AboutFragment() })
        }
    }

    override fun onBackPressed() {
        val fragment = currentFragment
        if (fragment != null && fragment is BackpressFragment) {
            if (!fragment.onBackPressed()) super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun rateApp() {
        val appPackageName = this.packageName
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$appPackageName")
                )
            )
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                    )
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.error_no_webbrowser_installed, Toast.LENGTH_LONG)
                    .show()
            }
        }
        getPreferences().hasRatedApp = true
    }

    override fun createStyleOptions(): StyleOptions {
        backgroundColor = getPreferences().theme.resolveAttribute(theme, android.R.attr.colorPrimary)
        textColor = getPreferences().theme.resolveAttribute(theme, android.R.attr.textColor)
        inputElementColor = getPreferences().theme.getColor(this, R.attr.inputElementColor, Color.WHITE)

        val options = StyleOptions()
        options.useDefaults()
        options.selectedListItemBackgroundColor = inputElementColor
        options.selectedListItemTextColor = textColor
        options.listItemBackgroundColor = backgroundColor
        options.listViewBackgroundColor = backgroundColor
        options.listItemTextColor = textColor
        options.headerTextColor = textColor
        options.alphaSelected = 1f
        options.iconTintLeft = getPreferences().theme.resolveAttribute(theme, R.attr.navDrawableColor)
        options.separatorColor = opaqueColor(options.iconTintLeft, 80)
        return options
    }

    override fun getConfiguration(): Configuration = Configuration.withDefaults()
    override fun getDefaultItem(): DrawerItem = drawerItems[0]
    override fun maxBackStackRecursion(): Int = 5
    override fun useItemBackStack(): Boolean = true

    override fun onItemClicked(item: DrawerItem, handle: Boolean) {
        if (item is BasicDrawerItem) {
            log("Menu item was clicked: '${item.title}'")
        }
    }
}
