package com.frostnerd.smokescreen.util.proxy

import android.content.Context
import com.frostnerd.dnstunnelproxy.DnsHandle
import com.frostnerd.dnstunnelproxy.QueryListener
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.log
import org.minidns.dnsmessage.DnsMessage

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class QueryListener(private val context: Context) : com.frostnerd.dnstunnelproxy.QueryListener {
    private val writeQueriesToLog = context.getPreferences().loggingEnabled
    private val logQueriesToDb = context.getPreferences().queryLoggingEnabled
    private val waitingQueryLogs: MutableMap<Int, DnsQuery> = mutableMapOf()
    private val askedServer = context.getPreferences().primaryServerConfig.urlCreator.baseUrl

    override suspend fun onQueryForwarded(questionMessage: DnsMessage, destination: UpstreamAddress, usedHandle:DnsHandle) {
        if(writeQueriesToLog) {
            context.log("Query with ID ${questionMessage.id} forwarded by $usedHandle")
        }

        if (logQueriesToDb) {
            val query = waitingQueryLogs[questionMessage.id] ?: return
            query.askedServer = askedServer
            context.getDatabase().dnsQueryDao().update(query)
        }
    }

    override suspend fun onDeviceQuery(questionMessage: DnsMessage) {
        if (writeQueriesToLog) {
            context.log("Query from device: $questionMessage")
        }
        if (logQueriesToDb) {
            val query = DnsQuery(
                type = questionMessage.question.type,
                name = questionMessage.question.name.toString(),
                askedServer = null,
                questionTime = System.currentTimeMillis(),
                responses = mutableListOf(),
                fromCache = false
            )
            val dao = context.getDatabase().dnsQueryDao()
            dao.insert(query)
            query.id = dao.getLastInsertedId()
            waitingQueryLogs[questionMessage.id] = query
        }
    }

    override suspend fun onQueryResponse(responseMessage: DnsMessage, source: QueryListener.Source) {
        if (writeQueriesToLog) {
            context.log("Returned from $source: $responseMessage")
            context.log("Response from upstream: $responseMessage")
        }

        if (logQueriesToDb) {
            val query = waitingQueryLogs[responseMessage.id]
            if (query != null) {
                query.responseTime = System.currentTimeMillis()
                for (answer in responseMessage.answerSection) {
                    query.addResponse(answer)
                }
                query.fromCache = (source == QueryListener.Source.CACHE || source == QueryListener.Source.CACHE_AND_LOCALRESOLVER)
                context.getDatabase().dnsQueryRepository().updateAsync(query)
                waitingQueryLogs.remove(responseMessage.id)
            }
        }
    }

}
