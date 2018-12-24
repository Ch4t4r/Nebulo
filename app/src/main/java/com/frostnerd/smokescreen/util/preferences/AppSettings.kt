package com.frostnerd.smokescreen.util.preferences

import android.content.Context
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.preferenceskt.restrictedpreferences.restrictedCollection
import com.frostnerd.preferenceskt.typedpreferences.SimpleTypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.*
import com.frostnerd.smokescreen.BuildConfig

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
interface AppSettings {
    companion object {
        internal var instance: AppSettingsSharedPreferences? = null
    }

    var catchKnownDnsServers: Boolean
    var dummyDnsAddressIpv4: String
    var dummyDnsAddressIpv6: String
    val defaultBypassPackages: Set<String>
    var areCustomServers: Boolean
    var primaryServerConfig: ServerConfiguration
    var secondaryServerConfig: ServerConfiguration?

    // ###### Settings (in order)
    // No Category
    var theme: Theme
    var startAppOnBoot: Boolean
    var startAppAfterUpdate: Boolean
    var userBypassPackages: MutableSet<String>
    var isBypassBlacklist:Boolean

    // Cache category
    var useDnsCache: Boolean
    var keepDnsCacheAcrossLaunches:Boolean
    var maxCacheSize:Int
    var useDefaultDnsCacheTime: Boolean
    var customDnsCacheTime: Int

    // Logging category
    var loggingEnabled:Boolean

    // Network category
    var disallowOtherVpns: Boolean
    var enableIpv6:Boolean
    var enableIpv4:Boolean
    var forceIpv6: Boolean
    var forceIpv4:Boolean
    var bypassSearchdomains:Boolean
    // ###### End of settings


    var hasRatedApp: Boolean
}

class AppSettingsSharedPreferences(context: Context) : AppSettings, SimpleTypedPreferences(context) {
    override var hasRatedApp: Boolean by booleanPref("has_rated_app", false)

    override var theme: Theme by ThemePreference("theme", Theme.MONO)
    override var startAppOnBoot: Boolean by booleanPref("start_on_boot", true)
    override var startAppAfterUpdate: Boolean by booleanPref("start_after_update", true)
    override var userBypassPackages by mutableStringSetPref("user_bypass_packages", mutableSetOf())
    override var isBypassBlacklist: Boolean by booleanPref("user_bypass_blacklist", true)

    override var useDnsCache: Boolean by booleanPref("dnscache_enabled", true)
    override var keepDnsCacheAcrossLaunches: Boolean by booleanPref("dnscache_keepacrosslaunches", false)
    override var maxCacheSize:Int by stringBasedIntPref("dnscache_maxsize", 1000)
    override var useDefaultDnsCacheTime: Boolean by booleanPref("dnscache_use_default_time", true)
    override var customDnsCacheTime: Int by stringBasedIntPref("dnscache_custom_time", 100)

    override var loggingEnabled: Boolean by booleanPref("logging_enabled", BuildConfig.VERSION_NAME.contains("alpha", true))

    override var disallowOtherVpns: Boolean by booleanPref("disallow_other_vpns", false)
    override var enableIpv6: Boolean by booleanPref("ipv6_enabled", true)
    override var enableIpv4: Boolean by booleanPref("ipv4_enabled", true)
    override var forceIpv6: Boolean by booleanPref("force_ipv6", false)
    override var forceIpv4: Boolean by booleanPref("force_ipv4", false)
    override var bypassSearchdomains: Boolean by booleanPref("bypass_searchdomains", true)

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
    override var areCustomServers: Boolean by booleanPref("doh_custom_server", false)
    override var primaryServerConfig: ServerConfiguration by ServerConfigurationPreference("doh_server_url_primary") {
        AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated(500)
        AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[0]!!.serverConfigurations.values.first()
    }
    override var secondaryServerConfig: ServerConfiguration? by optionalOf(ServerConfigurationPreference("doh_server_url_secondary") {
        AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated(500)
        val config = AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[0]!!.serverConfigurations.values.last()
        if (config != primaryServerConfig) config
        else throw UnsupportedOperationException()
    }, assignDefaultValue = true)

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