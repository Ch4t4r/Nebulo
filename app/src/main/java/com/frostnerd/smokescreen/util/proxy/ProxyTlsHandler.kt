package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.ParsedPacket
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.vpntunnelproxy.FutureAnswer
import com.frostnerd.vpntunnelproxy.ReceivedAnswer
import com.frostnerd.vpntunnelproxy.TunnelHandle
import org.minidns.dnsmessage.DnsMessage
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.net.ssl.SSLSession

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

class ProxyTlsHandler(
    private val upstreamAddresses: List<TLSUpstreamAddress>,
    connectTimeout: Int,
    val queryCountCallback: ((queryCount: Int) -> Unit)? = null
):AbstractTLSDnsHandle(connectTimeout) {
    override val handlesSpecificRequests: Boolean = false

    override suspend fun forwardDnsQuestion(
        deviceWriteToken: TunnelHandle.DeviceWriteToken,
        dnsMessage: DnsMessage,
        originalEnvelope: ParsedPacket,
        realDestination: UpstreamAddress
    ) {
        val destination = selectAddressOrNull(realDestination)
        if(destination != null) {
            val data = dnsMessage.toArray()
            sendPacketToUpstreamDNSServer(deviceWriteToken, DatagramPacket(data, 0, data.size, destination, realDestination.port), originalEnvelope)
        } else {
            val response = dnsMessage.asBuilder().setQrFlag(true).setResponseCode(DnsMessage.RESPONSE_CODE.SERVER_FAIL)
            dnsPacketProxy?.tunnelHandle?.proxy?.logger?.warning("Cannot forward packet because the address isn't resolved yet.")
            dnsPacketProxy?.writeUDPDnsPacketToDevice(response.build().toArray(), originalEnvelope)
        }
    }

    private fun selectAddressOrNull(upstreamAddress: UpstreamAddress):InetAddress? {
        val resolveResult = upstreamAddress.addressCreator.resolveOrGetResultOrNull(true) ?: return null
        return resolveResult.firstOrNull {
            (ipv4Enabled && ipv6Enabled) || (ipv4Enabled && it is Inet4Address) || (ipv6Enabled && it is Inet6Address)
        } ?: (resolveResult.firstOrNull() ?: throw IllegalStateException("The given UpstreamAddress doesn't have an address for the requested IP version (IPv4: $ipv4Enabled, IPv6: $ipv6Enabled)"))
    }

    override fun informFailedRequest(request: FutureAnswer, failureReason:Throwable?) {
    }

    override suspend fun modifyUpstreamResponse(dnsMessage: DnsMessage): DnsMessage {
        return dnsMessage
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
        return false
    }

    override fun verifyConnection(sslSession: SSLSession, outgoingPacket: DatagramPacket) {
        // Java handles certificate validation for us.
    }

    override fun name(): String {
        return "ProxyTlsHandler"
    }

}