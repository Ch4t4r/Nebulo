package com.frostnerd.smokescreen.util.preferences

import android.content.Context
import android.content.SharedPreferences
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.preferenceskt.restrictedpreferences.restrictedCollection
import com.frostnerd.preferenceskt.typedpreferences.SimpleTypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.buildMigration
import com.frostnerd.preferenceskt.typedpreferences.cache.ExpirationCacheControl
import com.frostnerd.preferenceskt.typedpreferences.cache.buildCacheStrategy
import com.frostnerd.preferenceskt.typedpreferences.types.*
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.dialog.HostSourceRefreshDialog
import java.util.*

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
interface AppSettings {
    companion object {
        internal var instance: AppSettingsSharedPreferences? = null
        val isReleaseVersion = BuildConfig.VERSION_NAME.let {
            !it.contains("alpha", true) && !it.contains("beta", true)
        }
        val isBetaVersion = BuildConfig.VERSION_NAME.let {
            it.contains("beta", true)
        }
        val isAlphaVersion = BuildConfig.VERSION_NAME.let {
            it.contains("alpha", true)
        }
    }
    val cacheControl:ExpirationCacheControl


    var catchKnownDnsServers: Boolean
    val defaultBypassPackages: Set<String>
    var dnsServerConfig: DnsServerInformation<*>
    var userServers: Set<UserServerConfiguration>
    var crashReportingConsent:Boolean
    var crashReportingConsentAsked:Boolean
    var crashReportingUUID:String
    var lastLogId:Int

    // ###### Settings (in order)
    // No Category
    var theme: Theme
    var startAppOnBoot: Boolean
    var startAppAfterUpdate: Boolean
    var userBypassPackages: MutableSet<String>
    var isBypassBlacklist: Boolean

    // Notification category
    var showNotificationOnLockscreen: Boolean
    var hideNotificationIcon: Boolean
    var allowPauseInNotification:Boolean
    var allowStopInNotification:Boolean

    // Pin category
    var enablePin:Boolean
    var allowFingerprintForPin:Boolean
    var pin: String

    // Cache category
    var useDnsCache: Boolean
    var keepDnsCacheAcrossLaunches: Boolean
    var maxCacheSize: Int
    var useDefaultDnsCacheTime: Boolean
    var minimumCacheTime:Int
    var customDnsCacheTime: Int
    var nxDomainCacheTime:Int

    // Logging category
    var loggingEnabled: Boolean


    // IP category
    var enableIpv6: Boolean
    var enableIpv4: Boolean
    var forceIpv6: Boolean
    var forceIpv4: Boolean
    var allowIpv6Traffic:Boolean
    var allowIpv4Traffic:Boolean

    // Network category
    var disallowOtherVpns: Boolean

    var bypassSearchdomains: Boolean
    var pauseOnCaptivePortal:Boolean
    var showNoConnectionNotification:Boolean

    // Query logging category
    var queryLoggingEnabled: Boolean
    // ###### End of settings


    var hasRatedApp: Boolean
    var previousInstalledVersion:Int // Maintained in ChangelogDialog
    var showChangelog:Boolean // Maintained in ChangelogDialog
    var exportedQueryCount:Int
    var totalAppLaunches:Int
    var askedForGroupJoin:Boolean

    fun addUserServerConfiguration(info:DnsServerInformation<*>):UserServerConfiguration {
        var max = 0
        for (server in userServers) {
            if (server.id >= max) max = server.id + 1
        }
        val mutableServers = userServers.toMutableSet()
        val config = UserServerConfiguration(max, info)
        mutableServers.add(config)
        userServers = mutableServers
        return config
    }

    fun addUserServerConfiguration(infos:List<DnsServerInformation<*>>) {
        var max = 0
        for (server in userServers) {
            if (server.id >= max) max = server.id + 1
        }
        val mutableServers = userServers.toMutableSet()
        for (info in infos) {
            val config = UserServerConfiguration(max++, info)
            mutableServers.add(config)
        }
        userServers = mutableServers
    }

    fun removeUserServerConfiguration(config:UserServerConfiguration) {
        val mutableServers = userServers.toMutableSet()
        val iterator = mutableServers.iterator()
        for (configuration in iterator) {
            if(config.id == configuration.id) iterator.remove()
        }
        userServers = mutableServers
    }
}

