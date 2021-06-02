package com.frostnerd.smokescreen.service

import android.content.Context
import android.net.ConnectivityManager
import com.frostnerd.smokescreen.Logger
import com.frostnerd.smokescreen.isVpnNetwork
import com.frostnerd.smokescreen.logger
import kotlinx.coroutines.*

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
class VpnWatchDog(private val onVpnDisconnected:() -> Unit,
                  private val context: Context) {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)
    private var running = true
    private var vpnRunning:Boolean = true
    private val logger:Logger? = context.logger
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        scope.launch {
            checkConnection()
        }
    }

    private fun log(text:String) {
        logger?.log(text, "ConnectionWatchdog")
    }

    private suspend fun checkConnection() {
        delay(3000)
        log("Beginning VPN-check")
        val networks = connectivityManager.allNetworks.toList().filterNotNull()
        val isStillRunning = networks.any { connectivityManager.isVpnNetwork(it) } || networks.isEmpty()

        if(!isStillRunning && vpnRunning) {
            callCallback()
        }
        vpnRunning = isStillRunning

        if(running) {
            log("Connection check done.")
            scope.launch {
                checkConnection()
            }
        }
    }

    private fun callCallback() {
        if(!running) return
        log("Calling callback.")
        onVpnDisconnected()
    }

    fun stop() {
        supervisor.cancel()
        running = false
    }
}