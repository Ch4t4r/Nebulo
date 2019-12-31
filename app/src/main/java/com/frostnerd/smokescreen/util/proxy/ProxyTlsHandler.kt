package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.IPPacket
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.vpntunnelproxy.DeviceWriteToken
import com.frostnerd.vpntunnelproxy.FutureAnswer
import com.frostnerd.vpntunnelproxy.ReceivedAnswer
import org.minidns.dnsmessage.DnsMessage
import org.minidns.record.A
import org.minidns.record.AAAA
import org.minidns.record.Record
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
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
    val ownAddresses:List<String>,
    private val upstreamAddresses: List<TLSUpstreamAddress>,
    connectTimeout: Int,
    val queryCountCallback: ((queryCount: Int) -> Unit)? = null,
    val mapQueryRefusedToHostBlock:Boolean
):AbstractTLSDnsHandle(connectTimeout) {
    override val handlesSpecificRequests: Boolean =
        ProxyBypassHandler.knownSearchDomains.isNotEmpty()
    private val hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
    private var queryCount = 0

    override suspend fun forwardDnsQuestion(
        deviceWriteToken: DeviceWriteToken,
        dnsMessage: DnsMessage,
        originalEnvelope: IPPacket,
        realDestination: UpstreamAddress
    ) {
        val destination = selectAddressOrNull(realDestination)
        if(destination != null) {
            if(dnsMessage.questions.all { it.type != null }) {
                val data = dnsMessage.toArray()
                sendPacketToUpstreamDNSServer(deviceWriteToken, DatagramPacket(data, 0, data.size, destination, realDestination.port), originalEnvelope)
            }
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
        return if(dnsMessage.responseCode == DnsMessage.RESPONSE_CODE.REFUSED) {
            if(dnsMessage.questions.isNotEmpty()) {
                val answer = if(dnsMessage.question.type == Record.TYPE.A) {
                    A("0.0.0.0")
                } else AAAA("::1")
                dnsMessage.asBuilder().setResponseCode(DnsMessage.RESPONSE_CODE.NO_ERROR).addAnswer(Record(dnsMessage.question.name, dnsMessage.question.type, Record.CLASS.IN.value, 50L, answer)).build()
            } else dnsMessage
        } else dnsMessage
    }

    override suspend fun remapDestination(destinationAddress: InetAddress, port: Int): TLSUpstreamAddress {
        queryCountCallback?.invoke(++queryCount)
        return upstreamAddresses[0]
    }

    override suspend fun shouldHandleDestination(destinationAddress: InetAddress, port: Int): Boolean = ownAddresses.any { it.equals(destinationAddress.hostAddress, true) }

    override suspend fun shouldHandleRequest(dnsMessage: DnsMessage): Boolean {
        return if(dnsMessage.questions.size > 0) {
            val name = dnsMessage.question.name
            return !ProxyBypassHandler.knownSearchDomains.any {
                name.endsWith(it)
            }
        } else true
    }

    override suspend fun shouldModifyUpstreamResponse(answer: ReceivedAnswer, receivedPayload: ByteArray): Boolean {
        return mapQueryRefusedToHostBlock
    }

    override fun verifyConnection(sslSession: SSLSession, outgoingPacket: DatagramPacket) {
        if (!upstreamAddresses.any { hostnameVerifier.verify(it.host, sslSession) }) {
            throw SSLHandshakeException("Certificate mismatch, got ${sslSession.peerPrincipal}, but expected any of ${upstreamAddresses.joinToString { it.host }}")
        }
    }

    override fun name(): String {
        return "ProxyTlsHandler"
    }

}