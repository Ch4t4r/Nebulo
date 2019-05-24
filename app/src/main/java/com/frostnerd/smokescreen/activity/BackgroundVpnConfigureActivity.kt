package com.frostnerd.smokescreen.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Window
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.DnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.HttpsUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.toJson
import java.lang.IllegalArgumentException

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
class BackgroundVpnConfigureActivity : BaseActivity() {

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    companion object {
        const val extraKeyServerConfig = "server_config"
        const val extraKeyServerType = "config_type"
        private const val VPN_REQUEST_CODE = 1

        fun prepareVpn(context: Context, serverInfo:DnsServerInformation<*>? = null) {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent == null) {
                DnsVpnService.startVpn(context, serverInfo)
            } else {
                val intent = Intent(context, BackgroundVpnConfigureActivity::class.java)
                if(serverInfo != null) {
                    writeServerInfoToIntent(serverInfo, intent)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        fun writeServerInfoToIntent(info:DnsServerInformation<*>, intent:Intent) {
            intent.putExtra(extraKeyServerConfig, info.toJson())
            intent.putExtra(extraKeyServerType, getServerInfoType(info))
        }

        fun writeServerInfoToIntent(info:DnsServerInformation<*>, bundle:Bundle) {
            bundle.putString(extraKeyServerConfig, info.toJson())
            bundle.putString(extraKeyServerType, getServerInfoType(info))
        }

        fun readServerInfoFromIntent(intent:Intent?):DnsServerInformation<*>? {
            if(intent == null) return null
            if(intent.extras?.containsKey(extraKeyServerConfig) == true) {
                if(intent.extras?.containsKey(extraKeyServerType) == true) {
                    return serverInfoFromJson(intent.extras!!.getString(extraKeyServerConfig)!!, intent.extras!!.getString(extraKeyServerType)!!)
                }
            }
            return null
        }

        fun readServerInfoFromIntent(bundle:Bundle?):DnsServerInformation<*>? {
            if(bundle == null) return null
            if(bundle.containsKey(extraKeyServerConfig)) {
                if(bundle.containsKey(extraKeyServerType)) {
                    return serverInfoFromJson(bundle.getString(extraKeyServerConfig)!!, bundle.getString(extraKeyServerType)!!)
                }
            }
            return null
        }

        fun getServerInfoType(serverInfo: DnsServerInformation<*>): String {
            return when {
                serverInfo is HttpsDnsServerInformation -> "https"
                serverInfo.servers.any {
                    it.address is TLSUpstreamAddress
                } -> "tls"
                else -> "unknown"
            }
        }

        fun serverInfoFromJson(json:String, type:String):DnsServerInformation<*> {
            TLSUpstreamAddress
            HttpsUpstreamAddress
            return when(type) {
                "tls" -> DnsServerInformationTypeAdapter().fromJson(json)
                "https" -> HttpsDnsServerInformationTypeAdapter().fromJson(json)
                else -> throw IllegalArgumentException()
        }
    }
}

    var requestTime: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        actionBar?.hide()

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent == null) {
            startService()
            finish()
        } else {
            requestTime = System.currentTimeMillis()
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startService()
                finish()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                if (System.currentTimeMillis() - requestTime <= 500) { // Most likely the system denied the request automatically
                    showPermissionDenialDialog()
                } else {
                    finish()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startService() {
        DnsVpnService.startVpn(this, readServerInfoFromIntent(intent))
    }

    private fun showPermissionDenialDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this, getPreferences().theme.dialogStyle)
            .setTitle(getString(R.string.app_name) + " - " + getString(R.string.information))
            .setPositiveButton(R.string.open_app) { _, _ ->
                val intent = Intent(this@BackgroundVpnConfigureActivity, PinActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .setOnCancelListener {
                finish()
            }.show()
    }
}