class AppSettingsSharedPreferences(context: Context) : AppSettings, SimpleTypedPreferences(context, version = 1, migrate = migration) {
    override val cacheControl: ExpirationCacheControl = ExpirationCacheControl(buildCacheStrategy {
        this.allKeys {
            neverExpires()
        }
    })
    init {
        filterForeignKeys = false
    }

    var lastCrashTimeStamp:Long? by longPref("last_crash_timestamp")

    override var hasRatedApp: Boolean by booleanPref("has_rated_app", false)
    var hasAskedRateApp:Boolean by booleanPref("asked_rate_app", false)
    override var previousInstalledVersion:Int by nonOptionalOf(intPref("previous_version"),true, BuildConfig.VERSION_CODE)
    override var showChangelog:Boolean by booleanPref("show_changelog", true)
    override var exportedQueryCount:Int by intPref("exported_query_count", 0)
    override var crashReportingConsent: Boolean by booleanPref("sentry_consent", false)
    override var crashReportingConsentAsked: Boolean by booleanPref("sentry_consent_asked", false)
    override var crashReportingUUID: String by nonOptionalOf(stringPref("sentry_id"), true, UUID.randomUUID().toString())
    override var totalAppLaunches: Int by intPref("total_app_launches", 0)
    override var askedForGroupJoin: Boolean by booleanPref("asked_group_join", false)
    override var lastLogId: Int by intPref("last_log_id", 0)

    var language:String by stringPref("language", "auto")
    override var theme: Theme by ThemePreference("theme", Theme.MONO)
    override var startAppOnBoot: Boolean by booleanPref("start_on_boot", true)
    override var startAppAfterUpdate: Boolean by booleanPref("start_after_update", true)
    override var userBypassPackages by mutableStringSetPref("user_bypass_packages", mutableSetOf("com.android.vending", "ch.threema.app.work", "ch.threema.app"))
    override var isBypassBlacklist: Boolean by booleanPref("user_bypass_blacklist", true)
    var fallbackDns: DnsServerInformation<*>? by cache(DnsServerInformationPreference("fallback_dns_server"), cacheControl)

    override var showNotificationOnLockscreen: Boolean by booleanPref("show_notification_on_lockscreen", true)
    var simpleNotification:Boolean by booleanPref("simple_notification", false)
    override var hideNotificationIcon: Boolean by booleanPref("hide_notification_icon", false)
    override var allowPauseInNotification: Boolean by booleanPref("notification_allow_pause", true)
    override var allowStopInNotification: Boolean by booleanPref("notification_allow_stop", true)
    var showNotificationOnRevoked:Boolean by booleanPref("show_vpn_revoked_notification", true)

    override var enablePin:Boolean by booleanPref("enable_pin", false)
    override var allowFingerprintForPin:Boolean by booleanPref("pin_allow_fingerprint", true)
    override var pin: String by stringPref("pin", "1234")
    override var useDnsCache: Boolean by booleanPref("dnscache_enabled", false)
    override var keepDnsCacheAcrossLaunches: Boolean by booleanPref("dnscache_keepacrosslaunches", false)
    override var maxCacheSize: Int by stringBasedIntPref("dnscache_maxsize", 1000)
    override var useDefaultDnsCacheTime: Boolean by booleanPref("dnscache_use_default_time", true)
    override var minimumCacheTime: Int by stringBasedIntPref("dnscache_minimum_time", 10)
    override var customDnsCacheTime: Int by stringBasedIntPref("dnscache_custom_time", 100)
    override var nxDomainCacheTime: Int by stringBasedIntPref("dnscache_nxdomain_cachetime", 1800)
    override var loggingEnabled: Boolean by booleanPref(
        "logging_enabled",
        AppSettings.isAlphaVersion || BuildConfig.DEBUG
    )
    fun shouldLogDnsQueriesToConsole():Boolean = loggingEnabled && (!AppSettings.isReleaseVersion || advancedLogging || BuildConfig.DEBUG)
    var advancedLogging:Boolean by booleanPref(
        "advanced_logging",
        false
    )
    private var oldCrashreportSetting: Boolean by booleanPref("enable_sentry", false)
    var crashreportingType: Crashreporting by CrashReportingPreferenceWithDefault("crashreporting_type") {
        if (oldCrashreportSetting) Crashreporting.FULL else Crashreporting.MINIMAL
    }

