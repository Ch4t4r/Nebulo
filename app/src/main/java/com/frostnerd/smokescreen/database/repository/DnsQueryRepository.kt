package com.frostnerd.smokescreen.database.repository

import android.content.Context
import com.frostnerd.smokescreen.database.converters.StringListConverter
import com.frostnerd.smokescreen.database.dao.DnsQueryDao
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.getPreferences
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

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

class DnsQueryRepository(private val dnsQueryDao: DnsQueryDao) {

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

    fun exportQueriesAsCsvAsync(context: Context,
                                fileReadyCallback:(createdFile: File) ->Unit,
                                countUpdateCallback:(count:Long, total:Int) -> Unit):Job {
        val exportDir = File(context.filesDir, "queryexport/")
        exportDir.mkdirs()
        val exportFile = File(exportDir, "queries.csv")
        val start = context.getPreferences().exportedQueryCount
        val existed = if(start == 0 && exportFile.exists()) {
            exportFile.delete()
            false
        } else exportFile.exists()
        return GlobalScope.launch {
            val outStream = BufferedWriter(FileWriter(exportFile, true))
            if(!existed) {
                outStream.write("Name,Short Name,Type Name,Type ID,Asked Server,Answered from Cache,Question time,Response Time,Responses(JSON-Array of Base64)")
                outStream.newLine()
            }
            val builder = StringBuilder()
            val responseConverter = StringListConverter()

            val count = dnsQueryDao.getCount()
            var total = start.toLong()
            for(i in start..count step 5000) {
                val all = dnsQueryDao.getAll(5000, i.toLong())
                for (query in all) {
                    total++
                    builder.append(query.name).append(",")
                    builder.append(query.shortName).append(",")
                    builder.append(query.type.name).append(",")
                    builder.append(query.type.value).append(",")
                    builder.append(query.askedServer).append(",")
                    builder.append(query.responseSource?.name ?: "").append(",")
                    builder.append(query.questionTime).append(",")
                    builder.append(query.responseTime).append(",")
                    builder.append("\"").append(responseConverter.someObjectListToString(query.responses).replace(",", ";").replace("\"", "'")).append("\"")
                    outStream.write(builder.toString())
                    outStream.newLine()
                    outStream.flush()
                    builder.clear()
                    if(total % 250 == 0L) countUpdateCallback(total, count)
                }
            }
            outStream.close()
            context.getPreferences().exportedQueryCount = count
            fileReadyCallback(exportFile)
        }
    }
}