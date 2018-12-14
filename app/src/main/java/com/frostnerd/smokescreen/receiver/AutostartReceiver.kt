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
class AutostartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null) {
            var startService = false
            when {
                intent.action == Intent.ACTION_MY_PACKAGE_REPLACED -> startService = context.getPreferences().startAppAfterUpdate
                intent.action == Intent.ACTION_PACKAGE_REPLACED -> startService = intent.data?.schemeSpecificPart == context.packageName && context.getPreferences().startAppAfterUpdate
                context.getPreferences().startAppOnBoot -> startService = true
            }
            if(startService) BackgroundVpnConfigureActivity.prepareVpn(context)
        }
    }
}