package com.frostnerd.smokescreen.util.rules

import android.content.SharedPreferences
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.PreferenceType
import java.io.File
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
open class HostSource(var name:String) {
    var enabled = true
}

class FileHostSource(name:String, val file: File): HostSource(name)

class HttpHostSource(name:String, val url:String): HostSource(name)

class HostSourcePreference(key:String):PreferenceType<SharedPreferences, HostSource>(key) {
    override fun getValue(thisRef: TypedPreferences<SharedPreferences>, property: KProperty<*>): HostSource? {
        return if(thisRef.sharedPreferences.contains(key)) {
            val type = thisRef.sharedPreferences.getString("${key}_type", null)!!
            val name = thisRef.sharedPreferences.getString("${key}_name", null)!!
            val source = when(type.toLowerCase()) {
                "file" -> FileHostSource(name, File(thisRef.sharedPreferences.getString(key, null)!!))
                else -> HttpHostSource(name, thisRef.sharedPreferences.getString(key, null)!!)
            }
            source.enabled = thisRef.sharedPreferences.getBoolean("${key}_enabled", true)
            source
        } else {
            null
        }
    }

    override fun setValue(thisRef: TypedPreferences<SharedPreferences>, property: KProperty<*>, value: HostSource?) {
        thisRef.edit { listener ->
            listener(key, value)
            if(value == null) {
                remove(key)
                remove("${key}_type")
                remove("${key}_enabled")
                remove("${key}_name")
            } else {
                if(value is FileHostSource) {
                    putString("${key}_type", "file")
                    putString(key, value.file.absolutePath)
                } else if(value is HttpHostSource) {
                    putString("${key}_type", "http")
                    putString(key, value.url)
                }
                putString("${key}_name", value.name)
                putBoolean("${key}_enabled", value.enabled)
            }
        }
    }
}