package com.frostnerd.smokescreen.util.preferences

import android.content.SharedPreferences
import com.frostnerd.encrypteddnstunnelproxy.*
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.PreferenceTypeWithDefault
import kotlin.reflect.KProperty

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

class ServerConfigurationPreference(key: String, defaultValue: (String) -> ServerConfiguration) :
    PreferenceTypeWithDefault<SharedPreferences, ServerConfiguration>(key, defaultValue) {
    constructor(key: String, defaultValue: ServerConfiguration) : this(key, { defaultValue })
    private val encodedDivider = "/~~/"

    override fun getValue(thisRef: TypedPreferences<SharedPreferences>, property: KProperty<*>): ServerConfiguration {
        if(thisRef.sharedPreferences.contains(key)) {
            val encoded = thisRef.sharedPreferences.getString(key, "")!!
            return if(encoded.contains("/~~/")) {
                val split = encoded.split("/~~/")
                val requestType = RequestType.fromId(split[1].toInt())!!
                val responseType = ResponseType.fromId(split[2].toInt())!!
                ServerConfiguration.createSimpleServerConfig(split[0], requestType, responseType)
            } else {
                for (value in AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.values) {
                    val config = value.servers.firstOrNull {
                        it.address.getUrl().contains(encoded, ignoreCase = true)
                    }?.createServerConfiguration(value)
                    if(config != null) return config
                }
                ServerConfiguration.createSimpleServerConfig(encoded)
            }
        } else return defaultValue(key)
    }

    override fun setValue(thisRef: TypedPreferences<SharedPreferences>, property: KProperty<*>, value: ServerConfiguration) {
        var encoded:String? = null

        for (config in AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.values) {
            for (serverConfiguration in config.serverConfigurations) {
                if(serverConfiguration.value == value) {
                    encoded = serverConfiguration.key.address.getUrl()
                    break
                }
            }
        }

        if(encoded == null) {
            encoded = value.urlCreator.baseUrl + encodedDivider + value.transportConfig.requestType.id +
                    encodedDivider + value.transportConfig.responseType.id
        }

        thisRef.edit {
            putString(key, encoded)
        }
    }
}