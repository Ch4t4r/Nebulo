package com.frostnerd.smokescreen.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverters
import com.frostnerd.smokescreen.database.converters.DnsRecordMapConverter
import com.frostnerd.smokescreen.database.converters.DnsTypeConverter
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
@Entity(tableName = "CachedResponse", primaryKeys = ["dnsName", "type"])
@TypeConverters(DnsTypeConverter::class, DnsRecordMapConverter::class)
data class CachedResponse(
    @ColumnInfo(name = "dnsName") var dnsName: String,
    @ColumnInfo(name = "type") var type: Record.TYPE,
    @ColumnInfo(name = "records") var records: MutableMap<String, Long>
)