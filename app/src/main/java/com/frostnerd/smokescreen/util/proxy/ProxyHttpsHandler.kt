package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.AddressCreator
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
class ProxyHttpsHandler(
    serverConfigurations: List<ServerConfiguration>,
    connectTimeout: Int,
    val queryCountCallback: ((queryCount: Int) -> Unit)? = null,
    val nullRouteKeweon: Boolean = false
) :
    AbstractHttpsDNSHandle(serverConfigurations, connectTimeout) {
    override val handlesSpecificRequests: Boolean = false
    private val dummyUpstreamAddress = UpstreamAddress(AddressCreator.fromHostAddress("0.0.0.0"), 0)

    override fun name(): String {
        return "ProxyHttpsHandler"
    }

    override suspend fun shouldHandleRequest(dnsMessage: DnsMessage): Boolean {
        throw RuntimeException("Won't ever be called")
    }

    constructor(
        serverConfiguration: ServerConfiguration,
        connectTimeout: Int
    ) : this(listOf(serverConfiguration), connectTimeout)

    override suspend fun modifyUpstreamResponse(dnsMessage: DnsMessage): DnsMessage {
        if (!nullRouteKeweon) return dnsMessage
        return com.frostnerd.smokescreen.util.proxy.nullRouteKeweon(dnsMessage)
    }

    override suspend fun remapDestination(destinationAddress: InetAddress, port: Int): UpstreamAddress {
        queryCountCallback?.invoke(dnsPacketProxy?.tunnelHandle?.trafficStats?.packetsReceivedFromDevice?.toInt() ?: 0)
        return dummyUpstreamAddress
    }

    override suspend fun shouldHandleDestination(destinationAddress: InetAddress, port: Int): Boolean = true

    override suspend fun shouldModifyUpstreamResponse(answer: ReceivedAnswer, receivedPayload: ByteArray): Boolean =
        nullRouteKeweon
}