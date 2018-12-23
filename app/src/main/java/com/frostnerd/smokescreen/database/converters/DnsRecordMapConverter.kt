package com.frostnerd.smokescreen.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class DnsRecordMapConverter {
    @TypeConverter
    fun stringToMap(value: String): Map<String, Long> {
        return Gson().fromJson(value, object : TypeToken<Map<String, Long>>() {}.type)
    }

    @TypeConverter
    fun mapToString(value: Map<String, Long>?): String {
        return if (value == null) "" else Gson().toJson(value)
    }
}