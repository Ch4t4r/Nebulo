package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.vpntunnelproxy.FutureAnswer
import com.frostnerd.vpntunnelproxy.ReceivedAnswer
import com.frostnerd.vpntunnelproxy.TunnelHandle
import org.minidns.dnsmessage.DnsMessage
import org.pcap4j.packet.IpPacket
import java.lang.IllegalStateException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.net.ssl.SSLSession

/**
 * Copyright Daniel Wolf 2019
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

class ProxyTlsHandler(
    val upstreamAddresses: List<TLSUpstreamAddress>,
    connectTimeout: Int,
    val queryCountCallback: ((queryCount: Int) -> Unit)? = null,
    val nullRouteKeweon: Boolean = false
):AbstractTLSDnsHandle(connectTimeout) {
    override val handlesSpecificRequests: Boolean = false

    override suspend fun forwardDnsQuestion(
        deviceWriteToken: TunnelHandle.DeviceWriteToken,
        dnsMessage: DnsMessage,
        originalEnvelope: IpPacket,
        realDestination: UpstreamAddress
    ) {
        val destination = selectAddressOrNull(realDestination)
        if(destination != null) {
            val data = dnsMessage.toArray()
            sendPacketToUpstreamDNSServer(deviceWriteToken, DatagramPacket(data, 0, data.size, destination, realDestination.port), originalEnvelope)
        } else {
            val response = dnsMessage.asBuilder().setQrFlag(true).setResponseCode(DnsMessage.RESPONSE_CODE.SERVER_FAIL)
            dnsPacketProxy?.writeUDPDnsPacketToDevice(deviceWriteToken, response.build().toArray(), originalEnvelope)
        }
    }

    private fun selectAddressOrNull(upstreamAddress: UpstreamAddress):InetAddress? {
        val resolveResult = upstreamAddress.addressCreator.resolveOrGetResultOrNull(true) ?: return null
        return resolveResult.firstOrNull {
            (ipv4Enabled && it is Inet4Address) || (ipv6Enabled && it is Inet6Address)
        } ?: throw IllegalStateException("The given UpstreamAddress doesn't have an address for the requested IP version (IPv4: $ipv4Enabled, IPv6: $ipv6Enabled)")
    }

    override fun informFailedRequest(request: FutureAnswer) {
    }

    override suspend fun modifyUpstreamResponse(dnsMessage: DnsMessage): DnsMessage {
        if(!nullRouteKeweon) return dnsMessage
        return com.frostnerd.smokescreen.util.proxy.nullRouteKeweon(dnsMessage)
    }

    override suspend fun remapDestination(destinationAddress: InetAddress, port: Int): TLSUpstreamAddress {
        queryCountCallback?.invoke(dnsPacketProxy?.tunnelHandle?.trafficStats?.packetsReceivedFromDevice?.toInt() ?: 0)
        return upstreamAddresses[0]
    }

    override suspend fun shouldHandleDestination(destinationAddress: InetAddress, port: Int): Boolean {
        return true
    }

    override suspend fun shouldHandleRequest(dnsMessage: DnsMessage): Boolean {
        return true
    }

    override suspend fun shouldModifyUpstreamResponse(answer: ReceivedAnswer, receivedPayload: ByteArray): Boolean {
        return nullRouteKeweon
    }

    override fun verifyConnection(sslSession: SSLSession, outgoingPacket: DatagramPacket) {
        // Java handles certificate validation for us.
    }

    override fun name(): String {
        return "ProxyTlsHandler"
    }

}