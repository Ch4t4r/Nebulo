package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.AddressCreator
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.quic.AbstractQuicDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.quic.QuicUpstreamAddress
import com.frostnerd.vpntunnelproxy.FutureAnswer
import org.chromium.net.CronetEngine
import org.minidns.dnsmessage.DnsMessage
import org.minidns.record.A
import org.minidns.record.AAAA
import org.minidns.record.Record
import java.net.InetAddress

/*
 * Copyright (C) 2020 Daniel Wolf (Ch4t4r)
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
class ProxyQuicHandler(
    private val ownAddresses:List<String>,
   upstreamAddresses: List<QuicUpstreamAddress>,
    connectTimeout: Int,
    val queryCountCallback: (() -> Unit)? = null,
    val mapQueryRefusedToHostBlock:Boolean,
    engine:CronetEngine
):AbstractQuicDnsHandle(upstreamAddresses, connectTimeout, engine) {
    override val handlesSpecificRequests: Boolean = ProxyBypassHandler.knownSearchDomains.isNotEmpty()
    private val dummyUpstreamAddress = UpstreamAddress(AddressCreator.fromHostAddress("0.0.0.0"), 1)

    override fun shouldHandleRequest(dnsMessage: DnsMessage): Boolean {
        return if(dnsMessage.questions.size > 0) {
            val name = dnsMessage.question.name
            return !ProxyBypassHandler.knownSearchDomains.any {
                name.endsWith(it)
            }
        } else true
    }

    override fun modifyUpstreamResponse(dnsMessage: DnsMessage): DnsMessage {
        return if(dnsMessage.responseCode == DnsMessage.RESPONSE_CODE.REFUSED) {
            if(dnsMessage.questions.isNotEmpty()) {
                val answer = if(dnsMessage.question.type == Record.TYPE.A) {
                    A("0.0.0.0")
                } else AAAA("::")
                dnsMessage.asBuilder().setResponseCode(DnsMessage.RESPONSE_CODE.NO_ERROR).addAnswer(
                    Record(
                        dnsMessage.question.name,
                        dnsMessage.question.type,
                        Record.CLASS.IN.value,
                        50L,
                        answer
                    )
                ).build()
            } else dnsMessage
        } else dnsMessage
    }

    override fun remapDestination(destinationAddress: InetAddress, port: Int): UpstreamAddress {
        queryCountCallback?.invoke()
        return dummyUpstreamAddress
    }

    override fun shouldHandleDestination(destinationAddress: InetAddress, port: Int): Boolean = ownAddresses.any { it.equals(destinationAddress.hostAddress, true) }

    override fun shouldModifyUpstreamResponse(dnsMessage: DnsMessage): Boolean =
        mapQueryRefusedToHostBlock && dnsMessage.responseCode == DnsMessage.RESPONSE_CODE.REFUSED

    override fun informFailedRequest(request: FutureAnswer, failureReason: Throwable?) {

    }

    override fun name(): String {
        return "ProxyQuicHandler"
    }
}