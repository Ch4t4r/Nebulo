package com.frostnerd.smokescreen.service

import com.frostnerd.vpntunnelproxy.TrafficStats
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
class ConnectionWatchdog(private val trafficStats: TrafficStats,
                         val checkIntervalMs:Long,
                         val debounceCallbackByMs:Long? = null,
                         val badLatencyThresholdMs:Int = 750,
                         val badPacketLossThresholdPercent:Int = 30,
                         private val onBadServerConnection:() -> Unit) {
    val supervisor = SupervisorJob()
    val scope = CoroutineScope(supervisor + Dispatchers.IO)
    private var running = true
    private var latencyAtLastCheck:Int? = null
    private var packetLossAtLastCheck:Int? = null
    private var packetCountAtLastCheck:Int? = null
    private var lastCallbackCall:Long? = null

    init {
        scope.launch {
            checkConnection()
        }
    }

    private suspend fun checkConnection() {
        delay(checkIntervalMs)
        if(trafficStats.packetsReceivedFromDevice >= 15
            && trafficStats.bytesSentToDevice > 0
            && packetCountAtLastCheck?.let { trafficStats.packetsReceivedFromDevice - it > 10 } != false
        ) { // Not enough data to act on.
            val currentLatency = trafficStats.floatingAverageLatency.toInt()
            val currentPacketLossPercent = (100*trafficStats.failedAnswers)/(trafficStats.packetsReceivedFromDevice*0.9)

            if(currentLatency > badLatencyThresholdMs*1.3 ||
                (latencyAtLastCheck?.let { it > badLatencyThresholdMs } == true && currentLatency > badLatencyThresholdMs)) {
                callCallback()
            } else if(currentPacketLossPercent > badPacketLossThresholdPercent*1.3 || (
                        packetLossAtLastCheck?.let { it > badPacketLossThresholdPercent } == true && currentPacketLossPercent > badPacketLossThresholdPercent)) {
                callCallback()
            }

            latencyAtLastCheck = trafficStats.floatingAverageLatency.toInt()
            packetLossAtLastCheck = currentPacketLossPercent.toInt()
        }
        if(running) {
            scope.launch {
                checkConnection()
            }
        }
    }

    private fun callCallback() {
        if(!running) return
        if(debounceCallbackByMs == null || lastCallbackCall == null) onBadServerConnection()
        else if(System.currentTimeMillis() - lastCallbackCall!! > debounceCallbackByMs) onBadServerConnection()
        lastCallbackCall = System.currentTimeMillis()
    }

    fun stop() {
        supervisor.cancel()
        running = false
    }
}