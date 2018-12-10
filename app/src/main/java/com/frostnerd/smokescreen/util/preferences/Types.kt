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
            return if(encoded.contains(encodedDivider)) {
                val split = encoded.split(encodedDivider)
                val requestType = RequestType.fromId(split[1].toInt())!!
                val responseType = ResponseType.fromId(split[2].toInt())!!
                ServerConfiguration.createSimpleServerConfig(split[0], requestType, responseType)
            } else {
                return AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[encoded.toInt()]!!.serverConfigurations.entries.first().value
            }
        } else return defaultValue(key)
    }

    override fun setValue(thisRef: TypedPreferences<SharedPreferences>, property: KProperty<*>, value: ServerConfiguration) {
        var encoded:String? = null

        for ((id,config) in AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS) {
            for (serverConfiguration in config.serverConfigurations) {
                if(serverConfiguration.value == value) {
                    encoded = id.toString()
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