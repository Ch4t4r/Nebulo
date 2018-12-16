package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.vpntunnelproxy.ReceivedAnswer
import org.minidns.dnsmessage.DnsMessage
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
    val queryCountCallback:((queryCount:Int) -> Unit)? = null
) :
    AbstractHttpsDNSHandle(serverConfigurations, connectTimeout) {

    constructor(
        serverConfiguration: ServerConfiguration,
        connectTimeout: Int
    ) : this(listOf(serverConfiguration), connectTimeout)

    override suspend fun modifyUpstreamResponse(dnsMessage: DnsMessage): DnsMessage = dnsMessage

    override suspend fun remapDestination(destinationAddress: InetAddress, port: Int): UpstreamAddress {
        queryCountCallback?.invoke(dnsPacketProxy?.tunnelHandle?.trafficStats?.packetsReceivedFromDevice?.toInt() ?: 0)
        return UpstreamAddress(destinationAddress, port)
    }


    override suspend fun shouldHandleDestination(destinationAddress: InetAddress, port: Int): Boolean = true

    override suspend fun shouldModifyUpstreamResponse(answer: ReceivedAnswer, receivedPayload: ByteArray): Boolean = false

}