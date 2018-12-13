package com.frostnerd.smokescreen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.getPreferences

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null) {
            if (intent.action!! == Intent.ACTION_BOOT_COMPLETED || intent.action!! == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                if (context.getPreferences().startAppOnBoot) {
                    BackgroundVpnConfigureActivity.prepareVpn(context)
                }
            }
        }
    }
}