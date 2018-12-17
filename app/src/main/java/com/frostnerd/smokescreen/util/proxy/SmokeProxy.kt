package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.DnsPacketProxy
import com.frostnerd.dnstunnelproxy.SimpleDnsCache
import com.frostnerd.smokescreen.service.DnsVpnService

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

class SmokeProxy(dnsHandle: ProxyHandler, vpnService: DnsVpnService) :
    DnsPacketProxy(listOf(dnsHandle), vpnService, SimpleDnsCache()) {

    val cache = super.dnsCache!! as SimpleDnsCache

}