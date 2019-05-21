package com.frostnerd.smokescreen.util.preferences

import android.content.Context
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.preferenceskt.restrictedpreferences.restrictedCollection
import com.frostnerd.preferenceskt.typedpreferences.SimpleTypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.buildMigration
import com.frostnerd.preferenceskt.typedpreferences.cache.ExpirationCacheControl
import com.frostnerd.preferenceskt.typedpreferences.cache.buildCacheStrategy
import com.frostnerd.preferenceskt.typedpreferences.types.*
import com.frostnerd.smokescreen.BuildConfig
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
    }
    val cacheControl:ExpirationCacheControl


    var catchKnownDnsServers: Boolean
    var dummyDnsAddressIpv4: String
    var dummyDnsAddressIpv6: String
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
    var pin:Int

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
    var crashReportingEnabled:Boolean


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
    var nullTerminateKeweon: Boolean
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

    fun isUsingKeweon(): Boolean {
        return dnsServerConfig.servers.any {
            val host = it.address.host ?: ""
            host.contains("keweon") || host.contains("asecdns.com") || host.contains("asecdns.ch")
        }
    }

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

    fun shouldShowCrashReportingConsentDialog(): Boolean {
        return BuildConfig.VERSION_NAME.let {
            it.contains("alpha", true) || it.contains("beta", true)
        } && !crashReportingConsent && !crashReportingConsentAsked
    }
}

class AppSettingsSharedPreferences(context: Context) : AppSettings, SimpleTypedPreferences(context, version = 1, migrate = migration) {
    override val cacheControl: ExpirationCacheControl = ExpirationCacheControl(buildCacheStrategy {
        this.allKeys {
            neverExpires()
        }
    })

    override var hasRatedApp: Boolean by booleanPref("has_rated_app", false)
    override var previousInstalledVersion:Int by nonOptionalOf(intPref("previous_version"),true, BuildConfig.VERSION_CODE)
    override var showChangelog:Boolean by booleanPref("show_changelog", true)
    override var exportedQueryCount:Int by intPref("exported_query_count", 0)
    override var crashReportingConsent: Boolean by booleanPref("sentry_consent", false)
    override var crashReportingConsentAsked: Boolean by booleanPref("sentry_consent_asked", false)
    override var crashReportingUUID: String by nonOptionalOf(stringPref("sentry_id"), true, UUID.randomUUID().toString())
    override var totalAppLaunches: Int by intPref("total_app_launches", 0)
    override var askedForGroupJoin: Boolean by booleanPref("asked_group_join", false)
    override var lastLogId: Int by intPref("last_log_id", 0)

    override var theme: Theme by ThemePreference("theme", Theme.MONO)
    override var startAppOnBoot: Boolean by booleanPref("start_on_boot", true)
    override var startAppAfterUpdate: Boolean by booleanPref("start_after_update", true)
    override var userBypassPackages by mutableStringSetPref("user_bypass_packages", mutableSetOf())
    override var isBypassBlacklist: Boolean by booleanPref("user_bypass_blacklist", true)

    override var showNotificationOnLockscreen: Boolean by booleanPref("show_notification_on_lockscreen", true)
    override var hideNotificationIcon: Boolean by booleanPref("hide_notification_icon", false)
    override var allowPauseInNotification: Boolean by booleanPref("notification_allow_pause", true)
    override var allowStopInNotification: Boolean by booleanPref("notification_allow_stop", true)

    override var enablePin:Boolean by booleanPref("enable_pin", false)
    override var allowFingerprintForPin:Boolean by booleanPref("pin_allow_fingerprint", true)
    override var pin: Int by stringBasedIntPref("pin", 1234)
    override var useDnsCache: Boolean by booleanPref("dnscache_enabled", true)
    override var keepDnsCacheAcrossLaunches: Boolean by booleanPref("dnscache_keepacrosslaunches", false)
    override var maxCacheSize: Int by stringBasedIntPref("dnscache_maxsize", 1000)
    override var useDefaultDnsCacheTime: Boolean by booleanPref("dnscache_use_default_time", true)
    override var minimumCacheTime: Int by stringBasedIntPref("dnscache_minimum_time", 10)
    override var customDnsCacheTime: Int by stringBasedIntPref("dnscache_custom_time", 100)
    override var nxDomainCacheTime: Int by stringBasedIntPref("dnscache_nxdomain_cachetime", 1800)
    override var loggingEnabled: Boolean by booleanPref(
        "logging_enabled",
        BuildConfig.VERSION_NAME.contains("alpha", true) || BuildConfig.VERSION_NAME.contains("beta", true)
    )
    override var crashReportingEnabled: Boolean by booleanPref("enable_sentry", false)

    override var enableIpv6: Boolean by booleanPref("ipv6_enabled", true)
    override var enableIpv4: Boolean by booleanPref("ipv4_enabled", true)
    override var forceIpv6: Boolean by booleanPref("force_ipv6", false)
    override var forceIpv4: Boolean by booleanPref("force_ipv4", false)
    override var allowIpv4Traffic: Boolean by booleanPref("allow_ipv4_traffic", true)
    override var allowIpv6Traffic: Boolean by booleanPref("allow_ipv6_traffic", true)


    override var disallowOtherVpns: Boolean by booleanPref("disallow_other_vpns", false)
    override var bypassSearchdomains: Boolean by booleanPref("bypass_searchdomains", true)
    override var nullTerminateKeweon: Boolean by booleanPref("null_terminate_keweon", false)
    override var pauseOnCaptivePortal: Boolean by booleanPref("pause_on_captive_portal", true)
    override var showNoConnectionNotification:Boolean by booleanPref("show_no_connection_notification", false)

    override var queryLoggingEnabled: Boolean by booleanPref("log_dns_queries", false)

    override var userServers: Set<UserServerConfiguration> by cache(UserServerConfigurationPreference(
        "user_servers"
    ) { mutableSetOf() }, cacheControl)
    override var catchKnownDnsServers: Boolean by booleanPref("catch_known_servers", false)
    override var dummyDnsAddressIpv4: String by stringPref("dummy_dns_ipv4", "203.0.113.244")
    override var dummyDnsAddressIpv6: String by stringPref("dummy_dns_ipv6", "fd21:c5ea:169d:fff1:3418:d688:36c5:e8c2")
    override val defaultBypassPackages: Set<String> by cache(restrictedCollection(
        stringSetPref(
            "default_bypass_packages",
            hashSetOf(BuildConfig.APPLICATION_ID, "com.android.vending")
        )
    ) {
        shouldContain(BuildConfig.APPLICATION_ID)
        shouldContain("com.android.vending")
    }, cacheControl)
    override var dnsServerConfig: DnsServerInformation<*> by cache(DnsServerInformationPreference("dns_server_config") {
        AbstractTLSDnsHandle.waitUntilKnownServersArePopulated(500) { knownServers ->
            knownServers.getValue(9)
        }
    }, cacheControl)
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