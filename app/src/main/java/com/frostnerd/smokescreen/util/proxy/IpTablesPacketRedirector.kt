package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.smokescreen.Logger
import java.io.DataOutputStream
import java.lang.Exception

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

/**
 * Class which uses IPTables to redirect all DNS queries (on port 53) to Nebulos DNS server.
 * Is only used in non-VPN mode. Requires root and iptables to be present on the device.
 *
 * Not all devices have an iptables binary present, thus this might fail.
 *
 */
class IpTablesPacketRedirector(var dnsServerPort:Int,
                               var dnsServerIpAddress:String,
                               private val logger:Logger?) {
    private val logTag = "IpTablesPacketRedirector"

    /**
     * Begins the redirect using iptables. All DNS requests on port 53 will be forwarded to the specified IP Address.
     * @return Whether the redirect rule could be created in IPTables
     */
    fun beginForward():Boolean {
        return try {
            processSuCommand(generateIpTablesCommand(true))
        } catch (ex:Exception) {
            false
        }
    }

    fun endForward():Boolean {
        return try {
            processSuCommand(generateIpTablesCommand(false))
        } catch (ex:Exception) {
            false
        }
    }

    // Append: iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination <ip>:<port>"
    // Drop:   iptables -t nat -D OUTPUT -p udp --dport 53 -j DNAT --to-destination <ip>:<port>"
    private fun generateIpTablesCommand(append:Boolean):String {
        return buildString {
            append("iptables -t nat ")
            if(append) append("-I")
            else append("-D")
            append("OUTPUT -p udp --dport 53 -j DNAT --to-destination ")
            append(dnsServerIpAddress)
            append(":")
            append(dnsServerPort)
        }
    }

    /**
     * @return Whether the command has been executed successfully
     */
    private fun processSuCommand(command:String):Boolean {
        return try {
            val su = runCommandWithSU(command)
            su.errorStream.readBytes().takeIf {
                it.isNotEmpty()
            }?.apply {
                logger?.log("Command '$command' yielded error output ${String(this)}", logTag)
            }

            su.waitFor()
            return su.exitValue() == 0
        } catch (ex:Exception) {
            logger?.log("Command '$command' yielded exception: ${Logger.stacktraceToString(ex)}", logTag)
            false
        }
    }

    private fun runCommandWithSU(command:String):Process {
        val su = Runtime.getRuntime().exec("su")
        DataOutputStream(su.outputStream).apply {
            writeBytes(command)
            writeBytes("\n")
            writeBytes("exit")
            writeBytes("\n")
            flush()
        }.close()
        return su
    }
}