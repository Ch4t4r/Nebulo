package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.DnsHandle
import com.frostnerd.dnstunnelproxy.DnsPacketProxy
import com.frostnerd.dnstunnelproxy.QueryListener
import com.frostnerd.dnstunnelproxy.SimpleDnsCache
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.log
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.vpntunnelproxy.FutureAnswer
import org.minidns.dnsmessage.DnsMessage

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

class SmokeProxy(
    dnsHandle: ProxyHandler,
    proxyBypassHandles: List<DnsHandle>,
    vpnService: DnsVpnService,
    val cache: SimpleDnsCache?,
    queryListener: QueryListener?
) :
    DnsPacketProxy(
        proxyBypassHandles.toMutableList().let {
            it.add(dnsHandle)
            it
        }.toList(),
        null,
        vpnService,
        cache,
        queryListener = queryListener
    )