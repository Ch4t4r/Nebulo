package com.frostnerd.smokescreen.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.log
import com.frostnerd.smokescreen.util.Notifications
import leakcanary.LeakSentry

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

class VpnRestartService : Service() {

    companion object {
        const val NOTIFICATION_ID: Int = 999
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        LeakSentry.refWatcher.watch(this, "VpnRestartService")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("Trying to restart the VPN Service.")
        val builder = NotificationCompat.Builder(this, Notifications.servicePersistentNotificationChannel(this))
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.setOngoing(true)
        builder.setContentTitle(getString(R.string.notification_vpnrestart_title))
        builder.setContentText(getString(R.string.notification_vpnrestart_text))

        startForeground(NOTIFICATION_ID, builder.build())

        log("Starting the background configure")
        BackgroundVpnConfigureActivity.prepareVpn(this,
            BackgroundVpnConfigureActivity.readServerInfoFromIntent(intent))

        stopForeground(true)
        log("Stopping self")
        stopSelf()
        return START_NOT_STICKY
    }

}