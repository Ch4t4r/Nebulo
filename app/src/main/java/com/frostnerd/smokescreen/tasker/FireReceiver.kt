package com.frostnerd.smokescreen.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.fromServerUrls
import com.frostnerd.smokescreen.service.Command
import com.frostnerd.smokescreen.service.DnsVpnService

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
                                if(extras.containsKey(TaskerHelper.DATA_KEY_PRIMARYSERVER)) {
                                    val primaryUrl = intent.getStringExtra(TaskerHelper.DATA_KEY_PRIMARYSERVER)!!
                                    val secondaryUrl = intent.getStringExtra(TaskerHelper.DATA_KEY_SECONDARYSERVER) ?: null
                                    DnsVpnService.restartVpn(context, HttpsDnsServerInformation.fromServerUrls(primaryUrl, secondaryUrl))
                                } else {
                                    val info = BackgroundVpnConfigureActivity.readServerInfoFromIntent(intent)
                                    DnsVpnService.restartVpn(context, info)
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