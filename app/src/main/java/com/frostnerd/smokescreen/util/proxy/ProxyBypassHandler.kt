package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.AbstractUDPDnsHandle
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.dnstunnelproxy.getInetAddressByNameLater
import com.frostnerd.vpntunnelproxy.FutureAnswer
import com.frostnerd.vpntunnelproxy.ReceivedAnswer
import org.minidns.dnsmessage.DnsMessage
import org.pcap4j.packet.IpPacket
import java.net.DatagramPacket
import java.net.InetAddress

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
class ProxyBypassHandler(val searchDomains:List<String>, val destinationDnsServer:InetAddress):AbstractUDPDnsHandle() {
    override val handlesSpecificRequests: Boolean = true

    override suspend fun shouldHandleRequest(dnsMessage: DnsMessage): Boolean {
        val name = dnsMessage.question.name
        return searchDomains.any {
            name.endsWith(it)
        }
    }

    override suspend fun forwardDnsQuestion(
        dnsMessage: DnsMessage,
        originalEnvelope: IpPacket,
        realDestination: UpstreamAddress
    ) {
        val bytes = dnsMessage.toArray()
        val packet = DatagramPacket(bytes, bytes.size, realDestination.address, realDestination.port)
        sendPacketToUpstreamDNSServer(packet, originalEnvelope)
    }

    override suspend fun shouldHandleDestination(destinationAddress: InetAddress, port: Int): Boolean {
        return true
    }

    override suspend fun informFailedRequest(request: FutureAnswer) {
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