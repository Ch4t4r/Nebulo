package com.frostnerd.smokescreen.activity

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.general.StringUtil
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.R
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
        if (intent != null) {
            val primaryUrl = intent.getStringExtra(BackgroundVpnConfigureActivity.extraKeyPrimaryUrl)!!
            val secondaryUrl = intent.getStringExtra(BackgroundVpnConfigureActivity.extraKeySecondaryUrl) ?: null
            DnsVpnService.restartVpn(this, primaryUrl, secondaryUrl)
        }
        finish()
    }

    companion object {
        fun createShortcut(context: Context, info: HttpsDnsServerInformation) {
            val shortcutName = info.name
            val primaryServerUrl = info.servers.first().address.getUrl()
            val secondaryServerUrl = info.servers.getOrNull(1)?.address?.getUrl()
            val targetIntent = Intent(context, ShortcutActivity::class.java)
            targetIntent.action = "${context.packageName}.dummy_action"
            targetIntent.putExtra(BackgroundVpnConfigureActivity.extraKeyPrimaryUrl, primaryServerUrl)
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (secondaryServerUrl != null) targetIntent.putExtra(
                BackgroundVpnConfigureActivity.extraKeySecondaryUrl,
                secondaryServerUrl
            )

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