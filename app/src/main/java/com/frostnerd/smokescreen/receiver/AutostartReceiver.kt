package com.frostnerd.smokescreen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.getPreferences

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