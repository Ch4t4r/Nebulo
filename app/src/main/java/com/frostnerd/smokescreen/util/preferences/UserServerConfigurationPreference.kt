package com.frostnerd.smokescreen.util.preferences

import android.content.SharedPreferences
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformationTypeAdapter
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.PreferenceTypeWithDefault
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.StringReader
import java.io.StringWriter
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