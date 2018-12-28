package com.frostnerd.smokescreen.database.repository

import android.content.Context
import androidx.room.Insert
import com.frostnerd.smokescreen.database.converters.StringListConverter
import com.frostnerd.smokescreen.database.dao.DnsQueryDao
import com.frostnerd.smokescreen.database.entities.DnsQuery
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.StringBuilder

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

class DnsQueryRepository(val dnsQueryDao: DnsQueryDao) {

    fun updateAsync(dnsQuery: DnsQuery): Job {
        return GlobalScope.launch {
            dnsQueryDao.update(dnsQuery)
        }
    }

    fun insertAllAsync(dnsQueries:List<DnsQuery>): Job {
        return GlobalScope.launch {
            dnsQueryDao.insertAll(dnsQueries)
        }
    }

    fun insertAsync(dnsQuery:DnsQuery): Job {
        return GlobalScope.launch {
            dnsQueryDao.insert(dnsQuery)
        }
    }

    suspend fun getAllAsync(coroutineScope: CoroutineScope): List<DnsQuery> {
        return coroutineScope.async(start = CoroutineStart.DEFAULT) {
            dnsQueryDao.getAll()
        }.await()
    }

    fun exportQueriesAsCsvAsync(context: Context, fileReadyCallback:(createdFile: File) ->Unit):Job {
        val exportDir = File(context.filesDir, "queryexport/")
        exportDir.mkdirs()
        val exportFile = File(exportDir, "queries.csv")
        exportFile.delete()
        return GlobalScope.launch {
            val all = getAllAsync(this)
            val outStream = BufferedWriter(FileWriter(exportFile))
            outStream.write("Name,Short Name,Type Name,Type ID,Asked Server,Answered from Cache,Question time,Response Time,Responses(JSON-Array of Base64)")
            outStream.newLine()
            val builder = StringBuilder()
            val responseConverter = StringListConverter()
            for (query in all) {
                builder.append(query.name).append(",")
                builder.append(query.shortName).append(",")
                builder.append(query.type.name).append(",")
                builder.append(query.type.value).append(",")
                builder.append(query.askedServer).append(",")
                builder.append(query.fromCache).append(",")
                builder.append(query.questionTime).append(",")
                builder.append(query.responseTime).append(",")
                builder.append("\"").append(responseConverter.someObjectListToString(query.responses).replace(",", ";").replace("\"", "'")).append("\"")
                outStream.write(builder.toString())
                outStream.newLine()
                outStream.flush()
                builder.clear()
            }
            outStream.close()
            fileReadyCallback(exportFile)
        }
    }
}