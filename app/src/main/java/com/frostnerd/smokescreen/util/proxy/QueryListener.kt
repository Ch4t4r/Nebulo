package com.frostnerd.smokescreen.util.proxy

import android.content.Context
import com.frostnerd.dnstunnelproxy.DnsHandle
import com.frostnerd.dnstunnelproxy.QueryListener
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.hasTlsServer
import com.frostnerd.smokescreen.log
import kotlinx.coroutines.*
import org.minidns.dnsmessage.DnsMessage

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
class QueryListener(private val context: Context) : QueryListener {
    private val writeQueriesToLog = context.getPreferences().shouldLogDnsQueriesToConsole()
    private val logQueriesToDb = context.getPreferences().queryLoggingEnabled
    private var waitingQueryLogs = LinkedHashMap<Int, DnsQuery>()
    // Question ID -> <Query, Flag> (0 = insert, 1 = nothing new, 2 = update)
    private val queryLogState: MutableMap<Int, Int> = LinkedHashMap()
    // Query -> Has already been inserted
    private var doneQueries = LinkedHashMap<DnsQuery, Boolean>()
    private val askedServer: String
    var lastDnsResponse: DnsMessage? = null
    private val databaseWriteJob: Job

    init {
        val config = context.getPreferences().dnsServerConfig
        askedServer = if (config.hasTlsServer()) {
            "tls::" + config.servers.first().address.host!!
        } else {
            "https::" + (config as HttpsDnsServerInformation).serverConfigurations.values.first().urlCreator.address.getUrl(
                false
            )
        }
        databaseWriteJob =
            GlobalScope.launch(newSingleThreadContext("QueryListener-DatabaseWrite")) {
                while (isActive) {
                    delay(1500)
                    insertQueries()
                }
            }
    }

    override suspend fun onDeviceQuery(questionMessage: DnsMessage, srcPort: Int) {
        if (writeQueriesToLog) {
            context.log("Query from device: $questionMessage")
        }
        if (logQueriesToDb && questionMessage.questions.size != 0) {
            val query = DnsQuery(
                type = questionMessage.question.type,
                name = questionMessage.question.name.toString(),
                askedServer = null,
                responseSource = QueryListener.Source.UPSTREAM,
                questionTime = System.currentTimeMillis(),
                responses = mutableListOf()
            )
            synchronized(waitingQueryLogs) {
                waitingQueryLogs[questionMessage.id] = query
                queryLogState[questionMessage.id] = 0 // Insert
            }
        }
    }

    override suspend fun onQueryForwarded(
        questionMessage: DnsMessage,
        destination: UpstreamAddress,
        usedHandle: DnsHandle
    ) {
        if (writeQueriesToLog) {
            context.log("Query with ID ${questionMessage.id} forwarded by $usedHandle")
        }

        if (logQueriesToDb) {
            waitingQueryLogs[questionMessage.id]?.askedServer = askedServer
            if(queryLogState[questionMessage.id] != 0) queryLogState[questionMessage.id] = 2
        }
    }

    override suspend fun onQueryResponse(
        responseMessage: DnsMessage,
        source: QueryListener.Source
    ) {
        if (writeQueriesToLog) {
            context.log("Returned from $source: $responseMessage")
        }
        lastDnsResponse = responseMessage

        if (logQueriesToDb) {
            val query = synchronized(waitingQueryLogs) {
                waitingQueryLogs.remove(responseMessage.id)
            } ?: return
            val wasInserted = queryLogState.remove(responseMessage.id)!! != 0 // Update if already inserted (0=insert)
            query.responseTime = System.currentTimeMillis()
            query.responses = mutableListOf()
            for (answer in responseMessage.answerSection) {
                query.addResponse(answer)
            }
            query.responseSource = source
            doneQueries[query] = wasInserted
        }
    }

    override fun cleanup() {
        databaseWriteJob.cancel()
        insertQueries()
        waitingQueryLogs.clear()
        queryLogState.clear()
    }

    private fun insertQueries() {
        if (waitingQueryLogs.isEmpty() && doneQueries.isEmpty()) return
        val currentInsertions:Map<Int, DnsQuery>
        val currentDoneInsertions:Map<DnsQuery, Boolean>
        synchronized(waitingQueryLogs) {
            currentInsertions = waitingQueryLogs.toMap()
            currentDoneInsertions = doneQueries
            doneQueries = LinkedHashMap()
        }
        val database = context.getDatabase()
        val dao = database.dnsQueryDao()

        val joined = (currentInsertions.map {
            (it.key to it.value) to queryLogState[it.key]
        } + currentDoneInsertions.map {
            (null to it.key) to if(it.value) 2 else 0
        }).sortedBy {
            it.first.second.questionTime
        }

        database.runInTransaction {
            joined.forEach {
                // Do nothing for 1 (no update to process)
                // Do nothing for null (query was fulfilled in the meantime)
                when(it.second) {
                    0 -> {
                        dao.insert(it.first.second)
                        if(it.first.first != null && it.first.first in queryLogState) queryLogState[it.first.first!!] = 1
                    }
                    2 -> {
                        dao.update(it.first.second)
                        if(it.first.first != null && it.first.first in queryLogState) queryLogState[it.first.first!!] = 1
                    }
                }
            }
        }
    }
}
