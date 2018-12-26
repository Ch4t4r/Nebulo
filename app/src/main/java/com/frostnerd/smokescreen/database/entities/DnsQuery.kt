package com.frostnerd.smokescreen.database.entities

import android.util.Base64
import androidx.room.*
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
    @delegate:Ignore
    val shortName:String by lazy { calculateShortName() }

    companion object {
        private val SHORT_DOMAIN_REGEX = "^((?:[^.]{1,3}\\.)+)([\\w]{4,})(?:\\.(?:[^.]*))*\$".toRegex()
    }

    /**
     * Returns a shortened representation of the name.
     * E.g frostnerd.com for ads.frostnerd.com, or "dgfhd" for "dgfhd"
     */
    private fun calculateShortName():String {
        val match = SHORT_DOMAIN_REGEX.matchEntire(name.reversed())
        if(match != null) {
            if(match.groups.size == 3) {
                return match.groupValues[2].reversed() + match.groupValues[1].reversed()
            }
            return name
        } else return name
    }

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