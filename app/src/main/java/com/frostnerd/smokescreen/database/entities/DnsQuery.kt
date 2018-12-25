package com.frostnerd.smokescreen.database.entities

import android.util.Base64
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverters
import com.frostnerd.smokescreen.database.converters.DnsRecordMapConverter
import com.frostnerd.smokescreen.database.converters.DnsTypeConverter
import com.frostnerd.smokescreen.database.converters.StringListConverter
import com.frostnerd.smokescreen.database.recordFromBase64
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
@Entity(tableName = "DnsQuery")
@TypeConverters(DnsTypeConverter::class, StringListConverter::class)
data class DnsQuery(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val type: Record.TYPE,
    val name: String,
    var askedServer: String?,
    val questionTime: Long,
    var responseTime: Long = 0,
    var responses: MutableList<String>
) {
    fun getParsedResponses(): List<Record<*>> {
        val responses = mutableListOf<Record<*>>()
        for (response in this.responses) {
            responses.add(recordFromBase64(response))
        }
        return responses
    }

    fun addResponse(record: Record<*>) {
        responses.add(Base64.encodeToString(record.toByteArray(), Base64.NO_WRAP))
    }

    override fun toString(): String {
        return "DnsQuery(id=$id, type=$type, name='$name', askedServer=$askedServer, questionTime=$questionTime, responseTime=$responseTime, responses={${responses.size}})"
    }
}