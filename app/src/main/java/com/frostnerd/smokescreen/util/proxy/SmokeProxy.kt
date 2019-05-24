package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.DnsHandle
import com.frostnerd.dnstunnelproxy.DnsPacketProxy
import com.frostnerd.dnstunnelproxy.QueryListener
import com.frostnerd.dnstunnelproxy.SimpleDnsCache
import com.frostnerd.smokescreen.service.DnsVpnService
import org.minidns.dnsmessage.DnsMessage
import org.minidns.record.A
import org.minidns.record.AAAA
import org.minidns.record.Data
import org.minidns.record.Record
import java.net.Inet4Address
import java.net.Inet6Address

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

class SmokeProxy(
    dnsHandle: DnsHandle,
    proxyBypassHandles: List<DnsHandle>,
    val cache: SimpleDnsCache?,
    queryListener: QueryListener?
) :
    DnsPacketProxy(
        proxyBypassHandles.toMutableList().let {
            it.add(dnsHandle)
            it
        }.toList(),
        null,
        cache,
        queryListener = queryListener
    )


private val keweonIpv4BlockIPs = setOf("45.32.152.171", "195.201.221.5", "176.9.62.7", "159.69.145.142", "5.189.169.177", "45.76.92.226")
private val keweonIpv6BlockIPs = setOf("2001:19f0:6c01:d6::171", "2001:19f0:6c01:d6:0:0:0:171", "2a01:4f8:1c1c:69a6::5", "2a01:4f8:1c1c:69a6:0:0:0:5")
private val nullRouteIpv4: Inet4Address by lazy {
    Inet4Address.getByName("0.0.0.0") as Inet4Address
}
private val nullRouteIpv6: Inet6Address by lazy {
    Inet6Address.getByName("::") as Inet6Address
}

internal fun nullRouteKeweon(dnsMessage:DnsMessage):DnsMessage {
    val questionName = dnsMessage.question.name
    if(questionName.contains("keweon") || questionName.contains("i.hate.ads")) return dnsMessage
    val newRecords = mutableListOf<Record<*>>()
    var changed = false
    for (record in dnsMessage.answerSection) {
        var newData: Data? = null
        if (record.type == Record.TYPE.A) {
            val data = record.payload as A
            if (keweonIpv4BlockIPs.contains(data.toString())) {
                newData = A(nullRouteIpv4)
            }
        } else if (record.type == Record.TYPE.AAAA) {
            val data = record.payload as AAAA
            if(keweonIpv6BlockIPs.contains(data.toString())) {
                newData = AAAA(nullRouteIpv6)
            }
        }
        if(newData != null) {
            newRecords.add(
                Record(
                    record.name,
                    record.type,
                    record.clazz,
                    record.ttl,
                    newData,
                    record.unicastQuery
                )
            )
            changed = true
        } else newRecords.add(record)
    }
    return if (changed) {
        dnsMessage.asBuilder().setAnswers(newRecords).build()
    } else dnsMessage
}