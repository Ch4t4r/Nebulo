package com.frostnerd.smokescreen.database.converters

import androidx.room.TypeConverter
import org.minidns.record.Record

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class DnsTypeConverter {
    @TypeConverter
    fun fromId(value: Int): Record.TYPE {
        return value.let { Record.TYPE.getType(it) }
    }

    @TypeConverter
    fun toId(type:Record.TYPE):Int {
        return type.value
    }
}