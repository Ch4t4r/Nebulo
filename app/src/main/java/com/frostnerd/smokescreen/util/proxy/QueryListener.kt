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
    // Question ID -> <Query, Insert> (true if the query has already been inserted)
    private val queryLogState: MutableMap<Int, Boolean> = mutableMapOf()
    // Query -> Has already been inserted
    private var doneQueries = mutableMapOf<DnsQuery, Boolean>()
    private val askedServer: String
    private var nextQueryId = 0L
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
        nextQueryId = context.getDatabase().dnsQueryDao().getLastInsertedId() + 1
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
                query.id = nextQueryId++
                waitingQueryLogs[questionMessage.id] = query
                queryLogState[questionMessage.id] = false
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
            waitingQueryLogs[questionMessage.id]!!.askedServer = askedServer
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
                waitingQueryLogs.remove(responseMessage.id)!!
            }
            query.responseTime = System.currentTimeMillis()
            for (answer in responseMessage.answerSection) {
                query.addResponse(answer)
            }
            query.responseSource = source
            doneQueries[query] = queryLogState.remove(responseMessage.id)!!
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
            doneQueries = mutableMapOf()
        }
        val database = context.getDatabase()
        val dao = database.dnsQueryDao()

        database.runInTransaction {
            currentInsertions.forEach { (key, value) ->
                if (queryLogState[key]!!) {
                    dao.insert(value)
                    queryLogState[key] = true
                } else {
                    dao.update(value)
                }
            }
            currentDoneInsertions.forEach { (key, value) ->
                if (value) dao.update(key)
                else dao.insert(key)
            }
        }
    }
}
