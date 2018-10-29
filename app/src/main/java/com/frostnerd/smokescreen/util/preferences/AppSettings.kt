package com.frostnerd.smokescreen.util.preferences

import android.content.Context
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.preferenceskt.restrictedpreferences.restrictedCollection
import com.frostnerd.preferenceskt.typedpreferences.SimpleTypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.booleanPref
import com.frostnerd.preferenceskt.typedpreferences.types.stringPref
import com.frostnerd.preferenceskt.typedpreferences.types.stringSetPref
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
    var theme: Theme
    var catchKnownDnsServers:Boolean
    var dummyDnsAddressIpv4:String
    var dummyDnsAddressIpv6:String
    var defaultBypassPackages:Set<String>
    var isCustomServerUrl:Boolean
    var primaryServerConfig:ServerConfiguration
    var secondaryServerConfig:ServerConfiguration?
}

class AppSettingsSharedPreferences(context: Context): AppSettings, SimpleTypedPreferences(context) {
    override var theme: Theme by ThemePreference("theme", Theme.MONO)
    override var catchKnownDnsServers: Boolean by booleanPref("doh_custom_server", false)
    override var dummyDnsAddressIpv4: String by stringPref("dummy_dns_ipv4", "8.8.8.8")
    override var dummyDnsAddressIpv6: String by stringPref("dummy_dns_ipv6", "2001:4860:4860::8888")
    override var defaultBypassPackages: Set<String> by restrictedCollection(stringSetPref("default_bypass_packages", hashSetOf(BuildConfig.APPLICATION_ID, "com.android.vending"))) {
        shouldContain(BuildConfig.APPLICATION_ID)
        shouldContain("com.android.vending")
    }
    override var isCustomServerUrl: Boolean by booleanPref("doh_custom_server", false)
    override var primaryServerConfig: ServerConfiguration by stringPref("doh_server_url", "dns.google.com")
    override var secondaryServerConfig: ServerConfiguration? by stringPref("doh_server_url_secondary")
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