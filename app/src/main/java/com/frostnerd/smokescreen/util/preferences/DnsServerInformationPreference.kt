package com.frostnerd.smokescreen.util.preferences

import android.content.SharedPreferences
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.json.DnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.HttpsUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.quic.QuicUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.PreferenceType
import com.frostnerd.smokescreen.type
import com.frostnerd.smokescreen.util.ServerType
import kotlin.reflect.KProperty

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
class DnsServerInformationPreference(key: String) :
    PreferenceType<SharedPreferences, DnsServerInformation<*>>(key) {
    private val tlsTypeAdapter = DnsServerInformationTypeAdapter()
    private val httpsTypeAdapter = HttpsDnsServerInformationTypeAdapter()

    init {
        TLSUpstreamAddress
        HttpsUpstreamAddress
    }

    override fun getValue(
        thisRef: TypedPreferences<SharedPreferences>,
        property: KProperty<*>
    ): DnsServerInformation<*>? {
        return if(thisRef.sharedPreferences.contains(key + "_type")) {
            val type = thisRef.sharedPreferences.getString(key + "_type", "")
            val json = thisRef.sharedPreferences.getString(key, "")
            if(json.isNullOrBlank()) {
                null
            } else {
                when (type) {
                    "https" -> httpsTypeAdapter.fromJson(json)
                    "tls" -> tlsTypeAdapter.fromJson(json) as DnsServerInformation<TLSUpstreamAddress>
                    "quic" -> tlsTypeAdapter.fromJson(json) as DnsServerInformation<QuicUpstreamAddress>
                    else -> throw IllegalStateException("Unknown type $type")
                }
            }
        } else null
    }

    override fun setValue(
        thisRef: TypedPreferences<SharedPreferences>,
        property: KProperty<*>,
        value: DnsServerInformation<*>?
    ) {
        if(value != null) {
            val type = when(value.type) {
                ServerType.DOH -> "https"
                ServerType.DOT -> "tls"
                ServerType.DOQ -> "quic"
            }
            val json = when(value.type) {
                ServerType.DOH -> httpsTypeAdapter.toJson(value as HttpsDnsServerInformation)
                ServerType.DOT -> tlsTypeAdapter.toJson(value)
                ServerType.DOQ -> tlsTypeAdapter.toJson(value)
            }

            thisRef.edit {listener ->
                listener(key, value)
                putString(key + "_type", type)
                putString(key, json)
            }
        } else {
            thisRef.edit { listener ->
                listener(key, null)
                remove(key)
                remove("${key}_type")
            }
        }
    }

}