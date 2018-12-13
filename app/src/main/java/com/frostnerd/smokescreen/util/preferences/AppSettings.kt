package com.frostnerd.smokescreen.util.preferences

import android.content.Context
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.general.CombinedIterator
import com.frostnerd.general.combineIterators
import com.frostnerd.preferenceskt.restrictedpreferences.restrictedCollection
import com.frostnerd.preferenceskt.typedpreferences.SimpleTypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.*
import com.frostnerd.smokescreen.BuildConfig
import java.lang.UnsupportedOperationException

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
    var theme: Theme
    var catchKnownDnsServers:Boolean
    var dummyDnsAddressIpv4:String
    var dummyDnsAddressIpv6:String
    val defaultBypassPackages:Set<String>
    var userBypassPackages:MutableSet<String>
    var areCustomServers:Boolean
    var primaryServerConfig:ServerConfiguration
    var secondaryServerConfig:ServerConfiguration?

    var startAppOnBoot:Boolean

    val bypassPackagesIterator: CombinedIterator<String>
        get() = combineIterators(defaultBypassPackages.iterator(), userBypassPackages.iterator())
}

class AppSettingsSharedPreferences(context: Context): AppSettings, SimpleTypedPreferences(context) {
    override var startAppOnBoot: Boolean by booleanPref("start_on_boot", true)
    override var theme: Theme by ThemePreference("theme", Theme.MONO)
    override var catchKnownDnsServers: Boolean by booleanPref("doh_custom_server", false)
    override var dummyDnsAddressIpv4: String by stringPref("dummy_dns_ipv4", "8.8.8.8")
    override var dummyDnsAddressIpv6: String by stringPref("dummy_dns_ipv6", "2001:4860:4860::8888")
    override val defaultBypassPackages: Set<String> by restrictedCollection(stringSetPref("default_bypass_packages", hashSetOf(BuildConfig.APPLICATION_ID, "com.android.vending"))) {
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
        if(config != primaryServerConfig) config
        else throw UnsupportedOperationException()
    }, assignDefaultValue = true)
    override var userBypassPackages by mutableStringSetPref("user_bypass_packages", mutableSetOf())
}

fun AppSettings.Companion.fromSharedPreferences(context: Context): AppSettingsSharedPreferences {
    return if(instance == null) {
        instance =
                AppSettingsSharedPreferences(context)
        instance!!
    } else {
        instance!!
    }
}