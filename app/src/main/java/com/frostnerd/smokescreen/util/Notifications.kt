package com.frostnerd.smokescreen.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.frostnerd.smokescreen.R

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
class Notifications {

    companion object {
        fun servicePersistentNotificationChannel(context: Context):String {
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "service_foreground_notification",
                    context.getString(R.string.notification_channel_foreground_service),
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.enableLights(false)
                channel.enableVibration(false)
                channel.setSound(null, null)
                channel.description = context.getString(R.string.notification_channel_foreground_service_description)
                channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }
            return "service_foreground_notification"
        }

        fun noConnectionNotificationChannelId(context: Context):String {
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "noConnectionChannel",
                    context.getString(R.string.notification_channel_noconnection),
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.enableLights(false)
                channel.enableVibration(true)
                channel.description = context.getString(R.string.notification_channel_noconnection_description)
                channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }
            return "noConnectionChannel"
        }

        fun getDefaultNotificationChannelId(context: Context): String {
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "defaultchannel",
                    context.getString(R.string.notification_channel_default),
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.enableLights(false)
                channel.enableVibration(false)
                channel.description = context.getString(R.string.notification_channel_default_description)
                channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }
            return "defaultchannel"
        }
    }
}