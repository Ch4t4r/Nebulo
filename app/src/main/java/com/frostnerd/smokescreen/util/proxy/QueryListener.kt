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
class QueryListener(private val context: Context) : com.frostnerd.dnstunnelproxy.QueryListener {
    private val writeQueriesToLog = context.getPreferences().loggingEnabled
    private val logQueriesToDb = context.getPreferences().queryLoggingEnabled
    private val waitingQueryLogs: MutableMap<Int, DnsQuery> = mutableMapOf()
    private val askedServer:String

    init {
        val config = context.getPreferences().dnsServerConfig
        askedServer = if(config.hasTlsServer()) {
            "tls::" + config.servers.first().address.host!!
        } else {
            "https::" + (config as HttpsDnsServerInformation).serverConfigurations.values.first().urlCreator.address.getUrl(false)
        }
    }

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
