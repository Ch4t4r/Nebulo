package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.vpntunnelproxy.ReceivedAnswer
import org.minidns.dnsmessage.DnsMessage
import org.minidns.record.A
import org.minidns.record.AAAA
import org.minidns.record.Data
import org.minidns.record.Record
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class ProxyHandler(
    serverConfigurations: List<ServerConfiguration>,
    connectTimeout: Int,
    val queryCountCallback: ((queryCount: Int) -> Unit)? = null,
    val nullRouteKeweon: Boolean = false
) :
    AbstractHttpsDNSHandle(serverConfigurations, connectTimeout) {
    override val handlesSpecificRequests: Boolean = false
    private val keweonIpv4BlockIPs = setOf("45.32.152.171", "195.201.221.5", "176.9.62.7", "159.69.145.142")
    private val keweonIpv6BlockIPs = setOf("2001:19f0:6c01:d6::171", "2001:19f0:6c01:d6:0:0:0:171", "2a01:4f8:1c1c:69a6::5", "2a01:4f8:1c1c:69a6:0:0:0:5")
    private val nullRouteIpv4: Inet4Address by lazy {
        Inet4Address.getByName("0.0.0.0") as Inet4Address
    }
    private val nullRouteIpv6: Inet6Address by lazy {
        Inet6Address.getByName("::") as Inet6Address
    }


    override suspend fun shouldHandleRequest(dnsMessage: DnsMessage): Boolean {
        throw RuntimeException("Won't ever be called")
    }

    constructor(
        serverConfiguration: ServerConfiguration,
        connectTimeout: Int
    ) : this(listOf(serverConfiguration), connectTimeout)

    override suspend fun modifyUpstreamResponse(dnsMessage: DnsMessage): DnsMessage {
        if(!nullRouteKeweon) return dnsMessage
        val newRecords = mutableListOf<Record<*>>()
        var changed = false
        for (record in dnsMessage.answerSection) {
            var newData:Data? = null
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

    override suspend fun remapDestination(destinationAddress: InetAddress, port: Int): UpstreamAddress {
        queryCountCallback?.invoke(dnsPacketProxy?.tunnelHandle?.trafficStats?.packetsReceivedFromDevice?.toInt() ?: 0)
        return UpstreamAddress(destinationAddress, port)
    }


    override suspend fun shouldHandleDestination(destinationAddress: InetAddress, port: Int): Boolean = true

    override suspend fun shouldModifyUpstreamResponse(answer: ReceivedAnswer, receivedPayload: ByteArray): Boolean =
        nullRouteKeweon
}