package com.frostnerd.smokescreen.util.preferences

import android.content.Context
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.preferenceskt.restrictedpreferences.restrictedCollection
import com.frostnerd.preferenceskt.typedpreferences.SimpleTypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.buildMigration
import com.frostnerd.preferenceskt.typedpreferences.types.*
import com.frostnerd.smokescreen.BuildConfig

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

    var catchKnownDnsServers: Boolean
    var dummyDnsAddressIpv4: String
    var dummyDnsAddressIpv6: String
    val defaultBypassPackages: Set<String>
    var dnsServerConfig: DnsServerInformation<*>
    var userServers: Set<UserServerConfiguration>

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

    // Query logging category
    var queryLoggingEnabled: Boolean
    // ###### End of settings


    var hasRatedApp: Boolean
    var previousInstalledVersion:Int // Maintained in ChangelogDialog
    var showChangelog:Boolean // Maintained in ChangelogDialog

    fun isUsingKeweon(): Boolean {
        return dnsServerConfig.servers.any {
            it.address.FQDN?.contains("keweon") ?: false
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
}

class AppSettingsSharedPreferences(context: Context) : AppSettings, SimpleTypedPreferences(context, version = 1, migrate = migration) {
    override var hasRatedApp: Boolean by booleanPref("has_rated_app", false)
    override var previousInstalledVersion:Int by intPref("previous_version", 22)
    override var showChangelog:Boolean by booleanPref("show_changelog", true)

    override var theme: Theme by ThemePreference("theme", Theme.MONO)
    override var startAppOnBoot: Boolean by booleanPref("start_on_boot", true)
    override var startAppAfterUpdate: Boolean by booleanPref("start_after_update", true)
    override var userBypassPackages by mutableStringSetPref("user_bypass_packages", mutableSetOf())
    override var isBypassBlacklist: Boolean by booleanPref("user_bypass_blacklist", true)

    override var showNotificationOnLockscreen: Boolean by booleanPref("show_notification_on_lockscreen", true)
    override var hideNotificationIcon: Boolean by booleanPref("hide_notification_icon", false)

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

    override var queryLoggingEnabled: Boolean by booleanPref("log_dns_queries", false)

    override var userServers: Set<UserServerConfiguration> by UserServerConfigurationPreference(
        "user_servers"
    ) { mutableSetOf() }
    override var catchKnownDnsServers: Boolean by booleanPref("catch_known_servers", false)
    override var dummyDnsAddressIpv4: String by stringPref("dummy_dns_ipv4", "8.8.8.8")
    override var dummyDnsAddressIpv6: String by stringPref("dummy_dns_ipv6", "2001:4860:4860::8888")
    override val defaultBypassPackages: Set<String> by restrictedCollection(
        stringSetPref(
            "default_bypass_packages",
            hashSetOf(BuildConfig.APPLICATION_ID, "com.android.vending")
        )
    ) {
        shouldContain(BuildConfig.APPLICATION_ID)
        shouldContain("com.android.vending")
    }
    override var dnsServerConfig: DnsServerInformation<*> by DnsServerInformationPreference("dns_server_config") {
        AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated(500) { knownServers ->
            knownServers.getValue(0)
        }
    }
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
    initialMigration { _, _ ->

    }
}