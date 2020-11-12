package com.frostnerd.smokescreen.util

import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.smokescreen.hasHttpsServer
import com.frostnerd.smokescreen.hasQuicServer
import com.frostnerd.smokescreen.hasTlsServer

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
enum class ServerType(val index:Int) {
    DOH(0), DOT(1), DOQ(2);

    companion object {
        fun detect(from: DnsServerInformation<*>):ServerType {
            return when {
                from.hasHttpsServer() -> {
                    DOH
                }
                from.hasTlsServer() -> {
                    DOT
                }
                from.hasQuicServer() -> {
                    DOQ
                }
                else -> {
                    error("Unknown Type")
                }
            }
        }

        fun from(index:Int): ServerType {
            return values().first { it.index == index }
        }
    }
}