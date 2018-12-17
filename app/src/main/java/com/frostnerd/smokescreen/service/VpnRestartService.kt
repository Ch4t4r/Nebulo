package com.frostnerd.smokescreen.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.startForegroundServiceCompat
import com.frostnerd.smokescreen.util.Notifications

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

class VpnRestartService : Service() {

    companion object {
        const val NOTIFICATION_ID: Int = 999
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = NotificationCompat.Builder(this, Notifications.servicePersistentNotificationChannel(this))
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.setOngoing(true)
        builder.setContentTitle(getString(R.string.notification_vpnrestart_title))
        builder.setContentText(getString(R.string.notification_vpnrestart_text))

        startForeground(NOTIFICATION_ID, builder.build())
        val restartIntent = Intent(this, DnsVpnService::class.java)
        if (intent != null && intent.extras != null)
            restartIntent.putExtras(intent.extras!!)
        startForegroundServiceCompat(restartIntent)
        stopForeground(true)
        stopSelf()
        return START_NOT_STICKY
    }

}