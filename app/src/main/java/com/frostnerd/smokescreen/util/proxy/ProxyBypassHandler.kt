package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.AbstractUDPDnsHandle
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.vpntunnelproxy.FutureAnswer
import com.frostnerd.vpntunnelproxy.ReceivedAnswer
import com.frostnerd.vpntunnelproxy.TunnelHandle
import org.minidns.dnsmessage.DnsMessage
import org.pcap4j.packet.IpPacket
import java.net.DatagramPacket
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
class ProxyBypassHandler(val searchDomains:List<String>, val destinationDnsServer:InetAddress):AbstractUDPDnsHandle() {
    override val handlesSpecificRequests: Boolean = true

    override suspend fun shouldHandleRequest(dnsMessage: DnsMessage): Boolean {
        val name = dnsMessage.question.name
        return searchDomains.any {
            name.endsWith(it)
        }
    }

    override fun name(): String {
        return "ProxyBypassHandler[$searchDomains]"
    }

    override suspend fun forwardDnsQuestion(
        deviceWriteToken: TunnelHandle.DeviceWriteToken,
        dnsMessage: DnsMessage,
        originalEnvelope: IpPacket,
        realDestination: UpstreamAddress
    ) {
        val bytes = dnsMessage.toArray()
        val packet = DatagramPacket(bytes, bytes.size, realDestination.address, realDestination.port)
        sendPacketToUpstreamDNSServer(deviceWriteToken, packet, originalEnvelope)
    }

    override suspend fun shouldHandleDestination(destinationAddress: InetAddress, port: Int): Boolean {
        return true
    }

    override fun informFailedRequest(request: FutureAnswer) {
    }

    override suspend fun modifyUpstreamResponse(dnsMessage: DnsMessage): DnsMessage {
        return dnsMessage
    }

    override suspend fun remapDestination(destinationAddress: InetAddress, port: Int): UpstreamAddress {
        return UpstreamAddress(destinationDnsServer, 53)
    }

    override suspend fun shouldModifyUpstreamResponse(answer: ReceivedAnswer, receivedPayload: ByteArray): Boolean {
        return false
    }

}