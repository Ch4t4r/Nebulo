package com.frostnerd.smokescreen.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.smokescreen.service.Command
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

class FireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TaskerHelper.ACTION_FIRE) {
            val extras = intent.getBundleExtra(TaskerHelper.EXTRAS_BUNDLE_KEY)
            if (extras != null) {
                val action = extras.getString(TaskerHelper.DATA_KEY_ACTION)
                val startIfRunning = extras.getBoolean(TaskerHelper.DATA_KEY_STARTIFRUNNING, true)
                if (action != null) {
                    when (action) {
                        "start" -> {
                            val running = context.isServiceRunning(DnsVpnService::class.java)
                            if (startIfRunning || !running) {
                                val primaryServer = extras.getString(TaskerHelper.DATA_KEY_PRIMARYSERVER, null)
                                val secondaryServer = extras.getString(TaskerHelper.DATA_KEY_SECONDARYSERVER, null)
                                if (running) {
                                    if(primaryServer != null) {
                                        DnsVpnService.restartVpn(context, primaryServer, secondaryServer)
                                    } else DnsVpnService.restartVpn(context, true)
                                } else {
                                    DnsVpnService.startVpn(context, primaryServer, secondaryServer)
                                }
                            }
                        }
                        "stop" -> {
                            if (context.isServiceRunning(DnsVpnService::class.java)) {
                                DnsVpnService.sendCommand(context, Command.STOP)
                            }
                        }
                    }

                }
            }
        }
    }

}