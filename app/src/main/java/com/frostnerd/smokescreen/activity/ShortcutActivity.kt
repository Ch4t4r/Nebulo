package com.frostnerd.smokescreen.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.general.StringUtil
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.fromServerUrls
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.service.DnsVpnService

/*
 * Copyright (C) {YEAR} Daniel Wolf (Ch4t4r)
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

class ShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent != null) {
            if(intent.extras?.containsKey("primary_url") == true) {
                val primaryUrl = intent.getStringExtra("primary_url")!!
                val secondaryUrl = intent.getStringExtra("secondary_url") ?: null
                DnsVpnService.restartVpn(this, HttpsDnsServerInformation.fromServerUrls(primaryUrl, secondaryUrl))
            } else {
                val serverInfo = BackgroundVpnConfigureActivity.readServerInfoFromIntent(intent)
                val hiddenDohServers = getPreferences().removedDefaultDoHServers
                val hiddenDoTServers = getPreferences().removedDefaultDoTServers
                val restartService = serverInfo?.let { info ->
                    (getPreferences().userServers.map {
                        it.serverInformation
                    } + AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated {servers ->
                        servers.filter {
                            it.key !in hiddenDohServers
                        }.values.toList()
                    } + AbstractTLSDnsHandle.waitUntilKnownServersArePopulated {
                            servers ->
                        servers.filter {
                            it.key !in hiddenDoTServers
                        }.values.toList()
                    }).firstOrNull {
                        info.name == it.name && it.servers.firstOrNull()?.address?.formatToString() == info.servers.firstOrNull()?.address?.formatToString()
                    }
                }?.let {
                    if(getPreferences().dnsServerConfig != it) {
                        getPreferences().dnsServerConfig = it
                        false
                    } else {
                        true
                    }
                } ?: true
                if(restartService) DnsVpnService.restartVpn(this, serverInfo)
            }
        }
        finish()
    }

    companion object {
        fun createShortcut(context: Context, info: DnsServerInformation<*>) {
            val shortcutName = info.name
            val targetIntent = Intent(context, ShortcutActivity::class.java)
            targetIntent.action = "${context.packageName}.dummy_action"
            BackgroundVpnConfigureActivity.writeServerInfoToIntent(info, targetIntent)
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                val shortcutInf = ShortcutInfoCompat.Builder(context, StringUtil.randomString(30))
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setShortLabel(shortcutName)
                    .setLongLabel(shortcutName)
                    .setIntent(targetIntent).build()
                ShortcutManagerCompat.requestPinShortcut(context, shortcutInf, null)
            }
        }
    }

}