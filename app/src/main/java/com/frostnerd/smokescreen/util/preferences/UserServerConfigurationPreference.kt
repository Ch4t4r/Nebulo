package com.frostnerd.smokescreen.util.preferences

import android.content.SharedPreferences
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.PreferenceTypeWithDefault
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.StringReader
import java.io.StringWriter
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
class UserServerConfigurationPreference(key: String, defaultValue: (String) -> Set<UserServerConfiguration>) :
    PreferenceTypeWithDefault<SharedPreferences, Set<UserServerConfiguration>>(key, defaultValue) {

    override fun getValue(
        thisRef: TypedPreferences<SharedPreferences>,
        property: KProperty<*>
    ): Set<UserServerConfiguration> {
        return if (thisRef.sharedPreferences.contains(key)) {
            val reader = JsonReader(StringReader(thisRef.sharedPreferences.getString(key, "")))
            val servers = mutableSetOf<UserServerConfiguration>()
            val adapter = HttpsDnsServerInformationTypeAdapter()
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.peek() != JsonToken.END_ARRAY) {
                    reader.beginObject()
                    var id = 0
                    var info:HttpsDnsServerInformation? = null
                    while(reader.peek() != JsonToken.END_OBJECT) {
                        when(reader.nextName().toLowerCase()) {
                            "id" -> id = reader.nextInt()
                            "server" -> info = adapter.read(reader)!!
                        }
                    }
                    reader.endObject()
                    servers.add(UserServerConfiguration(id, info!!))
                }
                reader.endArray()
            }
            reader.close()
            servers
        } else defaultValue(key)
    }

    override fun setValue(
        thisRef: TypedPreferences<SharedPreferences>,
        property: KProperty<*>,
        value: Set<UserServerConfiguration>
    ) {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        val adapter = HttpsDnsServerInformationTypeAdapter()
        jsonWriter.beginArray()
        for (server in value) {
            jsonWriter.beginObject()
            jsonWriter.name("id")
            jsonWriter.value(server.id)
            jsonWriter.name("server")
            adapter.write(jsonWriter, server.serverInformation)
            jsonWriter.endObject()
        }
        jsonWriter.endArray()
        jsonWriter.close()
        thisRef.edit { listener ->
            putString(key, stringWriter.toString())
            listener(key, value)
        }
    }
}

class UserServerConfiguration(val id:Int, val serverInformation:HttpsDnsServerInformation)