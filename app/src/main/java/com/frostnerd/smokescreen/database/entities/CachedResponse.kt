package com.frostnerd.smokescreen.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverters
import com.frostnerd.smokescreen.database.converters.DnsRecordMapConverter
import com.frostnerd.smokescreen.database.converters.DnsTypeConverter
import org.minidns.record.Record

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
@Entity(tableName = "CachedResponse", primaryKeys = ["dnsName", "type"])
@TypeConverters(DnsTypeConverter::class, DnsRecordMapConverter::class)
data class CachedResponse(
    @ColumnInfo(name = "dnsName") var dnsName: String,
    @ColumnInfo(name = "type") var type: Record.TYPE,
    @ColumnInfo(name = "records") var records: MutableMap<String, Long>
)