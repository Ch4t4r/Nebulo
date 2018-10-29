package com.frostnerd.smokescreen.util.preferences

import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
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
    PreferenceTypeWithDefault<ServerConfiguration>(key, defaultValue) {
    constructor(key: String, defaultValue: ServerConfiguration) : this(key, { defaultValue })

    override fun getValue(thisRef: TypedPreferences, property: KProperty<*>): ServerConfiguration {
        if(thisRef.sharedPreferences.contains(key)) {

        } else return defaultValue(key)
    }

    override fun setValue(thisRef: TypedPreferences, property: KProperty<*>, value: ServerConfiguration) {

    }

}