    override var enableIpv6: Boolean by booleanPref("ipv6_enabled", true)
    override var enableIpv4: Boolean by booleanPref("ipv4_enabled", true)
    override var forceIpv6: Boolean by booleanPref("force_ipv6", false)
    override var forceIpv4: Boolean by booleanPref("force_ipv4", false)
    override var allowIpv4Traffic: Boolean by booleanPref("allow_ipv4_traffic", true)
    override var allowIpv6Traffic: Boolean by booleanPref("allow_ipv6_traffic", true)


    override var disallowOtherVpns: Boolean by booleanPref("disallow_other_vpns", false)
    var restartVpnOnNetworkChange:Boolean by booleanPref("restart_vpn_networkchange", false)
    override var bypassSearchdomains: Boolean by booleanPref("bypass_searchdomains", true)
    override var pauseOnCaptivePortal: Boolean by booleanPref("pause_on_captive_portal", true)
    override var showNoConnectionNotification:Boolean by booleanPref("show_no_connection_notification", false)
    var mapQueryRefusedToHostBlock:Boolean by booleanPref("map_query_refused", true)

    override var queryLoggingEnabled: Boolean by booleanPref("log_dns_queries", false)

    override var userServers: Set<UserServerConfiguration> by cache(UserServerConfigurationPreference(
        "user_servers"
    ) { mutableSetOf() }, cacheControl)
    override var catchKnownDnsServers: Boolean by booleanPref("catch_known_servers", true)
    override val defaultBypassPackages: Set<String> by cache(restrictedCollection(
        stringSetPref(
            "default_bypass_packages",
            hashSetOf(BuildConfig.APPLICATION_ID)
        )
    ) {
        shouldContain(BuildConfig.APPLICATION_ID)
    }, cacheControl)
    override var dnsServerConfig: DnsServerInformation<*> by cache(DnsServerInformationPreference("dns_server_config"), cacheControl)
        .toNonOptionalPreference(true) {
            AbstractTLSDnsHandle.waitUntilKnownServersArePopulated(-1) { knownServers ->
                knownServers.getValue(9)
            }
        }

    var customHostsEnabled:Boolean by booleanPref("custom_hosts", true)
    var dnsRulesEnabled:Boolean by booleanPref("dns_rules_enabled", false)
    var hostSourcesVersion:Int by intPref("dns_rules_sources_version", 0)

    var removedDefaultDoTServers:Set<Int> by intPref<SharedPreferences>("removed_dohserver_id").toSetPreference(emptySet())
    var removedDefaultDoHServers:Set<Int> by intPref<SharedPreferences>("removed_dotserver_id").toSetPreference(emptySet())

    var vpnServiceState:VpnServiceState by enumPref("vpn_service_state", VpnServiceState.STOPPED)
    var ignoreServiceKilled:Boolean by booleanPref("ignore_service_killed", false)
    var vpnLaunchLastVersion:Int by intPref("vpn_last_version", 43)

    var automaticHostRefresh:Boolean by booleanPref("automatic_host_refresh", false)
    var automaticHostRefreshWifiOnly:Boolean by booleanPref("automatic_host_refresh_wifi_only", true)
    var automaticHostRefreshTimeUnit:HostSourceRefreshDialog.TimeUnit by enumPref("automatic_host_refresh_timeunit", HostSourceRefreshDialog.TimeUnit.HOURS)
    var automaticHostRefreshTimeAmount:Int by intPref("automatic_host_refresh_timeamount", 12)

    var vpnInformationShown:Boolean by booleanPref("vpn_information_shown", false)

    var runWithoutVpn:Boolean by booleanPref("run_without_vpn", false)
    var dnsServerModePort:Int by stringBasedIntPref("non_vpn_server_port", 11053)
    var nonVpnUseIptables:Boolean by booleanPref("nonvpn_use_iptables", false)
    var lastIptablesRedirectAddress:String? by stringPref("nonvpn_iptables_last_address")
    var lastIptablesRedirectAddressIPv6:String? by stringPref("nonvpn_iptables_last_address_ipv6")
    var iptablesModeDisableIpv6:Boolean by booleanPref("nonvpn_iptables_disable_ipv6", false)
}

fun AppSettings.Companion.fromSharedPreferences(context: Context): AppSettingsSharedPreferences {
    return if (instance == null) {
        instance =
                AppSettingsSharedPreferences(context)
        instance!!
    } else {
        instance!!
    }
}

private val migration = buildMigration {
    initialMigration { _, _, _ ->

    }
}

enum class Crashreporting(val value:String) {
    FULL("full"), MINIMAL("minimal"), OFF("off")
}