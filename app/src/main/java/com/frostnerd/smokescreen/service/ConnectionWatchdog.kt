package com.frostnerd.smokescreen.service

import com.frostnerd.smokescreen.Logger
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
                         private val checkIntervalMs:Long,
                         private val debounceCallbackByMs:Long? = null,
                         private val badLatencyThresholdMs:Int = 750,
                         private val badPacketLossThresholdPercent:Int = 30,
                         private val onBadServerConnection:() -> Unit,
                         private val onBadConnectionResolved:() -> Unit,
                         private val logger:Logger?,
                         private val advancedLogging:Boolean = false
                         ) {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)
    private var running = true
    private var latencyAtLastCheck:Int? = null
    private var packetLossAtLastCheck:Int? = null
    private var packetCountAtLastCheck:Int? = null
    private var lastFailedAnswerCount:Int = 0
    private var lastTotalPacketCount:Int = 0
    private var lastCallbackCall:Long? = null
    private var measurementsWithBadConnection:Int = 0

    init {
        scope.launch {
            checkConnection()
        }
    }

    private fun logFine(text:String) {
        if(advancedLogging) logger?.log(text, "ConnectionWatchdog")
    }

    private fun log(text:String) {
        logger?.log(text, "ConnectionWatchdog")
    }

    private suspend fun checkConnection() {
        delay(checkIntervalMs)
        log("Beginning connection check")
        if(trafficStats.packetsReceivedFromDevice >= 15
            && trafficStats.bytesSentToDevice > 0
            && packetCountAtLastCheck?.let { trafficStats.packetsReceivedFromDevice - it > 10 } != false
        ) { // Not enough data to act on.
            val currentLatency = trafficStats.floatingAverageLatency.toInt()
            val currentPacketLossPercent = (100*(trafficStats.failedAnswers - lastFailedAnswerCount))/((trafficStats.packetsReceivedFromDevice - lastTotalPacketCount)*0.9)
            lastFailedAnswerCount = trafficStats.failedAnswers.toInt()
            lastTotalPacketCount = trafficStats.packetsReceivedFromDevice.toInt()

            logFine("Current latency: $currentLatency")
            logFine("Current packet loss: $currentPacketLossPercent")

            val hasBadConnection = if(currentLatency > badLatencyThresholdMs*1.3 ||
                (latencyAtLastCheck?.let { it > badLatencyThresholdMs } == true && currentLatency > badLatencyThresholdMs)) {
                true
            } else currentPacketLossPercent > badPacketLossThresholdPercent*1.3 || (
                        packetLossAtLastCheck?.let { it > badPacketLossThresholdPercent } == true && currentPacketLossPercent > badPacketLossThresholdPercent)

            logFine("Deeming this connection bad: $hasBadConnection")

            if (hasBadConnection) {
                measurementsWithBadConnection++
                callCallback()
            } else if (measurementsWithBadConnection != 0) {
                measurementsWithBadConnection = maxOf(
                    0, measurementsWithBadConnection - maxOf(3, measurementsWithBadConnection / 6) -
                            when {
                                measurementsWithBadConnection > 500 -> maxOf(32, measurementsWithBadConnection/8)
                                measurementsWithBadConnection > 300 -> 32
                                measurementsWithBadConnection > 200 -> 18
                                measurementsWithBadConnection > 100 -> 10
                                measurementsWithBadConnection > 50 -> 5
                                measurementsWithBadConnection > 20 -> 3
                                measurementsWithBadConnection > 10 -> 2
                                else -> 0
                            }
                )
                if (measurementsWithBadConnection <= 0) {
                    onBadConnectionResolved()
                    measurementsWithBadConnection = 0
                } else {
                    logFine("Measurements left till calling callback with resolved status: $measurementsWithBadConnection")

                }
            }

            latencyAtLastCheck = trafficStats.floatingAverageLatency.toInt()
            packetLossAtLastCheck = currentPacketLossPercent.toInt()
        }
        if(running) {
            log("Connection check done.")
            scope.launch {
                checkConnection()
            }
        }
    }

    private fun callCallback() {
        if(!running) return
        logFine("Calling callback.")
        if(debounceCallbackByMs == null || lastCallbackCall == null) onBadServerConnection()
        else if(System.currentTimeMillis() - lastCallbackCall!! > debounceCallbackByMs) onBadServerConnection()
        lastCallbackCall = System.currentTimeMillis()
    }

    fun stop() {
        supervisor.cancel()
        running = false
    }
}