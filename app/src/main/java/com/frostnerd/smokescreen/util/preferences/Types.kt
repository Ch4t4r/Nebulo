package com.frostnerd.smokescreen.util.preferences

import android.content.SharedPreferences
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.PreferenceTypeWithDefault
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

class StringBasedIntPreferenceWithDefault<PrefType : SharedPreferences>(
    key: String,
    defaultValue: (key: String) -> Int
) : PreferenceTypeWithDefault<PrefType, Int>(key, defaultValue) {
    constructor(key: String, defaultValue: Int) : this(key, { defaultValue })

    override fun getValue(thisRef: TypedPreferences<PrefType>, property: KProperty<*>): Int {
        return if (thisRef.sharedPreferences.contains(key)) {
            thisRef.sharedPreferences.getString(key, "-1")!!.toInt()
        } else defaultValue(key)
    }

    override fun setValue(thisRef: TypedPreferences<PrefType>, property: KProperty<*>, value: Int) {
        thisRef.edit { listener ->
            putString(key, value.toString())
            listener(key, value)
        }
    }
}

fun <PrefType : SharedPreferences> stringBasedIntPref(
    key: String,
    defaultValue: Int
): StringBasedIntPreferenceWithDefault<PrefType> = StringBasedIntPreferenceWithDefault(key, defaultValue)