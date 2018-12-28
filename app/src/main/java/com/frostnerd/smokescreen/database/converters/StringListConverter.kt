package com.frostnerd.smokescreen.database.converters

import androidx.room.TypeConverter
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson


/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class StringListConverter {
    private val gson = Gson()
    private val type = object:TypeToken<MutableList<String>>() {}.type

    @TypeConverter
    fun stringToList(value: String): MutableList<String> {
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun someObjectListToString(value: MutableList<String>): String {
        return gson.toJson(value)
    }
}